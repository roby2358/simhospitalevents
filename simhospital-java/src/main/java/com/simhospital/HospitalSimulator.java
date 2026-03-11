package com.simhospital;

import ca.uhn.hl7v2.model.Message;
import com.simhospital.clock.SimulationClock;
import com.simhospital.clock.WallClock;
import com.simhospital.message.MessageFactory;
import com.simhospital.patient.PatientGenerator;
import com.simhospital.patient.PatientState;
import com.simhospital.pathway.Pathway;
import com.simhospital.pathway.PathwayEvent;
import com.simhospital.pathway.PathwayLoader;
import com.simhospital.simulator.ScheduledEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class HospitalSimulator {

    private final SimulatorConfig config;
    private final SimulationClock clock;
    private final MessageFactory messageFactory;
    private final PatientGenerator patientGenerator;
    private final Map<String, Pathway> pathways;
    private final List<String> pathwayNames;
    private final PriorityQueue<ScheduledEvent> eventQueue;
    private final Random random;
    private final boolean isWallClock;
    private Instant lastMessageTime;

    public HospitalSimulator(SimulatorConfig config) {
        this.config = config;
        this.clock = config.getClock();
        this.isWallClock = clock instanceof WallClock;
        this.random = new Random(config.getSeed());
        this.messageFactory = new MessageFactory(new Random(random.nextLong()));
        this.patientGenerator = new PatientGenerator(new Random(random.nextLong()));
        this.pathways = new PathwayLoader().load(config.getPathwayDirectory());
        this.pathwayNames = new ArrayList<>(pathways.keySet());
        this.eventQueue = new PriorityQueue<>();
        this.lastMessageTime = clock.now();

        for (int i = 0; i < config.getConcurrentPatients(); i++) {
            scheduleNewPatient();
        }
    }

    public Message nextMessage() throws InterruptedException {
        while (true) {
            ScheduledEvent scheduled = eventQueue.poll();
            if (scheduled == null) {
                throw new IllegalStateException("Event queue is empty");
            }

            clock.advanceTo(scheduled.scheduledTime());
            applyRateLimit();
            applyEventSideEffects(scheduled.event(), scheduled.patient(), scheduled.scheduledTime());

            Message message = messageFactory.build(scheduled.event(), scheduled.patient(), scheduled.scheduledTime());
            advancePatient(scheduled.patient(), scheduled.scheduledTime());

            if (message != null) {
                lastMessageTime = clock.now();
                return message;
            }
        }
    }

    private void applyRateLimit() throws InterruptedException {
        if (!isWallClock) return;
        if (config.getMessageRatePerSecond() <= 0) return;

        long intervalMs = (long) (1000.0 / config.getMessageRatePerSecond());
        Instant earliest = lastMessageTime.plusMillis(intervalMs);
        if (clock.now().isBefore(earliest)) {
            clock.advanceTo(earliest);
        }
    }

    private void applyEventSideEffects(PathwayEvent event, PatientState patient, Instant eventTime) {
        switch (event.type()) {
            case ADMISSION -> {
                patient.setAdmissionTime(eventTime);
                applyLocation(event, patient);
            }
            case TRANSFER -> applyLocation(event, patient);
            case DISCHARGE -> patient.setAdmissionTime(null);
            default -> {}
        }
    }

    private void applyLocation(PathwayEvent event, PatientState patient) {
        String loc = event.parameters().get("loc");
        if (loc != null) patient.setWard(loc);

        String bed = event.parameters().get("bed");
        if (bed != null) patient.setBed(bed);
    }

    private void advancePatient(PatientState patient, Instant previousEventTime) {
        patient.setNextEventIndex(patient.getNextEventIndex() + 1);

        Pathway pathway = pathways.get(patient.getPathwayName());
        if (patient.getNextEventIndex() < pathway.events().size()) {
            scheduleNextEvent(patient, previousEventTime);
        } else {
            scheduleNewPatient();
        }
    }

    private void scheduleNewPatient() {
        String pathwayName = pathwayNames.get(random.nextInt(pathwayNames.size()));
        Pathway pathway = pathways.get(pathwayName);
        PatientState patient = patientGenerator.generate(pathwayName);

        PathwayEvent firstEvent = pathway.events().get(0);
        Duration offset = Duration.ofMillis(random.nextInt(60_000));
        Instant scheduledTime = clock.now().plus(offset);
        if (firstEvent.delay() != null) {
            scheduledTime = scheduledTime.plus(firstEvent.delay());
        }

        eventQueue.add(new ScheduledEvent(scheduledTime, patient, firstEvent));
    }

    private void scheduleNextEvent(PatientState patient, Instant previousEventTime) {
        Pathway pathway = pathways.get(patient.getPathwayName());
        PathwayEvent nextEvent = pathway.events().get(patient.getNextEventIndex());

        Instant scheduledTime = previousEventTime;
        if (nextEvent.delay() != null) {
            scheduledTime = scheduledTime.plus(nextEvent.delay());
        } else {
            scheduledTime = scheduledTime.plus(Duration.ofMillis(1000 + random.nextInt(4000)));
        }

        eventQueue.add(new ScheduledEvent(scheduledTime, patient, nextEvent));
    }
}
