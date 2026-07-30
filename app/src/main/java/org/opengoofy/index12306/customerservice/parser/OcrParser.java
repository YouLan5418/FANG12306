package org.opengoofy.index12306.customerservice.parser;

import java.io.InputStream;

/**
 * OCR 解析器占位实现，用于扫描件 PDF。
 * 返回未实现标记，二期接入 PaddleOCR。
 * 不注册为 Spring 组件；管道在 Tika 提取不到文本时手动实例化。
 */
public class OcrParser implements DocumentParser {

    @Override
    public String getParserType() {
        return "OCR";
    }

    @Override
    public boolean supports(String mimeType) {
        return "application/pdf".equalsIgnoreCase(mimeType);
    }

    @Override
    public String parse(InputStream in, String fileName) {
        return "[OCR_NOT_IMPLEMENTED] " + fileName
                + ": 检测到扫描件，OCR 支持计划在二期接入 PaddleOCR";
    }
}