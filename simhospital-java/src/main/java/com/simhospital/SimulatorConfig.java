package com.simhospital;

import com.simhospital.clock.SimulationClock;

import java.nio.file.Path;
import java.util.Objects;

public class SimulatorConfig {
    private final Path pathwayDirectory;
    private final int concurrentPatients;
    private final SimulationClock clock;
    private final long seed;
    private final double messageRatePerSecond;

    public SimulatorConfig(
            Path pathwayDirectory,
            int concurrentPatients,
            SimulationClock clock,
            long seed,
            double messageRatePerSecond
    ) {
        this.pathwayDirectory = Objects.requireNonNull(pathwayDirectory, "pathwayDirectory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.concurrentPatients = concurrentPatients;
        this.seed = seed;
        this.messageRatePerSecond = messageRatePerSecond;
    }

    public Path getPathwayDirectory() { return pathwayDirectory; }
    public int getConcurrentPatients() { return concurrentPatients; }
    public SimulationClock getClock() { return clock; }
    public long getSeed() { return seed; }
    public double getMessageRatePerSecond() { return messageRatePerSecond; }
}
