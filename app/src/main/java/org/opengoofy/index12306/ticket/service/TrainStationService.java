package org.opengoofy.index12306.ticket.service;

import org.opengoofy.index12306.ticket.dao.entity.TrainStationRelationDO;

/**
 * 列车车站关系服务。
 */
public interface TrainStationService {

    TrainStationRelationDO getRelation(Long trainId, String departure, String arrival);
}