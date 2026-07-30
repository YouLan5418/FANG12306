package org.opengoofy.index12306.customerservice.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LLM 模型健康状态存储器，实现断路器模式。
 * 三态流转：CLOSED -> OPEN -> HALF_OPEN，按模型 ID 独立熔断。
 * 使用 ConcurrentHashMap.compute 保证原子状态流转。
 */
@Component
@RequiredArgsConstructor
public class ModelHealthStore {

    private final ChatProperties properties;

    private final Map<String, ModelHealth> healthById = new ConcurrentHashMap<>();

    public boolean isUnavailable(String modelId) {
        ModelHealth health = healthById.get(modelId);
        if (health == null) {
            return false;
        }
        if (health.state == State.OPEN && health.openUntil > System.currentTimeMillis()) {
            return true;
        }
        return health.state == State.HALF_OPEN && health.halfOpenInFlight;
    }

    public boolean allowCall(String modelId) {
        if (modelId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        healthById.compute(modelId, (k, v) -> {
            if (v == null) {
                v = new ModelHealth();
            }
            if (v.state == State.OPEN) {
                if (v.openUntil > now) {
                    return v;
                }
                // 熔断时长已过 -> 进入半开探测
                v.state = State.HALF_OPEN;
                v.halfOpenInFlight = true;
                allowed.set(true);
                return v;
            }
            if (v.state == State.HALF_OPEN) {
                if (v.halfOpenInFlight) {
                    return v;
                }
                v.halfOpenInFlight = true;
                allowed.set(true);
                return v;
            }
            // CLOSED 状态，放行调用
            allowed.set(true);
            return v;
        });
        return allowed.get();
    }

    public void markSuccess(String modelId) {
        if (modelId == null) {
            return;
        }
        healthById.compute(modelId, (k, v) -> {
            if (v == null) {
                return new ModelHealth();
            }
            v.state = State.CLOSED;
            v.consecutiveFailures = 0;
            v.openUntil = 0L;
            v.halfOpenInFlight = false;
            return v;
        });
    }

    public void markFailure(String modelId) {
        if (modelId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        healthById.compute(modelId, (k, v) -> {
            if (v == null) {
                v = new ModelHealth();
            }
            if (v.state == State.HALF_OPEN) {
                // 探测失败 -> 重新熔断
                v.state = State.OPEN;
                v.openUntil = now + properties.getOpenDurationMs();
                v.consecutiveFailures = 0;
                v.halfOpenInFlight = false;
                return v;
            }
            v.consecutiveFailures++;
            if (v.consecutiveFailures >= properties.getFailureThreshold()) {
                v.state = State.OPEN;
                v.openUntil = now + properties.getOpenDurationMs();
                v.consecutiveFailures = 0;
            }
            return v;
        });
    }

    private static class ModelHealth {
        int consecutiveFailures;
        long openUntil;
        boolean halfOpenInFlight;
        State state;

        ModelHealth() {
            this.consecutiveFailures = 0;
            this.openUntil = 0L;
            this.halfOpenInFlight = false;
            this.state = State.CLOSED;
        }
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}