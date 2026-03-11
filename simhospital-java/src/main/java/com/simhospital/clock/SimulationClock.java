package com.simhospital.clock;

import java.time.Instant;

public interface SimulationClock {
    /** Returns the current simulation time. */
    Instant now();

    /**
     * Advances the clock to the given target time.
     *
     * WallClock: blocks the calling thread until wall time reaches or exceeds target.
     * SyntheticClock: sets internal time to target and returns immediately.
     *
     * Calls with a target earlier than now() are a no-op — the clock never goes backwards.
     */
    void advanceTo(Instant target) throws InterruptedException;
}
