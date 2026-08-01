package org.opengoofy.index12306.customerservice.chat;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process concurrency guard for expensive AI calls.
 */
@Component
public class AiConcurrencyLimiter {

    private final ChatProperties properties;
    private final Semaphore globalSemaphore;
    private final ConcurrentHashMap<String, AtomicInteger> userCounters = new ConcurrentHashMap<>();

    public AiConcurrencyLimiter(ChatProperties properties) {
        this.properties = properties;
        this.globalSemaphore = new Semaphore(Math.max(1, properties.getGlobalMaxConcurrent()));
    }

    public Permit acquire(String username) {
        if (!globalSemaphore.tryAcquire()) {
            throw new RuntimeException("智能客服繁忙，请稍后再试");
        }
        String effectiveUsername = username == null || username.isBlank() ? "anonymous" : username;
        AtomicInteger userCounter = userCounters.computeIfAbsent(effectiveUsername, ignored -> new AtomicInteger());
        int current = userCounter.incrementAndGet();
        if (current > Math.max(1, properties.getUserMaxConcurrent())) {
            releaseUser(effectiveUsername);
            globalSemaphore.release();
            throw new RuntimeException("当前账号已有客服请求处理中，请稍后再试");
        }
        return new Permit(effectiveUsername);
    }

    private void releaseUser(String username) {
        AtomicInteger counter = userCounters.get(username);
        if (counter == null) {
            return;
        }
        if (counter.decrementAndGet() <= 0) {
            userCounters.remove(username, counter);
        }
    }

    public final class Permit implements AutoCloseable {

        private final String username;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Permit(String username) {
            this.username = username;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                releaseUser(username);
                globalSemaphore.release();
            }
        }
    }
}
