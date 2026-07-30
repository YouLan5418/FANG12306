package org.opengoofy.index12306.customerservice.storage;

import java.io.InputStream;

/**
 * S3 兼容对象存储抽象接口。
 * 入库管道通过此接口存储上传的原始文件。
 */
public interface FileStorageService {

    /**
     * 上传文件到对象存储。
     *
     * @param in            文件内容输入流
     * @param objectKey     存储键（如 "kb/{documentId}/{fileName}"）
     * @param contentLength 文件大小（字节，-1 表示未知）
     * @param contentType   MIME 类型
     * @return 实际使用的存储键
     */
    String upload(InputStream in, String objectKey, long contentLength, String contentType);

    /**
     * 从对象存储下载文件。
     *
     * @param objectKey 存储键
     * @return 文件内容输入流
     */
    InputStream download(String objectKey);

    /**
     * 从对象存储删除文件。
     *
     * @param objectKey 存储键
     */
    void delete(String objectKey);
}