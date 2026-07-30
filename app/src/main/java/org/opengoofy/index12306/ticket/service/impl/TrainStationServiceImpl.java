package org.opengoofy.index12306.ticket.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationRelationDO;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationRelationMapper;
import org.opengoofy.index12306.ticket.service.TrainStationService;
import org.springframework.stereotype.Service;

/**
 * 列车车站关系服务实现。
 */
@Service
@RequiredArgsConstructor
public class TrainStationServiceImpl implements TrainStationService {

    private final TrainStationRelationMapper trainStationRelationMapper;

    @Override
    public TrainStationRelationDO getRelation(Long trainId, String departure, String arrival) {
        TrainStationRelationDO relation = trainStationRelationMapper.selectOne(Wrappers.lambdaQuery(TrainStationRelationDO.class)
                .eq(TrainStationRelationDO::getTrainId, trainId)
                .eq(TrainStationRelationDO::getDeparture, departure)
                .eq(TrainStationRelationDO::getArrival, arrival));
        if (relation == null) {
            throw new ClientException("未找到列车区间，请检查出发/到达站是否正确");
        }
        return relation;
    }
}