package org.opengoofy.index12306.operations.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运营兜底任务执行结果。
 */
@Data
@Builder
public class OperationTaskRespDTO {

    private String taskName;
    private Integer scannedCount;
    private Integer handledCount;
    private Map<String, Object> details;

    public static OperationTaskRespDTO of(String taskName, int scannedCount, int handledCount) {
        return OperationTaskRespDTO.builder()
                .taskName(taskName)
                .scannedCount(scannedCount)
                .handledCount(handledCount)
                .details(new LinkedHashMap<>())
                .build();
    }
}
