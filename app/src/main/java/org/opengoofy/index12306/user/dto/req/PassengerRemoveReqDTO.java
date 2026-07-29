package org.opengoofy.index12306.user.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 乘车人删除请求。
 */
@Data
public class PassengerRemoveReqDTO {

    private List<String> ids;
}