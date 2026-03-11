package com.simhospital;

import com.simhospital.clock.SyntheticClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SyntheticClockTest {

    @Test
    void advanceTo_setsNowToTarget() throws InterruptedException {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        SyntheticClock clock = new SyntheticClock(start);

        Instant target = Instant.parse("2024-01-01T01:00:00Z");
        clock.advanceTo(target);

        assertEquals(target, clock.now());
    }

    @Test
    void advanceTo_earlierTime_isNoOp() throws InterruptedException {
        Instant start = Instant.parse("2024-01-01T12:00:00Z");
        SyntheticClock clock = new SyntheticClock(start);

        Instant earlier = Instant.parse("2024-01-01T06:00:00Z");
        clock.advanceTo(earlier);

        assertEquals(start, clock.now(), "Clock must not go backwards");
    }

    @Test
    void advanceTo_sameTime_isNoOp() throws InterruptedException {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        SyntheticClock clock = new SyntheticClock(start);

        clock.advanceTo(start);

        assertEquals(start, clock.now());
    }

    @Test
    void now_returnsStartTime_initially() {
        Instant start = Instant.parse("2024-06-15T10:30:00Z");
        SyntheticClock clock = new SyntheticClock(start);

        assertEquals(start, clock.now());
    }
}
