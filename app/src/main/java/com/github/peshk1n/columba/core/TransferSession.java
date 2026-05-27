package com.github.peshk1n.columba.core;

public class TransferSession {

    static {
        System.loadLibrary("columba");
    }

    private long nativeHandle;

    public TransferSession(String saveDir) {
        nativeHandle = nativeCreate(saveDir);
    }

    public void initSender(String filePath, int chunkSize, int windowSize) {
        nativeInitSender(nativeHandle, filePath, chunkSize, windowSize);
    }

    public void initReceiver() {
        nativeInitReceiver(nativeHandle);
    }

    public void tick(long nowMs) {
        nativeTick(nativeHandle, nowMs);
    }

    public float getProgress() {
        return nativeGetProgress(nativeHandle);
    }

    public boolean isDone() {
        return nativeIsDone(nativeHandle);
    }

    private native long nativeCreate(String saveDir);
    private native void nativeInitSender(long ptr, String filePath, int chunk, int window);
    private native void nativeInitReceiver(long ptr);
    private native void nativeTick(long ptr, long nowMs);
    private native float nativeGetProgress(long ptr);
    private native boolean nativeIsDone(long ptr);
}