package org.opengoofy.index12306.framework.convention.page;

import lombok.Data;

@Data
public class PageRequest {

    private Long current = 1L;
    private Long size = 10L;
}
