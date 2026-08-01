package com.foggyframework.runtime.api.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/** Single-process linearization boundary for live Bundle publication mutations. */
@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeAuthoringPublicationLock {

    private final ReentrantLock lock = new ReentrantLock(true);

    public Guard acquire() {
        lock.lock();
        return new Guard(lock);
    }

    public static final class Guard implements AutoCloseable {
        private final ReentrantLock lock;
        private boolean closed;

        private Guard(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                lock.unlock();
            }
        }
    }
}
