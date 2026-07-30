package org.opengoofy.index12306.customerservice.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档上传响应，返回文档 ID 与入库任务 ID。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadRespDTO {

    private Long documentId;
    private Long taskId;
}