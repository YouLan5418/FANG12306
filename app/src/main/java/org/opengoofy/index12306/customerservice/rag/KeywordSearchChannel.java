package org.opengoofy.index12306.customerservice.rag;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.dao.mapper.KnowledgeChunkMapper;
import org.opengoofy.index12306.customerservice.rerank.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 关键词检索通道，基于 PostgreSQL ILIKE + pg_trgm GIN 索引。
 * 适合精确字段查询（错误码、车次号等），不依赖中文分词扩展。
 */
@Component
@RequiredArgsConstructor
public class KeywordSearchChannel implements SearchChannel {

    private final KnowledgeChunkMapper chunkMapper;

    @Override
    public String getChannelName() {
        return "keyword";
    }

    @Override
    public List<RetrievedChunk> search(float[] queryVec, String queryText, Long categoryId, int topK) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        List<ChunkSearchResult> results = chunkMapper.keywordSearch(queryText.trim(), categoryId, topK);
        List<RetrievedChunk> hits = new ArrayList<>(results.size());
        for (ChunkSearchResult r : results) {
            hits.add(RetrievedChunk.builder()
                    .chunkId(r.getId())
                    .documentId(r.getDocumentId())
                    .categoryId(r.getCategoryId())
                    .chunkIndex(r.getChunkIndex())
                    .content(r.getContent())
                    .metadata(r.getMetadata())
                    .score(1.0f)
                    .source(getChannelName())
                    .build());
        }
        return hits;
    }
}
