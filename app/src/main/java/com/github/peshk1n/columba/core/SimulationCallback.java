package com.github.peshk1n.columba.core;

public interface SimulationCallback {
    void onUpdate(float progress, int sent, int lost, int corrupted);
    void onComplete();
}