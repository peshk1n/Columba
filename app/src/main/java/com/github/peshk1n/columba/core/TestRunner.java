package com.github.peshk1n.columba.core;

public class TestRunner {

    static {
        System.loadLibrary("columba");
    }

    public native void startSimulation(
            String filePath,
            String saveDir,
            float loss,
            float corruption,
            int delayMs,
            SimulationCallback callback
    );
}