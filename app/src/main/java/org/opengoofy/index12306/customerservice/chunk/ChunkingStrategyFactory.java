package org.opengoofy.index12306.customerservice.chunk;

/**
 * 切片策略工厂，按模式名创建切片器。
 * 模式：structure_aware（按标题）/ fixed_size（按字符）。
 */
public class ChunkingStrategyFactory {

    public static ChunkingStrategy create(String mode) {
        if (mode == null) {
            return new FixedSizeTextChunker();
        }
        return switch (mode) {
            case "structure_aware" -> new StructureAwareTextChunker();
            case "fixed_size" -> new FixedSizeTextChunker();
            default -> throw new IllegalArgumentException("未知的切片模式: " + mode);
        };
    }
}