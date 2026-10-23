package dev.kuratoon.support;

import dev.kuratoon.ingestion.ImportFaults;

/**
 * Stops the import pipeline at one {@link ImportFaults.Step}: either as a crash (an Error that no
 * pipeline code catches, so nothing is cleaned up, like a killed process) or as an ordinary
 * exception.
 */
public class TestFaults implements ImportFaults {

    /** A simulated process death. */
    public static class SimulatedCrash extends Error {
        public SimulatedCrash(Step step) {
            super("simulated crash at " + step);
        }
    }

    private volatile Step crashAt;
    private volatile Step failAt;

    public void crashAt(Step step) {
        this.crashAt = step;
    }

    public void failAt(Step step) {
        this.failAt = step;
    }

    public void reset() {
        crashAt = null;
        failAt = null;
    }

    @Override
    public void checkpoint(Step step) {
        if (step == crashAt) {
            // One crash per test: the restarted pipeline runs through.
            crashAt = null;
            throw new SimulatedCrash(step);
        }
        if (step == failAt) {
            failAt = null;
            throw new IllegalStateException("simulated failure at " + step);
        }
    }
}
