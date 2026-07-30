package org.opengoofy.index12306.customerservice.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 入库任务响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionTaskRespDTO {

    private Long id;
    private Long documentId;
    private Integer status;
    private Integer totalChunks;
    private Integer processedChunks;
    private String errorMsg;
    private Date createTime;
    private Date updateTime;
}