package org.opengoofy.index12306.customerservice.ingestion;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.storage.FileStorageProperties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P8-T3 单元测试：文件存储配置和管道配置。
 * 完整管道集成测试需要 PostgreSQL + RustFS + Embedding API，
 * 由 P8-T4 控制器测试覆盖。
 */
class IngestionPipelineTests {

    @Test
    void fileStorageProperties_defaultsAreSane() {
        FileStorageProperties props = new FileStorageProperties();
        assertEquals("http://localhost:9000", props.getEndpoint());
        assertEquals("index12306", props.getAccessKey());
        assertEquals("index12306", props.getSecretKey());
        assertEquals("index12306-kb", props.getBucket());
        assertEquals("us-east-1", props.getRegion());
    }
}