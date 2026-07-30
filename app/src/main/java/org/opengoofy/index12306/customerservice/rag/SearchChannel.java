package org.opengoofy.index12306.customerservice.rag;

import org.opengoofy.index12306.customerservice.rerank.RetrievedChunk;

import java.util.List;

/**
 * 检索通道抽象接口。
 * 向量通道使用 queryVec，关键词通道使用 queryText，各取所需。
 */
public interface SearchChannel {

    /**
     * 通道名称标识，写入 RetrievedChunk.source 字段。
     *
     * @return "vector" 或 "keyword"
     */
    String getChannelName();

    /**
     * 执行检索，返回按相关度降序排列的切片。
     *
     * @param queryVec  查询文本的向量表示（关键词通道可忽略）
     * @param queryText 查询原文（向量通道可忽略）
     * @param categoryId 分类 ID，null 表示不限分类
     * @param topK      最大返回数量
     * @return 检索结果列表
     */
    List<RetrievedChunk> search(float[] queryVec, String queryText, Long categoryId, int topK);
}