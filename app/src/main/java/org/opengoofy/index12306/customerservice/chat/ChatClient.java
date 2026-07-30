package org.opengoofy.index12306.customerservice.chat;

/**
 * Chat client abstraction for LLM communication.
 * Both local (Ollama) and cloud APIs use the OpenAI-compatible format.
 */
public interface ChatClient {

    String provider();

    /**
     * Synchronous (non-streaming) chat.
     */
    ChatResponse chat(ChatRequest request);

    /**
     * Asynchronous streaming chat. Returns immediately with a cancellation handle.
     * Content tokens are pushed to the callback on a background thread.
     */
    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback);

    /**
     * Blocking streaming chat that also collects the full response (content + tool_calls).
     * Content is streamed to the callback via ProbeStreamCallback first-packet routing:
     * if the LLM returns tool_calls, content is suppressed (not pushed to callback).
     * Used by the FC engine to decide tool execution vs. final answer.
     */
    ChatResponse streamChatCollect(ChatRequest request, StreamCallback callback);
}