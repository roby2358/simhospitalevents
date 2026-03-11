import heapq
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from random import Random

from simhospital.clock import SimulationClock, WallClock
from simhospital.config import SimulatorConfig
from simhospital.message import MessageFactory
from simhospital.patient import PatientGenerator, PatientState
from simhospital.pathway import Pathway, PathwayEvent, PathwayEventType, PathwayLoader


@dataclass(order=True)
class ScheduledEvent:
    scheduled_time: datetime
    _seq: int = field(compare=True)  # tiebreaker for equal times
    patient: PatientState = field(compare=False)
    event: PathwayEvent = field(compare=False)


class HospitalSimulator:
    def __init__(self, config: SimulatorConfig):
        self._config = config
        self._clock = config.clock
        self._is_wall_clock = isinstance(config.clock, WallClock)
        self._rng = Random(config.seed)
        self._message_factory = MessageFactory(Random(self._rng.randint(0, 2**63)))
        self._patient_generator = PatientGenerator(Random(self._rng.randint(0, 2**63)))
        self._pathways = PathwayLoader().load(config.pathway_directory)
        self._pathway_names = list(self._pathways.keys())
        self._heap: list[ScheduledEvent] = []
        self._seq = 0
        self._last_message_time = self._clock.now()

        for _ in range(config.concurrent_patients):
            self._schedule_new_patient()

    def next_message(self) -> str:
        while True:
            scheduled = heapq.heappop(self._heap)
            self._clock.advance_to(scheduled.scheduled_time)
            self._apply_rate_limit()
            self._apply_side_effects(scheduled.event, scheduled.patient, scheduled.scheduled_time)

            message = self._message_factory.build(
                scheduled.event, scheduled.patient, scheduled.scheduled_time
            )
            self._advance_patient(scheduled.patient, scheduled.scheduled_time)

            if message is not None:
                self._last_message_time = self._clock.now()
                return message

    def _apply_rate_limit(self) -> None:
        if not self._is_wall_clock:
            return
        if self._config.message_rate_per_second <= 0:
            return

        interval = timedelta(seconds=1.0 / self._config.message_rate_per_second)
        earliest = self._last_message_time + interval
        if self._clock.now() < earliest:
            self._clock.advance_to(earliest)

    def _apply_side_effects(self, event: PathwayEvent, patient: PatientState, event_time: datetime) -> None:
        if event.type == PathwayEventType.ADMISSION:
            patient.admission_time = event_time
            self._apply_location(event, patient)
        elif event.type == PathwayEventType.TRANSFER:
            self._apply_location(event, patient)
        elif event.type == PathwayEventType.DISCHARGE:
            patient.admission_time = None

    def _apply_location(self, event: PathwayEvent, patient: PatientState) -> None:
        loc = event.parameters.get("loc")
        if loc is not None:
            patient.ward = loc
        bed = event.parameters.get("bed")
        if bed is not None:
            patient.bed = bed

    def _advance_patient(self, patient: PatientState, previous_event_time: datetime) -> None:
        patient.next_event_index += 1
        pathway = self._pathways[patient.pathway_name]

        if patient.next_event_index < len(pathway.events):
            self._schedule_next_event(patient, previous_event_time)
        else:
            self._schedule_new_patient()

    def _schedule_new_patient(self) -> None:
        pathway_name = self._rng.choice(self._pathway_names)
        pathway = self._pathways[pathway_name]
        patient = self._patient_generator.generate(pathway_name)

        offset = timedelta(milliseconds=self._rng.randint(0, 59_999))
        scheduled_time = self._clock.now() + offset

        first_event = pathway.events[0]
        if first_event.delay is not None:
            scheduled_time += first_event.delay

        self._push(scheduled_time, patient, first_event)

    def _schedule_next_event(self, patient: PatientState, previous_event_time: datetime) -> None:
        pathway = self._pathways[patient.pathway_name]
        next_event = pathway.events[patient.next_event_index]

        if next_event.delay is not None:
            scheduled_time = previous_event_time + next_event.delay
        else:
            scheduled_time = previous_event_time + timedelta(
                milliseconds=1000 + self._rng.randint(0, 3999)
            )

        self._push(scheduled_time, patient, next_event)

    def _push(self, scheduled_time: datetime, patient: PatientState, event: PathwayEvent) -> None:
        self._seq += 1
        heapq.heappush(self._heap, ScheduledEvent(scheduled_time, self._seq, patient, event))
