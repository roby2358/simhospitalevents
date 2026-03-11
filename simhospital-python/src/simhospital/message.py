from dataclasses import dataclass
from datetime import datetime
from random import Random
from uuid import uuid4

import hl7

from simhospital.patient import OrderDetails, PatientState
from simhospital.pathway import PathwayEvent, PathwayEventType


@dataclass(frozen=True)
class _TestPanel:
    names: list[str]
    units: list[str]
    ranges: list[tuple[float, float]]

    def size(self) -> int:
        return len(self.names)


_LAB_PANEL = _TestPanel(
    names=["Hemoglobin", "White Blood Cell Count", "Platelet Count", "Sodium",
           "Potassium", "Creatinine", "Glucose", "Calcium", "ALT", "AST"],
    units=["g/dL", "x10^9/L", "x10^9/L", "mmol/L",
           "mmol/L", "mg/dL", "mg/dL", "mg/dL", "U/L", "U/L"],
    ranges=[(12.0, 17.5), (4.0, 11.0), (150.0, 400.0), (136.0, 145.0),
            (3.5, 5.1), (0.6, 1.2), (70.0, 100.0), (8.5, 10.5), (7.0, 56.0), (10.0, 40.0)],
)

_VITAL_PANEL = _TestPanel(
    names=["Heart Rate", "Systolic BP", "Diastolic BP", "Temperature", "Respiratory Rate", "SpO2"],
    units=["bpm", "mmHg", "mmHg", "degC", "breaths/min", "%"],
    ranges=[(60.0, 100.0), (90.0, 140.0), (60.0, 90.0), (36.1, 37.2), (12.0, 20.0), (95.0, 100.0)],
)

_TRIGGER_EVENTS: dict[PathwayEventType, str] = {
    PathwayEventType.REGISTRATION: "A04",
    PathwayEventType.PRE_ADMISSION: "A05",
    PathwayEventType.TRANSFER_IN_ERROR: "A02",
    PathwayEventType.DISCHARGE_IN_ERROR: "A03",
    PathwayEventType.CANCEL_VISIT: "A11",
    PathwayEventType.CANCEL_TRANSFER: "A12",
    PathwayEventType.CANCEL_DISCHARGE: "A13",
    PathwayEventType.ADD_PERSON: "A28",
    PathwayEventType.UPDATE_PERSON: "A31",
    PathwayEventType.PENDING_ADMISSION: "A14",
    PathwayEventType.PENDING_DISCHARGE: "A16",
    PathwayEventType.PENDING_TRANSFER: "A15",
    PathwayEventType.CANCEL_PENDING_ADMISSION: "A27",
    PathwayEventType.CANCEL_PENDING_DISCHARGE: "A25",
    PathwayEventType.CANCEL_PENDING_TRANSFER: "A26",
    PathwayEventType.DELETE_VISIT: "A23",
    PathwayEventType.TRACK_DEPARTURE: "A09",
    PathwayEventType.TRACK_ARRIVAL: "A10",
    PathwayEventType.MERGE: "A34",
    PathwayEventType.BED_SWAP: "A17",
    PathwayEventType.CLINICAL_NOTE: "R01",
    PathwayEventType.DOCUMENT: "R01",
}


def _fmt_dt(dt: datetime) -> str:
    return dt.strftime("%Y%m%d%H%M%S")


def _fmt_date(d) -> str:
    return d.strftime("%Y%m%d")


class MessageFactory:
    def __init__(self, rng: Random):
        self._rng = rng

    def build(self, event: PathwayEvent, patient: PatientState, event_time: datetime) -> str | None:
        if event.type == PathwayEventType.DELAY:
            return None

        builder = {
            PathwayEventType.ADMISSION: lambda: self._build_adt("A01", patient, event_time),
            PathwayEventType.DISCHARGE: lambda: self._build_adt("A03", patient, event_time),
            PathwayEventType.TRANSFER: lambda: self._build_adt("A02", patient, event_time),
            PathwayEventType.ORDER: lambda: self._build_order(patient, event_time, event, "NW"),
            PathwayEventType.CANCEL_ORDER: lambda: self._build_order(patient, event_time, event, "CA"),
            PathwayEventType.RESULT: lambda: self._build_result(patient, event_time, event, _LAB_PANEL),
            PathwayEventType.OBSERVATION: lambda: self._build_result(patient, event_time, event, _VITAL_PANEL),
        }.get(event.type)

        if builder is not None:
            return builder()
        return self._build_generic_adt(patient, event_time, event.type)

    def _msh(self, msg_type: str, trigger: str, event_time: datetime) -> str:
        return (
            f"MSH|^~\\&|SimHospital|SimHospital-Facility|||{_fmt_dt(event_time)}"
            f"||{msg_type}^{trigger}|{uuid4()}|P|2.5"
        )

    def _pid(self, patient: PatientState) -> str:
        dob = _fmt_date(patient.date_of_birth) if patient.date_of_birth else ""
        return (
            f"PID|||{patient.mrn}^^^SimHospital^MR"
            f"||{patient.family_name}^{patient.given_name}"
            f"||{dob}|{patient.gender}"
        )

    def _pv1(self, patient: PatientState) -> str:
        admit_dt = _fmt_dt(patient.admission_time) if patient.admission_time else ""
        return (
            f"PV1|1|I|{patient.ward}^^^{patient.bed}"
            f"||||||||||||||||||||||||||||||||||||||||||{admit_dt}"
        )

    def _build_adt(self, trigger: str, patient: PatientState, event_time: datetime) -> str:
        return "\r".join([
            self._msh("ADT", trigger, event_time),
            self._pid(patient),
            self._pv1(patient),
        ])

    def _build_order(self, patient: PatientState, event_time: datetime,
                     event: PathwayEvent, order_control: str) -> str:
        order_id = event.parameters.get("order_id", uuid4().hex[:8])
        order_profile = event.parameters.get("order_profile", uuid4().hex[:8])

        if order_control == "NW":
            patient.open_orders[order_id] = OrderDetails(order_profile, order_profile)
        elif order_control == "CA":
            patient.open_orders.pop(order_id, None)

        orc = f"ORC|{order_control}|{order_id}"
        obr = f"OBR|1|||{order_profile}^{order_profile}"

        return "\r".join([
            self._msh("ORM", "O01", event_time),
            self._pid(patient),
            self._pv1(patient),
            orc,
            obr,
        ])

    def _build_result(self, patient: PatientState, event_time: datetime,
                      event: PathwayEvent, panel: _TestPanel) -> str:
        order_profile = event.parameters.get("order_profile", panel.names[0])
        obr = f"OBR|1|||{order_profile}^{order_profile}"

        segments = [
            self._msh("ORU", "R01", event_time),
            self._pid(patient),
            self._pv1(patient),
            obr,
        ]

        num_results = 1 + self._rng.randint(0, 2)
        for i in range(num_results):
            idx = self._rng.randint(0, panel.size() - 1)
            low, high = panel.ranges[idx]
            value = low + self._rng.random() * (high - low)
            obx = (
                f"OBX|{i + 1}|NM|{panel.names[idx]}^{panel.names[idx]}"
                f"||{value:.1f}|{panel.units[idx]}|{low:.1f}-{high:.1f}||||F"
            )
            segments.append(obx)

        return "\r".join(segments)

    def _build_generic_adt(self, patient: PatientState, event_time: datetime,
                           event_type: PathwayEventType) -> str:
        trigger = _TRIGGER_EVENTS.get(event_type, "A01")
        return self._build_adt(trigger, patient, event_time)
