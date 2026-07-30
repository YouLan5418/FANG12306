package org.opengoofy.index12306.customerservice.chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定长度切片器：按 targetSize 字符数切分，支持 overlap 重叠。
 * 用于纯文本兜底或无标题结构的文档。
 *
 * 默认参数：targetSize=500, overlap=128。
 */
public class FixedSizeTextChunker implements ChunkingStrategy {

    private final int targetSize;
    private final int overlap;

    public FixedSizeTextChunker() {
        this(500, 128);
    }

    public FixedSizeTextChunker(int targetSize, int overlap) {
        this.targetSize = targetSize;
        this.overlap = Math.min(overlap, targetSize / 2);
    }

    @Override
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int len = text.length();
        if (len <= targetSize) {
            chunks.add(text);
            return chunks;
        }
        int step = targetSize - overlap;
        int start = 0;
        while (start < len) {
            int end = Math.min(start + targetSize, len);
            chunks.add(text.substring(start, end));
            if (end >= len) {
                break;
            }
            start += step;
        }
        return chunks;
    }

    @Override
    public String getMode() {
        return "fixed_size";
    }

    public int getTargetSize() {
        return targetSize;
    }

    public int getOverlap() {
        return overlap;
    }
}