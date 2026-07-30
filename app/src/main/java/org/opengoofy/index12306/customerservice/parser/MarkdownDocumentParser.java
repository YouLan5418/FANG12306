package org.opengoofy.index12306.customerservice.parser;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Markdown 解析器：直接按 UTF-8 读取文件全文。
 * Markdown 本身就是纯文本，结构化拆分交给切片器处理。
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public String getParserType() {
        return "MARKDOWN";
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return "text/markdown".equalsIgnoreCase(mimeType)
                || "text/x-markdown".equalsIgnoreCase(mimeType);
    }

    @Override
    public String parse(InputStream in, String fileName) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解析 Markdown 文件失败: " + fileName, e);
        }
    }
}