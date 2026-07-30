package org.opengoofy.index12306.customerservice.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P8-T1 单元测试：文档解析器和选择器。
 */
class DocumentParserTests {

    @Test
    void markdownParser_parsesPlainText() {
        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        String markdown = "# 退票规则\n\n开车前48小时以上退票收5%手续费。";
        String result = parser.parse(
                new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)),
                "refund.md"
        );
        assertEquals(markdown, result);
    }

    @Test
    void markdownParser_supportsCorrectMimeTypes() {
        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        assertTrue(parser.supports("text/markdown"));
        assertTrue(parser.supports("text/x-markdown"));
        assertFalse(parser.supports("application/pdf"));
        assertFalse(parser.supports(null));
    }

    @Test
    void markdownParser_parserTypeIsMarkdown() {
        assertEquals("MARKDOWN", new MarkdownDocumentParser().getParserType());
    }

    @Test
    void tikaParser_supportsDocumentTypes() {
        TikaDocumentParser parser = new TikaDocumentParser();
        assertTrue(parser.supports("application/pdf"));
        assertTrue(parser.supports("application/msword"));
        assertTrue(parser.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertTrue(parser.supports("application/vnd.ms-excel"));
        assertTrue(parser.supports("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertFalse(parser.supports("text/markdown"));
        assertFalse(parser.supports(null));
    }

    @Test
    void tikaParser_parserTypeIsTika() {
        assertEquals("TIKA", new TikaDocumentParser().getParserType());
    }

    @Test
    void ocrParser_returnsNotImplementedMarker() {
        OcrParser parser = new OcrParser();
        String result = parser.parse(
                new ByteArrayInputStream(new byte[0]),
                "scanned.pdf"
        );
        assertTrue(result.contains("OCR_NOT_IMPLEMENTED"));
        assertTrue(result.contains("scanned.pdf"));
    }

    @Test
    void ocrParser_supportsPdf() {
        OcrParser parser = new OcrParser();
        assertTrue(parser.supports("application/pdf"));
        assertFalse(parser.supports("text/markdown"));
    }

    @Test
    void selector_findsMarkdownParser() {
        DocumentParserSelector selector = new DocumentParserSelector(
                List.of(new MarkdownDocumentParser(), new TikaDocumentParser())
        );
        DocumentParser parser = selector.select("text/markdown");
        assertInstanceOf(MarkdownDocumentParser.class, parser);
    }

    @Test
    void selector_findsTikaParserForPdf() {
        DocumentParserSelector selector = new DocumentParserSelector(
                List.of(new MarkdownDocumentParser(), new TikaDocumentParser())
        );
        DocumentParser parser = selector.select("application/pdf");
        assertInstanceOf(TikaDocumentParser.class, parser);
    }

    @Test
    void selector_throwsForUnsupportedType() {
        DocumentParserSelector selector = new DocumentParserSelector(
                List.of(new MarkdownDocumentParser(), new TikaDocumentParser())
        );
        assertThrows(RuntimeException.class, () -> selector.select("application/zip"));
    }

    @Test
    void selector_isSupportedChecksCorrectly() {
        DocumentParserSelector selector = new DocumentParserSelector(
                List.of(new MarkdownDocumentParser(), new TikaDocumentParser())
        );
        assertTrue(selector.isSupported("text/markdown"));
        assertTrue(selector.isSupported("application/pdf"));
        assertFalse(selector.isSupported("application/zip"));
    }
}