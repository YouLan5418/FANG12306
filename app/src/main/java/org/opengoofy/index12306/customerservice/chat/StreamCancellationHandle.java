package org.opengoofy.index12306.customerservice.chat;

/**
 * 流式取消句柄，用于中断正在进行的流式请求。
 * 多次调用 cancel 必须安全（幂等）。
 */
public interface StreamCancellationHandle {

    void cancel();
}