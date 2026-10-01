package io.vanta.app.xserver;

public interface XLock extends AutoCloseable {
    @Override
    void close();
}
