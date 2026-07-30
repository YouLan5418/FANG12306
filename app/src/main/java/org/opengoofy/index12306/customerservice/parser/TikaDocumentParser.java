package org.opengoofy.index12306.customerservice.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;

/**
 * 基于 Apache Tika 的文档解析器，支持 PDF、Word、Excel。
 * 使用 Tika 2.9.2 AutoDetectParser 提取文本层。
 * 扫描件 PDF（纯图片）会提取出空文本，管道应回退到 OcrParser。
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    /** 文本最大长度：-1 表示不限 */
    private static final int MAX_TEXT_SIZE = -1;

    @Override
    public String getParserType() {
        return "TIKA";
    }

    @Override
    public boolean supports(String mimeType) {
        return mimeType != null && SUPPORTED_TYPES.contains(mimeType.toLowerCase());
    }

    @Override
    public String parse(InputStream in, String fileName) {
        try {
            BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_SIZE);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(in, handler, metadata, context);
            String text = handler.toString();
            if (text == null || text.isBlank()) {
                log.warn("Tika 从 {} 未提取到文本，可能是扫描件", fileName);
            }
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            throw new RuntimeException("Tika 解析文件失败: " + fileName, e);
        }
    }
}