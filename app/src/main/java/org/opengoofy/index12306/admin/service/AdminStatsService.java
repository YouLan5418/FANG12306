package org.opengoofy.index12306.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.order.dao.entity.OrderDO;
import org.opengoofy.index12306.order.dao.mapper.OrderMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainMapper;
import org.opengoofy.index12306.user.dao.entity.UserDO;
import org.opengoofy.index12306.user.dao.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 管理端统计服务。
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final TrainMapper trainMapper;

    public Map<String, Object> dashboard() {
        long userCount = userMapper.selectCount(
                Wrappers.lambdaQuery(UserDO.class).eq(UserDO::getDeletionTime, 0L));
        long orderCount = orderMapper.selectCount(null);
        long trainCount = trainMapper.selectCount(null);
        LocalDate today = LocalDate.now();
        Date todayStart = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<OrderDO> todayOrders = orderMapper.selectList(
                Wrappers.lambdaQuery(OrderDO.class).ge(OrderDO::getOrderTime, todayStart));
        long todayOrderCount = todayOrders.size();
        long todayRevenue = todayOrders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() != 0 && o.getStatus() != 30)
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userCount", userCount);
        result.put("orderCount", orderCount);
        result.put("trainCount", trainCount);
        result.put("todayOrderCount", todayOrderCount);
        result.put("todayRevenue", todayRevenue);
        return result;
    }

    public Map<String, Object> orderTrend(int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            labels.add(date.toString());
            Date dayStart = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dayEnd = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            long count = orderMapper.selectCount(
                    Wrappers.lambdaQuery(OrderDO.class)
                            .ge(OrderDO::getOrderTime, dayStart)
                            .lt(OrderDO::getOrderTime, dayEnd));
            values.add(count);
        }
        result.put("labels", labels);
        result.put("values", values);
        return result;
    }

    public Map<String, Object> revenueTrend(int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            labels.add(date.toString());
            Date dayStart = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dayEnd = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            List<OrderDO> dayOrders = orderMapper.selectList(
                    Wrappers.lambdaQuery(OrderDO.class)
                            .ge(OrderDO::getOrderTime, dayStart)
                            .lt(OrderDO::getOrderTime, dayEnd));
            long paidCount = dayOrders.stream()
                    .filter(o -> o.getStatus() != null && o.getStatus() != 0 && o.getStatus() != 30)
                    .count();
            values.add(paidCount);
        }
        result.put("labels", labels);
        result.put("values", values);
        return result;
    }
}
