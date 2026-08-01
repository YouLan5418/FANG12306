package org.opengoofy.index12306.customerservice.chunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

    private static final Pattern HEADING_LINE = Pattern.compile("^#{1,6}\\s+.*");

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

        List<MarkdownSection> sections = parseSections(text);
        if (sections.isEmpty()) {
            return new FixedSizeTextChunker(targetSize, overlap).chunk(text);
        }

        List<String> chunks = packSections(sections);
        if (overlap > 0 && chunks.size() > 1) {
            chunks = applyOverlap(chunks);
        }
        return chunks;
    }

    private List<MarkdownSection> parseSections(String text) {
        String[] lines = text.split("\\R");
        String[] headingStack = new String[6];
        List<MarkdownSection> sections = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        String currentPath = "";
        boolean hasHeading = false;

        for (String line : lines) {
            if (HEADING_LINE.matcher(line).matches()) {
                hasHeading = true;
                flushSection(sections, currentPath, content);
                int level = headingLevel(line);
                String title = line.substring(level).trim();
                headingStack[level - 1] = title;
                for (int i = level; i < headingStack.length; i++) {
                    headingStack[i] = null;
                }
                currentPath = headingPath(headingStack);
                appendLine(content, line);
            } else {
                appendLine(content, line);
            }
        }
        flushSection(sections, currentPath, content);
        if (!hasHeading) {
            return List.of();
        }
        return sections.stream()
                .filter(section -> !section.content().isBlank())
                .toList();
    }

    private void flushSection(List<MarkdownSection> sections, String headingPath, StringBuilder content) {
        if (content.length() == 0 || content.toString().isBlank()) {
            content.setLength(0);
            return;
        }
        sections.add(new MarkdownSection(headingPath, withContextPrefix(headingPath, content.toString().trim())));
        content.setLength(0);
    }

    private int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        return Math.min(level, 6);
    }

    private String headingPath(String[] headingStack) {
        return Arrays.stream(headingStack)
                .filter(Objects::nonNull)
                .toList()
                .stream()
                .reduce((left, right) -> left + " > " + right)
                .orElse("");
    }

    private String withContextPrefix(String headingPath, String content) {
        if (headingPath == null || headingPath.isBlank()) {
            return content;
        }
        return "上下文：" + headingPath + "\n\n" + content;
    }

    private void appendLine(StringBuilder content, String line) {
        if (content.length() > 0) {
            content.append('\n');
        }
        content.append(line);
    }

    private List<String> packSections(List<MarkdownSection> sections) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (MarkdownSection section : sections) {
            String sectionContent = section.content();
            // 超长段落：先刷新缓冲区，再用 FixedSize 子切分
            if (sectionContent.length() > maxSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                chunks.addAll(subSplitWithContext(section));
                continue;
            }

            // 加入后会超过 max：先刷新当前块
            if (current.length() > 0 && current.length() + sectionContent.length() + 2 > maxSize) {
                chunks.add(current.toString());
                current.setLength(0);
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(sectionContent);

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

    private List<String> subSplitWithContext(MarkdownSection section) {
        List<String> split = new FixedSizeTextChunker(targetSize, overlap).chunk(section.content());
        if (section.headingPath() == null || section.headingPath().isBlank()) {
            return split;
        }
        String prefix = "上下文：" + section.headingPath();
        return split.stream()
                .map(chunk -> chunk.startsWith(prefix) ? chunk : prefix + "\n\n" + chunk)
                .toList();
    }

    private List<String> applyOverlap(List<String> chunks) {
        List<String> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String tail = prev.length() > overlap
                    ? prev.substring(prev.length() - overlap)
                    : prev;
            result.add(tail + "\n\n" + chunks.get(i));
        }
        return result;
    }

    @Override
    public String getMode() {
        return "structure_aware";
    }

    private record MarkdownSection(String headingPath, String content) {
    }
}
