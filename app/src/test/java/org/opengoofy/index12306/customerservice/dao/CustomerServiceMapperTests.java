package org.opengoofy.index12306.customerservice.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.dao.entity.ChatMessageDO;
import org.opengoofy.index12306.customerservice.dao.entity.ChatSessionDO;
import org.opengoofy.index12306.customerservice.dao.entity.IngestionTaskDO;
import org.opengoofy.index12306.customerservice.dao.entity.KbCategoryDO;
import org.opengoofy.index12306.customerservice.dao.entity.KbDocumentDO;
import org.opengoofy.index12306.customerservice.dao.entity.KnowledgeChunkDO;
import org.opengoofy.index12306.customerservice.dao.mapper.ChatMessageMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.ChatSessionMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.IngestionTaskMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KbCategoryMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KbDocumentMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KnowledgeChunkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-T4 验收：客服域 Mapper CRUD；knowledge_chunk 插入 pgvector vector(1024) 成功并可余弦检索。
 * 事务回滚，不污染数据库。
 */
@SpringBootTest
@Transactional
class CustomerServiceMapperTests {

    @Autowired
    private KbCategoryMapper kbCategoryMapper;
    @Autowired
    private KbDocumentMapper kbDocumentMapper;
    @Autowired
    private KnowledgeChunkMapper knowledgeChunkMapper;
    @Autowired
    private IngestionTaskMapper ingestionTaskMapper;
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long newCategory() {
        KbCategoryDO category = new KbCategoryDO();
        category.setName("测试分类" + System.nanoTime());
        category.setDescription("Mapper 测试用");
        assertEquals(1, kbCategoryMapper.insert(category));
        assertNotNull(category.getId());
        return category.getId();
    }

    private Long newDocument(Long categoryId) {
        KbDocumentDO document = new KbDocumentDO();
        document.setCategoryId(categoryId);
        document.setFileName("退票规则.md");
        document.setFilePath("/tmp/退票规则.md");
        document.setFileType("markdown");
        document.setParserType("MARKDOWN");
        document.setChunkMode("structure_aware");
        document.setStatus(0);
        document.setChunkCount(0);
        assertEquals(1, kbDocumentMapper.insert(document));
        assertNotNull(document.getId());
        return document.getId();
    }

    private static List<Float> unitVector(int dim, int hotIndex) {
        List<Float> vector = new ArrayList<>(dim);
        for (int i = 0; i < dim; i++) {
            vector.add(i == hotIndex ? 1.0f : 0.0f);
        }
        return vector;
    }

    @Test
    void testCategorySeedAndCrud() {
        // P0-T3 种下的 3 个分类应存在
        List<KbCategoryDO> seeds = kbCategoryMapper.selectList(null);
        assertTrue(seeds.size() >= 3, "kb_category 应有种子数据");
        Long id = newCategory();
        KbCategoryDO actual = kbCategoryMapper.selectById(id);
        assertEquals("Mapper 测试用", actual.getDescription());
    }

    @Test
    void testDocumentCrud() {
        Long categoryId = newCategory();
        Long documentId = newDocument(categoryId);
        KbDocumentDO actual = kbDocumentMapper.selectOne(new LambdaQueryWrapper<KbDocumentDO>()
                .eq(KbDocumentDO::getCategoryId, categoryId));
        assertNotNull(actual);
        assertEquals(documentId, actual.getId());
        assertEquals("structure_aware", actual.getChunkMode());
    }

    @Test
    void testKnowledgeChunkInsertVector() {
        Long categoryId = newCategory();
        Long documentId = newDocument(categoryId);

        KnowledgeChunkDO chunk = new KnowledgeChunkDO();
        chunk.setCategoryId(categoryId);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(0);
        chunk.setContent("开车前 8 天以上退票免手续费");
        chunk.setEmbedding(unitVector(1024, 0));
        chunk.setMetadata("{\"heading\":\"退票手续费\",\"page\":1}");
        assertEquals(1, knowledgeChunkMapper.insert(chunk));
        assertNotNull(chunk.getId());

        // 读回：向量维度与内容一致，JSONB 原文可读
        KnowledgeChunkDO actual = knowledgeChunkMapper.selectById(chunk.getId());
        assertNotNull(actual);
        assertEquals(1024, actual.getEmbedding().size());
        assertEquals(1.0f, actual.getEmbedding().get(0), 1e-6);
        assertTrue(actual.getMetadata().contains("退票手续费"));

        // 库内类型确为 vector(1024)
        Integer dims = jdbcTemplate.queryForObject(
                "SELECT vector_dims(embedding) FROM knowledge_chunk WHERE id = ?", Integer.class, chunk.getId());
        assertEquals(1024, dims);

        // 余弦相似度检索：与第 0 维相同的向量应排在最前
        KnowledgeChunkDO other = new KnowledgeChunkDO();
        other.setCategoryId(categoryId);
        other.setDocumentId(documentId);
        other.setChunkIndex(1);
        other.setContent("改签不收取手续费");
        other.setEmbedding(unitVector(1024, 1));
        assertEquals(1, knowledgeChunkMapper.insert(other));

        List<Long> topIds = jdbcTemplate.queryForList(
                "SELECT id FROM knowledge_chunk WHERE document_id = ? ORDER BY embedding <=> ?::vector LIMIT 1",
                Long.class, documentId, org.opengoofy.index12306.common.config.VectorTypeHandler.toLiteral(unitVector(1024, 0)));
        assertEquals(chunk.getId(), topIds.get(0));
    }

    @Test
    void testIngestionTaskStatusFlow() {
        Long categoryId = newCategory();
        Long documentId = newDocument(categoryId);
        IngestionTaskDO task = new IngestionTaskDO();
        task.setDocumentId(documentId);
        task.setStatus(0);
        task.setTotalChunks(10);
        task.setProcessedChunks(0);
        assertEquals(1, ingestionTaskMapper.insert(task));
        assertNotNull(task.getId());

        task.setStatus(4);
        task.setProcessedChunks(10);
        assertEquals(1, ingestionTaskMapper.updateById(task));
        IngestionTaskDO actual = ingestionTaskMapper.selectById(task.getId());
        assertEquals(4, actual.getStatus());
        assertEquals(10, actual.getProcessedChunks());
        assertTrue(actual.getUpdateTime().getTime() >= actual.getCreateTime().getTime());
    }

    @Test
    void testChatSessionAndMessage() {
        String sessionId = "session-" + System.nanoTime();
        ChatSessionDO session = new ChatSessionDO();
        session.setSessionId(sessionId);
        session.setUsername("mapper_test");
        session.setTitle("退票咨询");
        assertEquals(1, chatSessionMapper.insert(session));

        ChatMessageDO message = new ChatMessageDO();
        message.setSessionId(sessionId);
        message.setRole("tool");
        message.setToolName("searchKnowledge");
        message.setToolArgs("{\"query\":\"退票手续费\"}");
        message.setToolResult("开车前 8 天以上免手续费");
        assertEquals(1, chatMessageMapper.insert(message));

        List<ChatMessageDO> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getSessionId, sessionId));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getToolArgs().contains("退票手续费"));
    }
}