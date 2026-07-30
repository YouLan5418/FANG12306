package org.opengoofy.index12306.customerservice.chat;

/**
 * Streaming response callback for LLM token-by-token output.
 * onContent may be called zero or more times; onComplete exactly once on success.
 */
public interface StreamCallback {

    void onContent(String content);

    default void onThinking(String content) {
    }

    void onComplete();

    void onError(Throwable error);
}