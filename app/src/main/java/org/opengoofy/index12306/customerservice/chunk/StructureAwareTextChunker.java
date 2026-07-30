package org.opengoofy.index12306.customerservice.chunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 结构感知切片器：按 Markdown 标题（#{1,6}）切分，
 * 再按 min/target/max 预算打包为块。
 * 超长段落自动用 FixedSizeTextChunker 子切分；
 * 过短段落自动合并到相邻块；开启 overlap 时复制上一块尾部。
 *
 * 默认参数：min=200, target=500, max=1000, overlap=128。
 */
public class StructureAwareTextChunker implements ChunkingStrategy {

    private static final Pattern HEADING_SPLIT = Pattern.compile("(?m)(?=^#{1,6}\\s+)");

    private final int minSize;
    private final int targetSize;
    private final int maxSize;
    private final int overlap;

    public StructureAwareTextChunker() {
        this(200, 500, 1000, 128);
    }

    public StructureAwareTextChunker(int minSize, int targetSize, int maxSize, int overlap) {
        this.minSize = minSize;
        this.targetSize = targetSize;
        this.maxSize = maxSize;
        this.overlap = Math.min(overlap, targetSize / 2);
    }

    @Override
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] sections = HEADING_SPLIT.split(text);
        List<String> valid = Arrays.stream(sections)
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .toList();

        if (valid.isEmpty()) {
            return List.of();
        }

        // 没有标题 -> 回退到固定长度切片
        boolean hasHeading = valid.stream().anyMatch(s -> s.matches("(?m)^#{1,6}\\s+.*"));
        if (!hasHeading) {
            return new FixedSizeTextChunker(targetSize, overlap).chunk(text);
        }

        List<String> chunks = packSections(valid);
        if (overlap > 0 && chunks.size() > 1) {
            chunks = applyOverlap(chunks);
        }
        return chunks;
    }

    private List<String> packSections(List<String> sections) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String section : sections) {
            // 超长段落：先刷新缓冲区，再用 FixedSize 子切分
            if (section.length() > maxSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                chunks.addAll(new FixedSizeTextChunker(targetSize, overlap).chunk(section));
                continue;
            }

            // 加入后会超过 max：先刷新当前块
            if (current.length() > 0 && current.length() + section.length() + 2 > maxSize) {
                chunks.add(current.toString());
                current.setLength(0);
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(section);

            // 达到 target：刷新
            if (current.length() >= targetSize) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }

        // 刷新剩余内容
        if (current.length() > 0) {
            if (current.length() < minSize && !chunks.isEmpty()) {
                // 过短：尝试合并到上一个块
                String last = chunks.get(chunks.size() - 1);
                if (last.length() + current.length() + 2 <= maxSize) {
                    chunks.set(chunks.size() - 1, last + "\n\n" + current);
                } else {
                    chunks.add(current.toString());
                }
            } else {
                chunks.add(current.toString());
            }
        }

        return chunks;
    }

    private List<String> applyOverlap(List<String> chunks) {
        List<String> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String tail = prev.length() > overlap
                    ? prev.substring(prev.length() - overlap)
                    : prev;
            result.add(tail + chunks.get(i));
        }
        return result;
    }

    @Override
    public String getMode() {
        return "structure_aware";
    }
}