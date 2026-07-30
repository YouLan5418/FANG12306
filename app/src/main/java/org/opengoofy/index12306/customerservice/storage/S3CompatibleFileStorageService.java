package org.opengoofy.index12306.customerservice.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * 基于 AWS S3 SDK 的对象存储实现。
 * endpoint 指向 RustFS（或 MinIO / 真 S3），切换后端只需改配置不需改代码。
 * 启用 path-style access 以兼容 RustFS/MinIO。
 */
@Slf4j
@Component
public class S3CompatibleFileStorageService implements FileStorageService {

    private final FileStorageProperties properties;
    private final S3Client s3Client;

    public S3CompatibleFileStorageService(FileStorageProperties properties) {
        this.properties = properties;
        this.s3Client = S3Client.builder()
                .endpointOverride(java.net.URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @PostConstruct
    public void init() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(properties.getBucket())
                    .build());
            log.info("S3 bucket 已存在: {}", properties.getBucket());
        } catch (NoSuchBucketException e) {
            log.info("创建 S3 bucket: {}", properties.getBucket());
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(properties.getBucket())
                    .build());
        } catch (Exception e) {
            log.warn("检查/创建 S3 bucket '{}' 失败: {}", properties.getBucket(), e.getMessage());
        }
    }

    @Override
    public String upload(InputStream in, String objectKey, long contentLength, String contentType) {
        try {
            byte[] bytes = in.readAllBytes();
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            log.debug("已上传 {} 字节到 s3://{}/{}", bytes.length, properties.getBucket(), objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("上传文件到对象存储失败: " + objectKey, e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build();
            ResponseInputStream<?> response = s3Client.getObject(request);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("从对象存储下载文件失败: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(request);
            log.debug("已删除 s3://{}/{}", properties.getBucket(), objectKey);
        } catch (Exception e) {
            log.warn("从对象存储删除文件失败: {}", objectKey, e);
        }
    }
}