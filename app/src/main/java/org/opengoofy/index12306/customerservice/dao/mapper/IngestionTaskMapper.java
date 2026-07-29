package org.opengoofy.index12306.customerservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.opengoofy.index12306.customerservice.dao.entity.IngestionTaskDO;

@Mapper
public interface IngestionTaskMapper extends BaseMapper<IngestionTaskDO> {
}