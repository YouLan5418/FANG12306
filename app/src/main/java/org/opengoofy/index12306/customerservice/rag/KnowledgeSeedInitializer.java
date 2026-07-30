package org.opengoofy.index12306.customerservice.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.chunk.ChunkingStrategy;
import org.opengoofy.index12306.customerservice.chunk.ChunkingStrategyFactory;
import org.opengoofy.index12306.customerservice.dao.entity.KbCategoryDO;
import org.opengoofy.index12306.customerservice.dao.entity.KbDocumentDO;
import org.opengoofy.index12306.customerservice.dao.entity.KnowledgeChunkDO;
import org.opengoofy.index12306.customerservice.dao.mapper.KbCategoryMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KbDocumentMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KnowledgeChunkMapper;
import org.opengoofy.index12306.customerservice.embedding.EmbeddingClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 种子知识文档初始化器。
 * 应用启动时检查 knowledge_chunk 表是否为空，若为空则读取 resources/docs/knowledge/ 下的种子文档，
 * 解析切片后入库，确保 RAG 检索有基础语料可用。
 * 向量化失败时降级为仅存文本（关键词检索仍可用），初始化异常不影响应用启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSeedInitializer implements ApplicationRunner {

    private final KnowledgeChunkMapper chunkMapper;
    private final KbCategoryMapper categoryMapper;
    private final KbDocumentMapper documentMapper;
    private final EmbeddingClient embeddingClient;

    private final ChunkingStrategy chunker = ChunkingStrategyFactory.create("structure_aware");

    /** 种子文档配置：文件名 -> 分类名 */
    private static final String[][] SEEDS = {
            {"refund_rules.md", "退改规则"},
            {"purchase_guide.md", "购票流程"},
            {"id_requirements.md", "证件要求"}
    };
    private static final String SEED_DIR = "docs/knowledge/";

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long count = chunkMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("知识库已有 {} 条切片，跳过种子文档初始化", count);
                return;
            }
            log.info("知识库为空，开始加载种子文档...");
            int total = 0;
            for (String[] seed : SEEDS) {
                try {
                    total += loadSeed(seed[0], seed[1]);
                } catch (Exception e) {
                    log.error("加载种子文档失败: {}", seed[0], e);
                }
            }
            log.info("种子文档初始化完成，共入库 {} 条切片", total);
        } catch (Exception e) {
            log.warn("种子文档初始化失败，不影响应用启动: {}", e.getMessage());
        }
    }

    private int loadSeed(String fileName, String categoryName) {
        KbCategoryDO category = categoryMapper.selectOne(
                new LambdaQueryWrapper<KbCategoryDO>().eq(KbCategoryDO::getName, categoryName));
        if (category == null) {
            log.warn("分类不存在，跳过种子文档: {}", categoryName);
            return 0;
        }

        String content = readResource(SEED_DIR + fileName);
        if (content == null || content.isBlank()) {
            log.warn("种子文档为空: {}", fileName);
            return 0;
        }

        KbDocumentDO doc = new KbDocumentDO();
        doc.setCategoryId(category.getId());
        doc.setFileName(fileName);
        doc.setFileType("md");
        doc.setParserType("MARKDOWN");
        doc.setChunkMode("structure_aware");
        doc.setStatus(1);
        documentMapper.insert(doc);

        List<String> chunks = chunker.chunk(content);
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunkDO chunkDO = new KnowledgeChunkDO();
            chunkDO.setCategoryId(category.getId());
            chunkDO.setDocumentId(doc.getId());
            chunkDO.setChunkIndex(i);
            chunkDO.setContent(chunks.get(i));
            try {
                float[] vec = embeddingClient.embed(chunks.get(i));
                chunkDO.setEmbedding(toFloatList(vec));
            } catch (Exception e) {
                log.warn("种子切片向量化失败，仅存文本: {}", e.getMessage());
            }
            chunkMapper.insert(chunkDO);
        }

        doc.setStatus(2);
        doc.setChunkCount(chunks.size());
        documentMapper.updateById(doc);
        log.info("种子文档入库完成: {}, 切片数={}", fileName, chunks.size());
        return chunks.size();
    }

    private String readResource(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读取资源失败: {}", path);
            return null;
        }
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }
}