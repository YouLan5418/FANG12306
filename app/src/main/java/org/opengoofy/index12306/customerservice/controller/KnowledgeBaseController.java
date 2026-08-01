package org.opengoofy.index12306.customerservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.dao.entity.KnowledgeChunkDO;
import org.opengoofy.index12306.customerservice.dao.entity.IngestionTaskDO;
import org.opengoofy.index12306.customerservice.dao.mapper.IngestionTaskMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KnowledgeChunkMapper;
import org.opengoofy.index12306.customerservice.dto.req.KnowledgeChunkUpdateReqDTO;
import org.opengoofy.index12306.customerservice.dto.req.KbCategoryCreateReqDTO;
import org.opengoofy.index12306.customerservice.dto.resp.DocumentUploadRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.IngestionTaskRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.KbCategoryRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.KnowledgeChunkRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.KbDocumentRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.KnowledgeSearchRespDTO;
import org.opengoofy.index12306.customerservice.embedding.EmbeddingClient;
import org.opengoofy.index12306.customerservice.ingestion.IngestionPipelineService;
import org.opengoofy.index12306.customerservice.rag.RagRetriever;
import org.opengoofy.index12306.customerservice.rerank.ScoredChunk;
import org.opengoofy.index12306.customerservice.service.KbCategoryService;
import org.opengoofy.index12306.customerservice.service.KbDocumentService;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库后台管理控制器。
 * 提供分类 CRUD、文档上传与管理（触发入库管道）、入库任务查询与重试。
 */
@RestController
@RequestMapping("/api/admin/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KbCategoryService kbCategoryService;
    private final KbDocumentService kbDocumentService;
    private final IngestionPipelineService ingestionPipelineService;
    private final IngestionTaskMapper ingestionTaskMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final EmbeddingClient embeddingClient;
    private final RagRetriever ragRetriever;

    // ==================== 分类管理 ====================

    @PostMapping("/category")
    public Result<Long> createCategory(@RequestBody KbCategoryCreateReqDTO requestParam) {
        return Results.success(kbCategoryService.createCategory(requestParam));
    }

    @GetMapping("/category")
    public Result<List<KbCategoryRespDTO>> listCategories() {
        return Results.success(kbCategoryService.listCategories());
    }

    @PutMapping("/category/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody KbCategoryCreateReqDTO requestParam) {
        kbCategoryService.updateCategory(id, requestParam);
        return Results.success();
    }

    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        kbCategoryService.deleteCategory(id);
        return Results.success();
    }

    // ==================== 文档管理 ====================

    @PostMapping("/document/upload")
    public Result<DocumentUploadRespDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "parserType", required = false) String parserType,
            @RequestParam(value = "chunkMode", required = false) String chunkMode) {
        return Results.success(ingestionPipelineService.upload(file, categoryId, parserType, chunkMode));
    }

    @GetMapping("/document")
    public Result<List<KbDocumentRespDTO>> listDocuments(
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Results.success(kbDocumentService.listDocuments(categoryId));
    }

    @DeleteMapping("/document/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        kbDocumentService.deleteDocument(id);
        return Results.success();
    }

    // ==================== 入库任务 ====================

    @GetMapping("/task")
    public Result<List<IngestionTaskRespDTO>> listTasks() {
        List<IngestionTaskDO> tasks = ingestionTaskMapper.selectList(
                new LambdaQueryWrapper<IngestionTaskDO>()
                        .orderByDesc(IngestionTaskDO::getId));
        return Results.success(tasks.stream()
                .map(this::toTaskRespDTO)
                .toList());
    }

    @GetMapping("/task/{id}")
    public Result<IngestionTaskRespDTO> getTask(@PathVariable Long id) {
        IngestionTaskDO task = ingestionTaskMapper.selectById(id);
        return Results.success(task != null ? toTaskRespDTO(task) : null);
    }

    @PostMapping("/task/{id}/retry")
    public Result<Void> retryTask(@PathVariable Long id) {
        ingestionPipelineService.retryTask(id);
        return Results.success();
    }

    // ==================== 知识切片 ====================

    @GetMapping("/chunk")
    public Result<List<KnowledgeChunkRespDTO>> listChunks(
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        List<KnowledgeChunkDO> chunks = knowledgeChunkMapper.selectList(
                new LambdaQueryWrapper<KnowledgeChunkDO>()
                        .eq(documentId != null, KnowledgeChunkDO::getDocumentId, documentId)
                        .eq(categoryId != null, KnowledgeChunkDO::getCategoryId, categoryId)
                        .like(keyword != null && !keyword.isBlank(), KnowledgeChunkDO::getContent, keyword)
                        .orderByAsc(KnowledgeChunkDO::getDocumentId)
                        .orderByAsc(KnowledgeChunkDO::getChunkIndex)
                        .last("LIMIT 200"));
        return Results.success(chunks.stream()
                .map(this::toChunkRespDTO)
                .toList());
    }

    @PutMapping("/chunk/{id}")
    public Result<Void> updateChunk(@PathVariable Long id, @RequestBody KnowledgeChunkUpdateReqDTO requestParam) {
        if (requestParam.getContent() == null || requestParam.getContent().isBlank()) {
            throw new IllegalArgumentException("切片内容不能为空");
        }
        KnowledgeChunkDO chunk = knowledgeChunkMapper.selectById(id);
        if (chunk == null) {
            throw new IllegalArgumentException("知识切片不存在: " + id);
        }
        float[] embedding = embeddingClient.embed(requestParam.getContent());
        chunk.setContent(requestParam.getContent());
        chunk.setEmbedding(toFloatList(embedding));
        knowledgeChunkMapper.updateById(chunk);
        return Results.success();
    }

    @GetMapping("/search")
    public Result<List<KnowledgeSearchRespDTO>> searchKnowledge(
            @RequestParam("query") String query,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "topK", required = false, defaultValue = "5") Integer topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        int actualTopK = topK == null ? 5 : Math.max(1, Math.min(topK, 20));
        List<ScoredChunk> chunks = ragRetriever.retrieve(query.trim(), categoryId, actualTopK);
        return Results.success(chunks.stream()
                .map(each -> KnowledgeSearchRespDTO.builder()
                        .chunkId(each.getChunk().getChunkId())
                        .categoryId(each.getChunk().getCategoryId())
                        .documentId(each.getChunk().getDocumentId())
                        .chunkIndex(each.getChunk().getChunkIndex())
                        .content(each.getChunk().getContent())
                        .score(each.getRerankScore())
                        .source(each.getChunk().getSource())
                        .metadata(each.getChunk().getMetadata())
                        .build())
                .toList());
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    private IngestionTaskRespDTO toTaskRespDTO(IngestionTaskDO task) {
        return IngestionTaskRespDTO.builder()
                .id(task.getId())
                .documentId(task.getDocumentId())
                .status(task.getStatus())
                .totalChunks(task.getTotalChunks())
                .processedChunks(task.getProcessedChunks())
                .errorMsg(task.getErrorMsg())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }

    private KnowledgeChunkRespDTO toChunkRespDTO(KnowledgeChunkDO chunk) {
        return KnowledgeChunkRespDTO.builder()
                .id(chunk.getId())
                .categoryId(chunk.getCategoryId())
                .documentId(chunk.getDocumentId())
                .chunkIndex(chunk.getChunkIndex())
                .content(chunk.getContent())
                .metadata(chunk.getMetadata())
                .embeddingDimension(chunk.getEmbedding() != null ? chunk.getEmbedding().size() : 0)
                .createTime(chunk.getCreateTime())
                .build();
    }
}
