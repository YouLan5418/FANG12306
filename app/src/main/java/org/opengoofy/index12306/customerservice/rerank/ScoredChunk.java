package org.opengoofy.index12306.customerservice.rerank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 带重排序分数的检索切片。
 * 重排序分数替代原始检索分数用于最终排序。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoredChunk {

    private RetrievedChunk chunk;
    private float rerankScore;
}