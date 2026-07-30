package org.opengoofy.index12306.customerservice.chat;

/**
 * 流式响应回调接口，用于逐 token 接收 LLM 输出。
 * onContent 可能被调用零次或多次；onComplete 在成功结束时恰好调用一次。
 */
public interface StreamCallback {

    void onContent(String content);

    default void onThinking(String content) {
    }

    void onComplete();

    void onError(Throwable error);
}