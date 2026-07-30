package org.opengoofy.index12306.customerservice.chat;

/**
 * Handle to cancel an in-progress streaming request.
 * Multiple calls to cancel must be safe (idempotent).
 */
public interface StreamCancellationHandle {

    void cancel();
}