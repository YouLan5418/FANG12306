package org.opengoofy.index12306.customerservice.parser;

import java.io.InputStream;

/**
 * 文档解析器抽象接口，从上传文件中提取纯文本。
 * 每个实现负责特定文件类型（Markdown、PDF/Word via Tika、OCR）。
 */
public interface DocumentParser {

    /**
     * 解析器类型标识，存入 kb_document.parser_type。
     * @return 解析器类型：MARKDOWN / TIKA / OCR
     */
    String getParserType();

    /**
     * 判断是否支持指定 MIME 类型。
     * @param mimeType 文件上传时的 MIME 类型（如 "text/markdown"、"application/pdf"）
     * @return 支持返回 true
     */
    boolean supports(String mimeType);

    /**
     * 从输入流中提取纯文本内容。
     * @param in 文件内容输入流
     * @param fileName 原始文件名（用于日志/元数据）
     * @return 提取的纯文本
     */
    String parse(InputStream in, String fileName);
}