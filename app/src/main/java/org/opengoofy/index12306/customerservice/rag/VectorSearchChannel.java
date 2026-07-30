package org.opengoofy.index12306.customerservice.rag;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.common.config.VectorTypeHandler;
import org.opengoofy.index12306.customerservice.dao.mapper.KnowledgeChunkMapper;
import org.opengoofy.index12306.customerservice.rerank.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量检索通道，基于 pgvector 余弦相似度（HNSW 索引）。
 * 将查询文本向量化后在 knowledge_chunk 表中做近邻搜索。
 */
@Component
@RequiredArgsConstructor
public class VectorSearchChannel implements SearchChannel {

    private final KnowledgeChunkMapper chunkMapper;

    @Override
    public String getChannelName() {
        return "vector";
    }

    @Override
    public List<RetrievedChunk> search(float[] queryVec, String queryText, Long categoryId, int topK) {
        if (queryVec == null || queryVec.length == 0) {
            return List.of();
        }
        String vecLiteral = VectorTypeHandler.toLiteral(toFloatList(queryVec));
        List<ChunkSearchResult> results = chunkMapper.vectorSearch(vecLiteral, categoryId, topK);
        List<RetrievedChunk> hits = new ArrayList<>(results.size());
        for (ChunkSearchResult r : results) {
            hits.add(RetrievedChunk.builder()
                    .chunkId(r.getId())
                    .documentId(r.getDocumentId())
                    .categoryId(r.getCategoryId())
                    .chunkIndex(r.getChunkIndex())
                    .content(r.getContent())
                    .score(r.getScore() != null ? r.getScore().floatValue() : 0f)
                    .source(getChannelName())
                    .build());
        }
        return hits;
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }
}