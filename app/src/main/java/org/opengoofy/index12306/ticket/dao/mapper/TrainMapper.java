package org.opengoofy.index12306.ticket.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.opengoofy.index12306.ticket.dao.entity.TrainDO;

@Mapper
public interface TrainMapper extends BaseMapper<TrainDO> {
}