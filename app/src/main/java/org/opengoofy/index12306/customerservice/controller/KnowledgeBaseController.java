package org.opengoofy.index12306.customerservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.dao.entity.IngestionTaskDO;
import org.opengoofy.index12306.customerservice.dao.mapper.IngestionTaskMapper;
import org.opengoofy.index12306.customerservice.dto.req.KbCategoryCreateReqDTO;
import org.opengoofy.index12306.customerservice.dto.resp.IngestionTaskRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.KbCategoryRespDTO;
import org.opengoofy.index12306.customerservice.ingestion.IngestionPipelineService;
import org.opengoofy.index12306.customerservice.service.KbCategoryService;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库后台管理控制器。
 * 提供分类 CRUD、文档上传（触发入库管道）、入库任务查询与重试。
 */
@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KbCategoryService kbCategoryService;
    private final IngestionPipelineService ingestionPipelineService;
    private final IngestionTaskMapper ingestionTaskMapper;

    // ==================== 分类管理 ====================

    @PostMapping("/api/customerservice/kb/category")
    public Result<Long> createCategory(@RequestBody KbCategoryCreateReqDTO requestParam) {
        return Results.success(kbCategoryService.createCategory(requestParam));
    }

    @GetMapping("/api/customerservice/kb/category")
    public Result<List<KbCategoryRespDTO>> listCategories() {
        return Results.success(kbCategoryService.listCategories());
    }

    @PutMapping("/api/customerservice/kb/category/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody KbCategoryCreateReqDTO requestParam) {
        kbCategoryService.updateCategory(id, requestParam);
        return Results.success();
    }

    @DeleteMapping("/api/customerservice/kb/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        kbCategoryService.deleteCategory(id);
        return Results.success();
    }

    // ==================== 文档上传 ====================

    @PostMapping("/api/customerservice/kb/document/upload")
    public Result<Long> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "parserType", required = false) String parserType,
            @RequestParam(value = "chunkMode", required = false) String chunkMode) {
        return Results.success(ingestionPipelineService.upload(file, categoryId, parserType, chunkMode));
    }

    // ==================== 入库任务 ====================

    @GetMapping("/api/customerservice/kb/task")
    public Result<List<IngestionTaskRespDTO>> listTasks() {
        List<IngestionTaskDO> tasks = ingestionTaskMapper.selectList(
                new LambdaQueryWrapper<IngestionTaskDO>()
                        .orderByDesc(IngestionTaskDO::getId));
        return Results.success(tasks.stream()
                .map(this::toTaskRespDTO)
                .toList());
    }

    @GetMapping("/api/customerservice/kb/task/{id}")
    public Result<IngestionTaskRespDTO> getTask(@PathVariable Long id) {
        IngestionTaskDO task = ingestionTaskMapper.selectById(id);
        return Results.success(task != null ? toTaskRespDTO(task) : null);
    }

    @PostMapping("/api/customerservice/kb/task/{id}/retry")
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