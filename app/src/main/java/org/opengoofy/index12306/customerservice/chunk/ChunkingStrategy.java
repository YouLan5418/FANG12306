package org.opengoofy.index12306.customerservice.chunk;

import java.util.List;

/**
 * 文本切片策略接口，将文档拆分为适合 embedding 的小块。
 */
public interface ChunkingStrategy {

    /**
     * 将文本切分为适合向量化的块。
     *
     * @param text 完整文档文本
     * @return 切片列表
     */
    List<String> chunk(String text);

    /**
     * 策略模式标识，存入 kb_document.chunk_mode。
     */
    String getMode();
}