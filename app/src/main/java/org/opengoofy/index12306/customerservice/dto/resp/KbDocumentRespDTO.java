package org.opengoofy.index12306.customerservice.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文档列表响应。
 * status：0待处理 1处理中 2完成 3失败
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbDocumentRespDTO {

    private Long id;
    private Long categoryId;
    private String fileName;
    private String fileType;
    private String parserType;
    private String chunkMode;
    private Integer status;
    private Integer chunkCount;
}