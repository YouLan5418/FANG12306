package org.opengoofy.index12306.ticket.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 车票查询响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPageQueryRespDTO {

    private List<TicketListDTO> trainList;
    private Long current;
    private Long size;
    private Long total;
}