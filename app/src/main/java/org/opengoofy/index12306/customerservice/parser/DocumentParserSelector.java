package org.opengoofy.index12306.customerservice.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 根据 MIME 类型选择合适的文档解析器。
 * 按注册顺序遍历（Markdown、Tika），首个匹配者胜出。
 * OcrParser 不自动注册，管道在需要时手动使用。
 */
@Component
@RequiredArgsConstructor
public class DocumentParserSelector {

    private final List<DocumentParser> parsers;

    /**
     * 按 MIME 类型查找解析器。
     *
     * @param mimeType 文件上传时的 MIME 类型
     * @return 首个匹配的解析器
     * @throws RuntimeException 没有解析器支持该类型时抛出
     */
    public DocumentParser select(String mimeType) {
        return parsers.stream()
                .filter(p -> p.supports(mimeType))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到支持该 MIME 类型的解析器: " + mimeType));
    }

    /**
     * 按类型标识查找解析器（MARKDOWN / TIKA / OCR）。
     * 注意：OCR 解析器未自动注册，如需使用请 new OcrParser()。
     */
    public DocumentParser selectByType(String parserType) {
        return parsers.stream()
                .filter(p -> p.getParserType().equals(parserType))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到指定类型的解析器: " + parserType));
    }

    /**
     * 检查是否有解析器支持指定 MIME 类型。
     */
    public boolean isSupported(String mimeType) {
        return parsers.stream().anyMatch(p -> p.supports(mimeType));
    }
}