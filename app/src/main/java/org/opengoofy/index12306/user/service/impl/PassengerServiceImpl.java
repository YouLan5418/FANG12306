package org.opengoofy.index12306.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.common.toolkit.AESCryptUtil;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.user.common.constant.RedisKeyConstant;
import org.opengoofy.index12306.user.dao.entity.PassengerDO;
import org.opengoofy.index12306.user.dao.mapper.PassengerMapper;
import org.opengoofy.index12306.user.dto.req.PassengerRemoveReqDTO;
import org.opengoofy.index12306.user.dto.req.PassengerReqDTO;
import org.opengoofy.index12306.user.dto.resp.PassengerActualRespDTO;
import org.opengoofy.index12306.user.dto.resp.PassengerRespDTO;
import org.opengoofy.index12306.user.service.PassengerService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 乘车人服务实现。列表走 Redis 缓存，写操作后失效。
 */
@Service
@RequiredArgsConstructor
public class PassengerServiceImpl implements PassengerService {

    private final PassengerMapper passengerMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<PassengerRespDTO> listPassenger(String username) {
        String cacheKey = RedisKeyConstant.USER_PASSENGER_LIST + username;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseArray(cached, PassengerRespDTO.class);
        }
        List<PassengerRespDTO> passengers = listPassengerDO(username).stream()
                .map(each -> BeanUtil.copyProperties(each, PassengerRespDTO.class))
                .collect(Collectors.toList());
        stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(passengers), 10, TimeUnit.MINUTES);
        return passengers;
    }

    @Override
    public List<PassengerActualRespDTO> listPassengerActual(String username, List<String> ids) {
        return listPassengerDO(username).stream()
                .filter(each -> ids == null || ids.isEmpty() || ids.contains(String.valueOf(each.getId())))
                .map(each -> BeanUtil.copyProperties(each, PassengerActualRespDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public void savePassenger(PassengerReqDTO requestParam) {
        String username = UserContext.getUsername();
        // 证件号密文作等值查询条件需显式加密
        Long count = passengerMapper.selectCount(Wrappers.lambdaQuery(PassengerDO.class)
                .eq(PassengerDO::getUsername, username)
                .eq(PassengerDO::getIdCard, AESCryptUtil.encrypt(requestParam.getIdCard())));
        if (count != null && count > 0) {
            throw new ClientException("该乘车人已存在，请勿重复添加");
        }
        PassengerDO passengerDO = BeanUtil.copyProperties(requestParam, PassengerDO.class);
        passengerDO.setId(SnowflakeIdUtil.nextId());
        passengerDO.setUsername(username);
        passengerDO.setCreateDate(new Date());
        passengerDO.setVerifyStatus(0);
        passengerMapper.insert(passengerDO);
        evictCache(username);
    }

    @Override
    public void updatePassenger(PassengerReqDTO requestParam) {
        String username = UserContext.getUsername();
        PassengerDO passengerDO = BeanUtil.copyProperties(requestParam, PassengerDO.class);
        passengerDO.setId(Long.parseLong(requestParam.getId()));
        passengerDO.setUsername(null);
        int updated = passengerMapper.update(passengerDO, Wrappers.lambdaUpdate(PassengerDO.class)
                .eq(PassengerDO::getId, requestParam.getId())
                .eq(PassengerDO::getUsername, username));
        if (updated < 1) {
            throw new ClientException("乘车人不存在");
        }
        evictCache(username);
    }

    @Override
    public void removePassenger(PassengerRemoveReqDTO requestParam) {
        String username = UserContext.getUsername();
        int deleted = passengerMapper.delete(Wrappers.lambdaQuery(PassengerDO.class)
                .eq(PassengerDO::getUsername, username)
                .in(PassengerDO::getId, requestParam.getIds()));
        if (deleted < 1) {
            throw new ClientException("乘车人不存在");
        }
        evictCache(username);
    }

    private List<PassengerDO> listPassengerDO(String username) {
        return passengerMapper.selectList(Wrappers.lambdaQuery(PassengerDO.class)
                .eq(PassengerDO::getUsername, username)
                .orderByDesc(PassengerDO::getCreateDate));
    }

    private void evictCache(String username) {
        stringRedisTemplate.delete(RedisKeyConstant.USER_PASSENGER_LIST + username);
    }
}