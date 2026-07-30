package org.opengoofy.index12306.customerservice.chat;

/**
 * LLM 聊天客户端抽象接口。
 * 本地 Ollama 和云端 API 均走 OpenAI 兼容格式。
 */
public interface ChatClient {

    String provider();

    /**
     * 同步（非流式）聊天。
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 异步流式聊天。立即返回取消句柄。
     * 内容 token 在后台线程中推送给回调。
     */
    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback);

    /**
     * 阻塞式流式聊天，同时收集完整响应（content + tool_calls）。
     * 通过 ProbeStreamCallback 首包分流：若 LLM 返回 tool_calls，
     * 则 content 不推送给回调。FC 引擎用此方法判断工具调用还是最终回复。
     */
    ChatResponse streamChatCollect(ChatRequest request, StreamCallback callback);
}