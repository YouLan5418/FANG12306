package org.opengoofy.index12306.framework.distributedid.core.snowflake;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnowflakeIdInfo {
    private long sequence;
    private long workerId;
    private long dataCenterId;
    private long timestamp;
    private long gene;
}
