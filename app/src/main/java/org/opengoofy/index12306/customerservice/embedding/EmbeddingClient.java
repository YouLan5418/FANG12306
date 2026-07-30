package org.opengoofy.index12306.customerservice.embedding;

import java.util.List;

/**
 * 文本向量化客户端抽象接口。
 * 维度由配置的模型决定，不硬编码。
 */
public interface EmbeddingClient {

    /**
     * 将单条文本嵌入为稠密向量。
     *
     * @param text 输入文本
     * @return float 数组，长度等于模型输出维度
     */
    float[] embed(String text);

    /**
     * 批量嵌入多条文本。
     * 实现可按 batchSize 拆分为多次 API 调用。
     *
     * @param texts 输入文本列表
     * @return 向量列表，顺序与输入一致
     */
    List<float[]> embedBatch(List<String> texts);
}