package org.opengoofy.index12306.customerservice.rag;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.dao.mapper.KnowledgeChunkMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KeywordRepairTests {
    @Test void naturalLanguageFallsBackToTerms() {
        var mapper = mock(KnowledgeChunkMapper.class);
        when(mapper.keywordSearch(anyString(), isNull(), anyInt())).thenReturn(List.of());
        var hit = new ChunkSearchResult();
        hit.setId(1L);
        hit.setContent("学生票购买条件");
        when(mapper.keywordSearchTerms(anyList(), isNull(), anyInt())).thenAnswer(inv -> {
            List<String> terms = inv.getArgument(0);
            assertTrue(terms.contains("学生"));
            assertTrue(terms.contains("生票"));
            return List.of(hit);
        });
        var result = new KeywordSearchChannel(mapper).search(null, "学生票怎么买", null, 5);
        assertEquals("学生票购买条件", result.get(0).getContent());
    }

    @Test void trainNumbersStayWholeAndTermsAreBounded() {
        assertTrue(KeywordSearchChannel.queryTerms("G35 学生票怎么买").contains("G35"));
        assertTrue(KeywordSearchChannel.queryTerms("中文".repeat(100)).size() <= 24);
        assertTrue(KeywordSearchChannel.queryTerms("% _ !!!").isEmpty());
    }
    @Test void mapperBindsKeywordTermsAsParameters() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.addMapper(KnowledgeChunkMapper.class);
        var params = new java.util.HashMap<String, Object>();
        params.put("terms", List.of("学生", "生票"));
        params.put("categoryId", null);
        params.put("topK", 5);
        var statement = configuration.getMappedStatement(KnowledgeChunkMapper.class.getName() + ".keywordSearchTerms");
        var bound = statement.getBoundSql(params);
        assertTrue(bound.getSql().contains("strpos(lower(content), lower(?)) > 0"));
        assertTrue(bound.getSql().contains("DESC, id LIMIT ?"));
        assertFalse(bound.getSql().contains("学生"));
        assertEquals(7, bound.getParameterMappings().size());
    }

}
