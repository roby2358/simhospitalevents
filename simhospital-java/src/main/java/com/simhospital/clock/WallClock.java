package com.simhospital.clock;

import java.time.Duration;
import java.time.Instant;

public class WallClock implements SimulationClock {

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public void advanceTo(Instant target) throws InterruptedException {
        while (true) {
            Instant current = Instant.now();
            if (current.compareTo(target) >= 0) {
                return;
            }
            Duration remaining = Duration.between(current, target);
            Thread.sleep(remaining.toMillis());
        }
    }
}
