package org.opengoofy.index12306.customerservice.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库分类响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbCategoryRespDTO {

    private Long id;
    private String name;
    private String description;
    private Long documentCount;
}