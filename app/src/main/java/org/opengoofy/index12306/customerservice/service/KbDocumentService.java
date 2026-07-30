package org.opengoofy.index12306.customerservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.dao.entity.KbDocumentDO;
import org.opengoofy.index12306.customerservice.dao.entity.KnowledgeChunkDO;
import org.opengoofy.index12306.customerservice.dao.mapper.KbDocumentMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.KnowledgeChunkMapper;
import org.opengoofy.index12306.customerservice.dto.resp.KbDocumentRespDTO;
import org.opengoofy.index12306.customerservice.storage.FileStorageService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库文档管理服务：文档列表查询与删除（含切片、对象存储文件清理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocumentService {

    private final KbDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final FileStorageService fileStorage;

    /**
     * 查询文档列表。categoryId 为空时返回全部。
     */
    public List<KbDocumentRespDTO> listDocuments(Long categoryId) {
        LambdaQueryWrapper<KbDocumentDO> wrapper = new LambdaQueryWrapper<KbDocumentDO>()
                .orderByDesc(KbDocumentDO::getId);
        if (categoryId != null) {
            wrapper.eq(KbDocumentDO::getCategoryId, categoryId);
        }
        List<KbDocumentDO> docs = docMapper.selectList(wrapper);
        return docs.stream()
                .map(d -> KbDocumentRespDTO.builder()
                        .id(d.getId())
                        .categoryId(d.getCategoryId())
                        .fileName(d.getFileName())
                        .fileType(d.getFileType())
                        .parserType(d.getParserType())
                        .chunkMode(d.getChunkMode())
                        .status(d.getStatus())
                        .chunkCount(d.getChunkCount())
                        .build())
                .toList();
    }

    /**
     * 删除文档：先删关联切片与对象存储文件，再删文档记录。
     * 切片与文档均为物理删除，避免逻辑删除失效导致的残留。
     */
    public void deleteDocument(Long id) {
        KbDocumentDO doc = docMapper.selectById(id);
        if (doc == null) {
            return;
        }
        // 删除关联切片（含向量）
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkDO>()
                .eq(KnowledgeChunkDO::getDocumentId, id));
        // 删除对象存储中的原始文件
        if (doc.getFilePath() != null && !doc.getFilePath().isBlank()) {
            fileStorage.delete(doc.getFilePath());
        }
        // 删除文档记录
        docMapper.deleteById(id);
        log.info("已删除知识库文档: id={}, fileName={}", id, doc.getFileName());
    }
}