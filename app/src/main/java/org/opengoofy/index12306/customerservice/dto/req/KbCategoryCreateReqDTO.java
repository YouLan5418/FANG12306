package org.opengoofy.index12306.customerservice.dto.req;

import lombok.Data;

/**
 * 新增知识库分类请求。
 */
@Data
public class KbCategoryCreateReqDTO {

    private String name;

    private String description;
}