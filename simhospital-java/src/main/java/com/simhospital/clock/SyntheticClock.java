package com.simhospital.clock;

import java.time.Instant;

public class SyntheticClock implements SimulationClock {

    private Instant currentTime;

    public SyntheticClock(Instant startTime) {
        this.currentTime = startTime;
    }

    @Override
    public Instant now() {
        return currentTime;
    }

    @Override
    public void advanceTo(Instant target) {
        if (target.isAfter(currentTime)) {
            currentTime = target;
        }
    }
}
