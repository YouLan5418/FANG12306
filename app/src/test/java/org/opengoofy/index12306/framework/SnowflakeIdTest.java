package org.opengoofy.index12306.framework;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.framework.distributedid.core.snowflake.Snowflake;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdTest {

    @Test
    void testConcurrentNoDuplicate() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 1000;
        Snowflake snowflake = new Snowflake();
        Set<Long> ids = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        ids.add(snowflake.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        assertEquals(threadCount * idsPerThread, ids.size(), "并发生成的 ID 不应有重复");
    }

    @Test
    void testMonotonicIncrease() {
        Snowflake snowflake = new Snowflake();
        long prev = snowflake.nextId();
        boolean increasing = true;
        for (int i = 0; i < 100; i++) {
            long curr = snowflake.nextId();
            if (curr <= prev) {
                increasing = false;
                break;
            }
            prev = curr;
        }
        assertTrue(increasing, "单线程生成的 ID 应单调递增");
    }
}
