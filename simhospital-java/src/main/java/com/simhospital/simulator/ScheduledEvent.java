package com.simhospital.simulator;

import com.simhospital.patient.PatientState;
import com.simhospital.pathway.PathwayEvent;

import java.time.Instant;

public record ScheduledEvent(
    Instant scheduledTime,
    PatientState patient,
    PathwayEvent event
) implements Comparable<ScheduledEvent> {

    @Override
    public int compareTo(ScheduledEvent other) {
        return this.scheduledTime.compareTo(other.scheduledTime);
    }
}
