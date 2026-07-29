package org.opengoofy.index12306.order.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.opengoofy.index12306.order.dao.entity.OrderDO;

@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {
}