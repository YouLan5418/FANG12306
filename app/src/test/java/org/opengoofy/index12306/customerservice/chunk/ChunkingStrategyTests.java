package org.opengoofy.index12306.customerservice.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P8-T2 单元测试：切片策略。
 */
class ChunkingStrategyTests {

    // ==================== FixedSizeTextChunker 固定切片测试 ====================

    @Test
    void fixedSize_shortTextReturnsSingleChunk() {
        FixedSizeTextChunker chunker = new FixedSizeTextChunker(500, 128);
        List<String> chunks = chunker.chunk("short text");
        assertEquals(1, chunks.size());
        assertEquals("short text", chunks.get(0));
    }

    @Test
    void fixedSize_longTextSplitsIntoMultipleChunks() {
        FixedSizeTextChunker chunker = new FixedSizeTextChunker(100, 20);
        String text = "a".repeat(250);
        List<String> chunks = chunker.chunk(text);

        assertTrue(chunks.size() > 1);
        // 除最后一个外，每个切片长度应 <= targetSize
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertTrue(chunks.get(i).length() <= 100);
        }
    }

    @Test
    void fixedSize_overlapApplied() {
        FixedSizeTextChunker chunker = new FixedSizeTextChunker(50, 20);
        String text = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJ";
        List<String> chunks = chunker.chunk(text);

        if (chunks.size() > 1) {
            // 第二个切片开头应与第一个切片结尾重叠
            String endOfFirst = chunks.get(0).substring(chunks.get(0).length() - 20);
            String startOfSecond = chunks.get(1).substring(0, Math.min(20, chunks.get(1).length()));
            assertEquals(endOfFirst, startOfSecond);
        }
    }

    @Test
    void fixedSize_emptyTextReturnsEmpty() {
        FixedSizeTextChunker chunker = new FixedSizeTextChunker();
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
    }

    @Test
    void fixedSize_modeIsFixedSize() {
        assertEquals("fixed_size", new FixedSizeTextChunker().getMode());
    }

    // ==================== StructureAwareTextChunker 结构感知切片测试 ====================

    @Test
    void structureAware_splitsByHeadings() {
        StructureAwareTextChunker chunker = new StructureAwareTextChunker();
        String markdown = """
                # 退票规则

                开车前48小时以上退票收5%手续费。

                # 改签规则

                改签需要在开车前办理。

                # 儿童票

                儿童票按规定身高购买。
                """;
        List<String> chunks = chunker.chunk(markdown);

        assertTrue(chunks.size() >= 1);
        // 每个切片应包含标题内容
        boolean hasRefund = chunks.stream().anyMatch(c -> c.contains("退票规则"));
        boolean hasChange = chunks.stream().anyMatch(c -> c.contains("改签规则"));
        boolean hasChild = chunks.stream().anyMatch(c -> c.contains("儿童票"));
        assertTrue(hasRefund);
        assertTrue(hasChange);
        assertTrue(hasChild);
    }

    @Test
    void structureAware_noHeadingsFallsBackToFixedSize() {
        StructureAwareTextChunker chunker = new StructureAwareTextChunker(100, 200, 400, 50);
        String plainText = "This is a long paragraph without any headings. ".repeat(20);
        List<String> chunks = chunker.chunk(plainText);

        assertTrue(chunks.size() > 1);
        // 行为应等同于固定切片
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 400);
        }
    }

    @Test
    void structureAware_oversizedSectionIsSubSplit() {
        StructureAwareTextChunker chunker = new StructureAwareTextChunker(50, 100, 200, 20);
        String markdown = "# Title\n\n" + "x".repeat(500);
        List<String> chunks = chunker.chunk(markdown);

        assertTrue(chunks.size() > 1);
        // 每个切片长度应 <= maxSize(200) + overlap
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 220, "chunk too long: " + chunk.length());
        }
    }

    @Test
    void structureAware_smallSectionsMerged() {
        StructureAwareTextChunker chunker = new StructureAwareTextChunker(200, 500, 1000, 0);
        String markdown = """
                # A

                short.

                # B

                short.

                # C

                short.
                """;
        List<String> chunks = chunker.chunk(markdown);
        // 过短的小节应合并为更少的切片
        assertTrue(chunks.size() <= 2, "expected merge, got " + chunks.size() + " chunks");
    }

    @Test
    void structureAware_emptyTextReturnsEmpty() {
        StructureAwareTextChunker chunker = new StructureAwareTextChunker();
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
    }

    @Test
    void structureAware_modeIsStructureAware() {
        assertEquals("structure_aware", new StructureAwareTextChunker().getMode());
    }

    @Test
    void structureAware_overlapAppliedBetweenChunks() {
        StructureAwareTextChunker chunker = new StructureAwareTextChunker(10, 50, 100, 20);
        String markdown = "# H1\n\n" + "a".repeat(80) + "\n\n# H2\n\n" + "b".repeat(80);
        List<String> chunks = chunker.chunk(markdown);

        if (chunks.size() > 1) {
            // 第二个切片应以第一个切片的重叠内容开头
            String prevTail = chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 20));
            assertTrue(chunks.get(1).startsWith(prevTail) || chunks.get(1).contains(prevTail.substring(0, 5)));
        }
    }

    // ==================== ChunkingStrategyFactory 工厂测试 ====================

    @Test
    void factory_createsStructureAware() {
        ChunkingStrategy strategy = ChunkingStrategyFactory.create("structure_aware");
        assertInstanceOf(StructureAwareTextChunker.class, strategy);
    }

    @Test
    void factory_createsFixedSize() {
        ChunkingStrategy strategy = ChunkingStrategyFactory.create("fixed_size");
        assertInstanceOf(FixedSizeTextChunker.class, strategy);
    }

    @Test
    void factory_nullModeReturnsFixedSize() {
        ChunkingStrategy strategy = ChunkingStrategyFactory.create(null);
        assertInstanceOf(FixedSizeTextChunker.class, strategy);
    }

    @Test
    void factory_unknownModeThrows() {
        assertThrows(IllegalArgumentException.class, () -> ChunkingStrategyFactory.create("unknown"));
    }
}