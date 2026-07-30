package org.opengoofy.index12306.customerservice.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.chat.ToolDefinition;
import org.opengoofy.index12306.customerservice.fc.ToolExecutionContext;
import org.opengoofy.index12306.customerservice.fc.ToolExecutor;
import org.opengoofy.index12306.ticket.dao.entity.StationDO;
import org.opengoofy.index12306.ticket.dao.mapper.StationMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查车站工具：按名称或拼音模糊搜索车站信息。
 * 公开数据，无需归属校验。最多返回 5 条。
 */
@Component
@RequiredArgsConstructor
public class QueryStationTool implements ToolExecutor {

    private static final int MAX_STATIONS = 5;

    private final StationMapper stationMapper;

    @Override
    public String name() {
        return "queryStation";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("queryStation")
                .description("查询车站信息，支持按名称或拼音模糊搜索")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "keyword", Map.of("type", "string", "description", "车站名称或拼音关键词")
                        ),
                        "required", List.of("keyword")
                ))
                .build();
    }

    @Override
    public String execute(Map<String, Object> args, ToolExecutionContext ctx) {
        String keyword = (String) args.get("keyword");
        if (keyword == null || keyword.isBlank()) {
            return "{\"error\":\"缺少必要参数: keyword\"}";
        }

        List<StationDO> stations = stationMapper.selectList(
                new LambdaQueryWrapper<StationDO>()
                        .like(StationDO::getName, keyword)
                        .or()
                        .like(StationDO::getSpell, keyword)
                        .last("LIMIT " + MAX_STATIONS));

        JSONArray result = new JSONArray();
        for (StationDO station : stations) {
            JSONObject s = new JSONObject();
            s.put("name", station.getName());
            s.put("spell", station.getSpell());
            s.put("region", station.getRegion());
            result.add(s);
        }
        return result.toJSONString();
    }
}