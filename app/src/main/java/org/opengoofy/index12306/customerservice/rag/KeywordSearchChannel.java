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
        List<String> terms = queryTerms(queryText);
        if (terms.isEmpty()) return List.of();
        String literalQuery = queryText.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        List<ChunkSearchResult> results = chunkMapper.keywordSearch(literalQuery, categoryId, topK);
        if (results.isEmpty()) {
            results = chunkMapper.keywordSearchTerms(terms, categoryId, topK);
        }
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
    static List<String> queryTerms(String query) {
        java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
        var matcher = java.util.regex.Pattern.compile("[\\p{IsHan}]+|[A-Za-z0-9_-]+").matcher(query);
        while (matcher.find()) {
            String part = matcher.group();
            if (Character.UnicodeScript.of(part.codePointAt(0)) == Character.UnicodeScript.HAN) {
                int[] points = part.codePoints().toArray();
                if (points.length == 1) terms.add(part);
                else for (int i = 0; i < points.length - 1; i++) terms.add(new String(points, i, 2));
            } else if (part.codePoints().anyMatch(Character::isLetterOrDigit)) terms.add(part);
        }
        return terms.stream().limit(24).toList();
    }

}
