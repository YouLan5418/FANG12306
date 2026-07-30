package org.opengoofy.index12306.customerservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.dao.entity.IngestionTaskDO;
import org.opengoofy.index12306.customerservice.dao.mapper.IngestionTaskMapper;
import org.opengoofy.index12306.customerservice.dto.req.KbCategoryCreateReqDTO;
import org.opengoofy.index12306.customerservice.dto.resp.DocumentUploadRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.IngestionTaskRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.KbCategoryRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.KbDocumentRespDTO;
import org.opengoofy.index12306.customerservice.ingestion.IngestionPipelineService;
import org.opengoofy.index12306.customerservice.service.KbCategoryService;
import org.opengoofy.index12306.customerservice.service.KbDocumentService;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库后台管理控制器。
 * 提供分类 CRUD、文档上传与管理（触发入库管道）、入库任务查询与重试。
 */
@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KbCategoryService kbCategoryService;
    private final KbDocumentService kbDocumentService;
    private final IngestionPipelineService ingestionPipelineService;
    private final IngestionTaskMapper ingestionTaskMapper;

    // ==================== 分类管理 ====================

    @PostMapping("/api/customer-service/kb/category")
    public Result<Long> createCategory(@RequestBody KbCategoryCreateReqDTO requestParam) {
        return Results.success(kbCategoryService.createCategory(requestParam));
    }

    @GetMapping("/api/customer-service/kb/category")
    public Result<List<KbCategoryRespDTO>> listCategories() {
        return Results.success(kbCategoryService.listCategories());
    }

    @PutMapping("/api/customer-service/kb/category/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody KbCategoryCreateReqDTO requestParam) {
        kbCategoryService.updateCategory(id, requestParam);
        return Results.success();
    }

    @DeleteMapping("/api/customer-service/kb/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        kbCategoryService.deleteCategory(id);
        return Results.success();
    }

    // ==================== 文档管理 ====================

    @PostMapping("/api/customer-service/kb/document/upload")
    public Result<DocumentUploadRespDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "parserType", required = false) String parserType,
            @RequestParam(value = "chunkMode", required = false) String chunkMode) {
        return Results.success(ingestionPipelineService.upload(file, categoryId, parserType, chunkMode));
    }

    @GetMapping("/api/customer-service/kb/document")
    public Result<List<KbDocumentRespDTO>> listDocuments(
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Results.success(kbDocumentService.listDocuments(categoryId));
    }

    @DeleteMapping("/api/customer-service/kb/document/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        kbDocumentService.deleteDocument(id);
        return Results.success();
    }

    // ==================== 入库任务 ====================

    @GetMapping("/api/customer-service/kb/task")
    public Result<List<IngestionTaskRespDTO>> listTasks() {
        List<IngestionTaskDO> tasks = ingestionTaskMapper.selectList(
                new LambdaQueryWrapper<IngestionTaskDO>()
                        .orderByDesc(IngestionTaskDO::getId));
        return Results.success(tasks.stream()
                .map(this::toTaskRespDTO)
                .toList());
    }

    @GetMapping("/api/customer-service/kb/task/{id}")
    public Result<IngestionTaskRespDTO> getTask(@PathVariable Long id) {
        IngestionTaskDO task = ingestionTaskMapper.selectById(id);
        return Results.success(task != null ? toTaskRespDTO(task) : null);
    }

    @PostMapping("/api/customer-service/kb/task/{id}/retry")
    public Result<Void> retryTask(@PathVariable Long id) {
        ingestionPipelineService.retryTask(id);
        return Results.success();
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
}