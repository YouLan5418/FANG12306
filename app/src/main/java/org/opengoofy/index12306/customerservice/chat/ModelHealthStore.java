package org.opengoofy.index12306.customerservice.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Circuit breaker for LLM model health tracking.
 * Three-state: CLOSED -> OPEN -> HALF_OPEN, per model id.
 * Uses ConcurrentHashMap.compute for atomic state transitions.
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
                // Open duration elapsed -> half-open probe
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
            // CLOSED
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
                // Probe failed -> back to open
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