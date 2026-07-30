package org.opengoofy.index12306.customerservice.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.chat.ToolDefinition;
import org.opengoofy.index12306.customerservice.fc.ToolExecutionContext;
import org.opengoofy.index12306.customerservice.fc.ToolExecutor;
import org.opengoofy.index12306.ticket.dto.req.TicketPageQueryReqDTO;
import org.opengoofy.index12306.ticket.dto.resp.SeatClassDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketListDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPageQueryRespDTO;
import org.opengoofy.index12306.ticket.service.TicketService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 查车票工具：按出发站、到达站、日期查询车次信息。
 * 结果截断为关键字段，最多返回 5 趟车次。
 */
@Component
@RequiredArgsConstructor
public class QueryTicketTool implements ToolExecutor {

    private static final int MAX_TRAINS = 5;

    private final TicketService ticketService;

    @Override
    public String name() {
        return "queryTicket";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("queryTicket")
                .description("查询火车票车次信息，输入出发站、到达站和出发日期")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "departure", Map.of("type", "string", "description", "出发站名称"),
                                "arrival", Map.of("type", "string", "description", "到达站名称"),
                                "date", Map.of("type", "string", "description", "出发日期，格式 YYYY-MM-DD")
                        ),
                        "required", List.of("departure", "arrival", "date")
                ))
                .build();
    }

    @Override
    public String execute(Map<String, Object> args, ToolExecutionContext ctx) {
        String departure = (String) args.get("departure");
        String arrival = (String) args.get("arrival");
        String dateStr = (String) args.get("date");

        if (departure == null || arrival == null || dateStr == null) {
            return "{\"error\":\"缺少必要参数: departure, arrival, date\"}";
        }

        TicketPageQueryReqDTO req = new TicketPageQueryReqDTO();
        req.setDeparture(departure);
        req.setArrival(arrival);
        req.setDepartureDate(LocalDate.parse(dateStr));
        req.setCurrent(1L);
        req.setSize((long) MAX_TRAINS);

        TicketPageQueryRespDTO resp = ticketService.pageListTicketQuery(req);

        JSONArray trains = new JSONArray();
        List<TicketListDTO> trainList = resp.getTrainList();
        if (trainList != null) {
            int limit = Math.min(trainList.size(), MAX_TRAINS);
            for (int i = 0; i < limit; i++) {
                TicketListDTO train = trainList.get(i);
                JSONObject t = new JSONObject();
                t.put("trainNumber", train.getTrainNumber());
                t.put("departure", train.getDeparture());
                t.put("arrival", train.getArrival());
                t.put("departureTime", train.getDepartureTime());
                JSONArray seats = new JSONArray();
                if (train.getSeatClassList() != null) {
                    for (SeatClassDTO seat : train.getSeatClassList()) {
                        JSONObject s = new JSONObject();
                        s.put("seatType", seat.getSeatType());
                        s.put("price", seat.getPrice());
                        s.put("quantity", seat.getQuantity());
                        seats.add(s);
                    }
                }
                t.put("seatClasses", seats);
                trains.add(t);
            }
        }
        return trains.toJSONString();
    }
}