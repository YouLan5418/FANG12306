package org.opengoofy.index12306.user.service;

import org.opengoofy.index12306.user.dto.req.PassengerRemoveReqDTO;
import org.opengoofy.index12306.user.dto.req.PassengerReqDTO;
import org.opengoofy.index12306.user.dto.resp.PassengerActualRespDTO;
import org.opengoofy.index12306.user.dto.resp.PassengerRespDTO;

import java.util.List;

/**
 * 乘车人服务。
 */
public interface PassengerService {

    List<PassengerRespDTO> listPassenger(String username);

    List<PassengerActualRespDTO> listPassengerActual(String username, List<String> ids);

    void savePassenger(PassengerReqDTO requestParam);

    void updatePassenger(PassengerReqDTO requestParam);

    void removePassenger(PassengerRemoveReqDTO requestParam);
}