package org.opengoofy.index12306.customerservice.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储配置，映射 application.yml 中 file-storage 前缀。
 * endpoint 指向 RustFS（或任意 S3 兼容服务）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file-storage")
public class FileStorageProperties {

    private String endpoint = "http://localhost:9000";

    private String accessKey = "index12306";

    private String secretKey = "index12306";

    private String bucket = "index12306-kb";

    private String region = "us-east-1";
}