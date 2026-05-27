package com.github.peshk1n.columba.core;

public class SimulationResult {

    public float progress;
    public int sent;
    public int lost;
    public int corrupted;

    public SimulationResult(float progress, int sent, int lost, int corrupted) {
        this.progress = progress;
        this.sent = sent;
        this.lost = lost;
        this.corrupted = corrupted;
    }
}