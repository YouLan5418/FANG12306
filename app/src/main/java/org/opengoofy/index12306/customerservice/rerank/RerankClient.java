package org.opengoofy.index12306.customerservice.rerank;

import java.util.List;

/**
 * 重排序客户端抽象接口，按与查询的相关性对检索到的切片重新排序。
 * 实现必须优雅降级：失败时返回原始候选（限制为 topN），不抛异常、不中断 RAG 管道。
 */
public interface RerankClient {

    /**
     * 按与查询的相关性重排候选切片。
     *
     * @param query     用户查询文本
     * @param candidates 混合检索的候选切片
     * @param topN      最大返回数量
     * @return 按相关性降序排列的切片，限制为 topN 条
     */
    List<ScoredChunk> rerank(String query, List<RetrievedChunk> candidates, int topN);
}