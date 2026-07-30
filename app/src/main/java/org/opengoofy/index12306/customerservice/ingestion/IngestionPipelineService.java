package org.opengoofy.index12306.customerservice.ingestion;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.chunk.ChunkingStrategy;
import org.opengoofy.index12306.customerservice.chunk.ChunkingStrategyFactory;
import org.opengoofy.index12306.customerservice.dao.entity.IngestionTaskDO;
import org.opengoofy.index12306.customerservice.dao.entity.KbDocumentDO;
import org.opengoofy.index12306.customerservice.dao.entity.KnowledgeChunkDO;
import org.opengoofy.index12306.customerservice.dao.mapper.IngestionTaskMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KbDocumentMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KnowledgeChunkMapper;
import org.opengoofy.index12306.customerservice.embedding.EmbeddingClient;
import org.opengoofy.index12306.customerservice.parser.DocumentParser;
import org.opengoofy.index12306.customerservice.parser.DocumentParserSelector;
import org.opengoofy.index12306.customerservice.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 知识库入库管道：上传 -> 解析 -> 切片 -> 向量化 -> 存储。
 * 使用 Semaphore(3) 控制并发，状态机驱动重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionPipelineService {

    private final KbDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final IngestionTaskMapper taskMapper;
    private final FileStorageService fileStorage;
    private final DocumentParserSelector parserSelector;
    private final EmbeddingClient embeddingClient;

    private final Semaphore pipelineSlots = new Semaphore(3);
    private final ExecutorService pipelineExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ingestion-pipeline");
        t.setDaemon(true);
        return t;
    });

    /**
     * 上传文件并启动异步入库。
     *
     * @return 入库任务 ID
     */
    @Transactional
    public Long upload(MultipartFile file, Long categoryId, String parserType, String chunkMode) {
        String fileName = file.getOriginalFilename();
        String contentType = resolveContentType(file);

        // 未指定解析器类型时自动检测
        if (parserType == null || parserType.isBlank()) {
            DocumentParser parser = parserSelector.select(contentType);
            parserType = parser.getParserType();
        }

        // 默认切片模式
        if (chunkMode == null || chunkMode.isBlank()) {
            chunkMode = "text/markdown".equalsIgnoreCase(contentType) ? "structure_aware" : "fixed_size";
        }

        // 1. 保存文档记录
        KbDocumentDO doc = new KbDocumentDO();
        doc.setCategoryId(categoryId);
        doc.setFileName(fileName);
        doc.setFileType(extractFileExtension(fileName));
        doc.setParserType(parserType);
        doc.setChunkMode(chunkMode);
        doc.setStatus(1); // 处理中
        docMapper.insert(doc);

        // 2. 上传文件到对象存储
        String objectKey = "kb/" + doc.getId() + "/" + fileName;
        try {
            fileStorage.upload(file.getInputStream(), objectKey, file.getSize(), contentType);
            doc.setFilePath(objectKey);
            docMapper.updateById(doc);
        } catch (Exception e) {
            log.error("上传文件到对象存储失败: {}", fileName, e);
            doc.setStatus(3); // 失败
            docMapper.updateById(doc);
            throw new RuntimeException("文件上传失败", e);
        }

        // 3. 创建入库任务
        IngestionTaskDO task = new IngestionTaskDO();
        task.setDocumentId(doc.getId());
        task.setStatus(0); // 待处理
        taskMapper.insert(task);

        // 4. 异步处理
        asyncProcess(doc, task);

        return task.getId();
    }

    /**
     * 重试失败的任务。清除已有切片后从头重新执行。
     */
    public void retryTask(Long taskId) {
        IngestionTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }
        if (task.getStatus() != 5) {
            throw new RuntimeException("仅失败任务可重试（当前状态: " + task.getStatus() + "）");
        }

        KbDocumentDO doc = docMapper.selectById(task.getDocumentId());
        if (doc == null) {
            throw new RuntimeException("任务对应的文档不存在: " + taskId);
        }

        // 清理已有切片
        chunkMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocumentId, doc.getId()));

        // 重置任务状态
        task.setStatus(0);
        task.setTotalChunks(0);
        task.setProcessedChunks(0);
        task.setErrorMsg(null);
        taskMapper.updateById(task);

        // 重新执行管道
        asyncProcess(doc, task);
    }

    private void asyncProcess(KbDocumentDO doc, IngestionTaskDO task) {
        pipelineExecutor.submit(() -> {
            try {
                pipelineSlots.acquire();
                processPipeline(doc, task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                markFailed(task, "管道被中断");
            } catch (Exception e) {
                markFailed(task, e.getMessage());
            } finally {
                pipelineSlots.release();
            }
        });
    }

    private void processPipeline(KbDocumentDO doc, IngestionTaskDO task) {
        // 阶段 1：解析
        updateStatus(task, 1);
        InputStream fileStream = fileStorage.download(doc.getFilePath());
        DocumentParser parser = parserSelector.selectByType(doc.getParserType());
        String text = parser.parse(fileStream, doc.getFileName());

        // 阶段 2：切片
        updateStatus(task, 2);
        ChunkingStrategy chunker = ChunkingStrategyFactory.create(doc.getChunkMode());
        List<String> chunks = chunker.chunk(text);
        task.setTotalChunks(chunks.size());
        taskMapper.updateById(task);

        // 阶段 3：向量化 + 存储
        updateStatus(task, 3);
        for (int i = 0; i < chunks.size(); i++) {
            float[] embedding = embeddingClient.embed(chunks.get(i));
            KnowledgeChunkDO chunkDO = new KnowledgeChunkDO();
            chunkDO.setCategoryId(doc.getCategoryId());
            chunkDO.setDocumentId(doc.getId());
            chunkDO.setChunkIndex(i);
            chunkDO.setContent(chunks.get(i));
            chunkDO.setEmbedding(toFloatList(embedding));
            chunkMapper.insert(chunkDO);

            task.setProcessedChunks(i + 1);
            taskMapper.updateById(task);
        }

        // 阶段 4：完成
        task.setStatus(4);
        taskMapper.updateById(task);
        doc.setStatus(2); // 完成
        doc.setChunkCount(chunks.size());
        docMapper.updateById(doc);
        log.info("入库完成: doc={}, 切片数={}", doc.getId(), chunks.size());
    }

    private void updateStatus(IngestionTaskDO task, int status) {
        task.setStatus(status);
        taskMapper.updateById(task);
    }

    private void markFailed(IngestionTaskDO task, String errorMsg) {
        task.setStatus(5);
        task.setErrorMsg(errorMsg != null && errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg);
        taskMapper.updateById(task);

        KbDocumentDO doc = docMapper.selectById(task.getDocumentId());
        if (doc != null && doc.getStatus() == 1) {
            doc.setStatus(3); // 失败
            docMapper.updateById(doc);
        }
        log.error("入库失败: task={}, 错误={}", task.getId(), errorMsg);
    }

    // ==================== 辅助方法 ====================

    private String resolveContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && !ct.isBlank()) {
            return ct;
        }
        return detectFromExtension(file.getOriginalFilename());
    }

    private String detectFromExtension(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "text/markdown";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }

    private String extractFileExtension(String fileName) {
        if (fileName == null) return "unknown";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "unknown";
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }

    @PreDestroy
    public void shutdown() {
        pipelineExecutor.shutdownNow();
    }
}