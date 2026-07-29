package org.opengoofy.index12306.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.common.constant.UserConstant;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.common.core.UserInfoDTO;
import org.opengoofy.index12306.common.toolkit.JWTUtil;
import org.opengoofy.index12306.framework.cache.DistributedCache;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.framework.convention.exception.ServiceException;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.user.common.constant.RedisKeyConstant;
import org.opengoofy.index12306.user.service.UserAvailabilityChecker;
import org.opengoofy.index12306.user.common.enums.UserRegisterErrorCodeEnum;
import org.opengoofy.index12306.user.dao.entity.UserDO;
import org.opengoofy.index12306.user.dao.entity.UserDeletionDO;
import org.opengoofy.index12306.user.dao.entity.UserMailDO;
import org.opengoofy.index12306.user.dao.entity.UserPhoneDO;
import org.opengoofy.index12306.user.dao.entity.UserReuseDO;
import org.opengoofy.index12306.user.dao.mapper.UserDeletionMapper;
import org.opengoofy.index12306.user.dao.mapper.UserMailMapper;
import org.opengoofy.index12306.user.dao.mapper.UserMapper;
import org.opengoofy.index12306.user.dao.mapper.UserPhoneMapper;
import org.opengoofy.index12306.user.dao.mapper.UserReuseMapper;
import org.opengoofy.index12306.user.dto.req.UserDeletionReqDTO;
import org.opengoofy.index12306.user.dto.req.UserLoginReqDTO;
import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.opengoofy.index12306.user.dto.resp.UserLoginRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserQueryActualRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserRegisterRespDTO;
import org.opengoofy.index12306.user.service.UserLoginService;
import org.opengoofy.index12306.user.service.UserService;
import org.opengoofy.index12306.user.service.handler.UserRegisterChainContext;
import org.opengoofy.index12306.user.toolkit.UserReuseUtil;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 用户登录/注册服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginServiceImpl implements UserLoginService {

    private final UserService userService;
    private final UserMapper userMapper;
    private final UserReuseMapper userReuseMapper;
    private final UserDeletionMapper userDeletionMapper;
    private final UserPhoneMapper userPhoneMapper;
    private final UserMailMapper userMailMapper;
    private final RedissonClient redissonClient;
    private final DistributedCache distributedCache;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRegisterChainContext userRegisterChainContext;
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final UserAvailabilityChecker userAvailabilityChecker;

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        String usernameOrMailOrPhone = requestParam.getUsernameOrMailOrPhone();
        boolean mailFlag = usernameOrMailOrPhone != null && usernameOrMailOrPhone.indexOf('@') >= 0;
        String username = null;
        if (mailFlag) {
            username = Optional.ofNullable(userMailMapper.selectOne(Wrappers.lambdaQuery(UserMailDO.class)
                            .eq(UserMailDO::getMail, usernameOrMailOrPhone)
                            .eq(UserMailDO::getDeletionTime, 0L)))
                    .map(UserMailDO::getUsername)
                    .orElseThrow(() -> new ClientException("用户名/手机号/邮箱不存在"));
        } else {
            username = Optional.ofNullable(userPhoneMapper.selectOne(Wrappers.lambdaQuery(UserPhoneDO.class)
                            .eq(UserPhoneDO::getPhone, usernameOrMailOrPhone)
                            .eq(UserPhoneDO::getDeletionTime, 0L)))
                    .map(UserPhoneDO::getUsername)
                    .orElse(null);
        }
        username = Optional.ofNullable(username).orElse(usernameOrMailOrPhone);
        UserDO userDO = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDeletionTime, 0L)
                .eq(UserDO::getPassword, requestParam.getPassword())
                .select(UserDO::getId, UserDO::getUsername, UserDO::getRealName));
        if (userDO == null) {
            throw new ServiceException("账号不存在或密码错误");
        }
        UserInfoDTO userInfo = UserInfoDTO.builder()
                .userId(String.valueOf(userDO.getId()))
                .username(userDO.getUsername())
                .realName(userDO.getRealName())
                .build();
        String accessToken = JWTUtil.generateAccessToken(userInfo);
        UserLoginRespDTO response = UserLoginRespDTO.builder()
                .userId(userInfo.getUserId())
                .username(userInfo.getUsername())
                .realName(userInfo.getRealName())
                .accessToken(accessToken)
                .build();
        distributedCache.put(UserConstant.LOGIN_TOKEN_KEY_PREFIX + accessToken, JSON.toJSONString(response), 30, TimeUnit.MINUTES);
        return response;
    }

    @Override
    public UserLoginRespDTO checkLogin(String accessToken) {
        return distributedCache.get(UserConstant.LOGIN_TOKEN_KEY_PREFIX + accessToken, UserLoginRespDTO.class);
    }

    @Override
    public void logout(String accessToken) {
        if (StrUtil.isNotBlank(accessToken)) {
            distributedCache.delete(UserConstant.LOGIN_TOKEN_KEY_PREFIX + accessToken);
        }
    }

    @Override
    public Boolean hasUsername(String username) {
        return userAvailabilityChecker.hasUsername(username);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserRegisterRespDTO register(UserRegisterReqDTO requestParam) {
        userRegisterChainContext.execute(requestParam);
        RLock lock = redissonClient.getLock(RedisKeyConstant.LOCK_USER_REGISTER + requestParam.getUsername());
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            throw new ServiceException(UserRegisterErrorCodeEnum.HAS_USERNAME_NOTNULL);
        }
        try {
            UserDO userDO = BeanUtil.copyProperties(requestParam, UserDO.class);
            userDO.setId(SnowflakeIdUtil.nextId());
            userDO.setDeletionTime(0L);
            if (StrUtil.isBlank(userDO.getRegion())) {
                userDO.setRegion("0");
            }
            try {
                int inserted = userMapper.insert(userDO);
                if (inserted < 1) {
                    throw new ServiceException(UserRegisterErrorCodeEnum.USER_REGISTER_FAIL);
                }
            } catch (DuplicateKeyException dke) {
                log.error("用户名 [{}] 重复注册", requestParam.getUsername());
                throw new ServiceException(UserRegisterErrorCodeEnum.HAS_USERNAME_NOTNULL);
            }
            UserPhoneDO userPhoneDO = new UserPhoneDO();
            userPhoneDO.setId(SnowflakeIdUtil.nextId());
            userPhoneDO.setPhone(requestParam.getPhone());
            userPhoneDO.setUsername(requestParam.getUsername());
            userPhoneDO.setDeletionTime(0L);
            try {
                userPhoneMapper.insert(userPhoneDO);
            } catch (DuplicateKeyException dke) {
                log.error("用户 [{}] 注册手机号 [{}] 重复", requestParam.getUsername(), requestParam.getPhone());
                throw new ServiceException(UserRegisterErrorCodeEnum.PHONE_REGISTERED);
            }
            if (StrUtil.isNotBlank(requestParam.getMail())) {
                UserMailDO userMailDO = new UserMailDO();
                userMailDO.setId(SnowflakeIdUtil.nextId());
                userMailDO.setMail(requestParam.getMail());
                userMailDO.setUsername(requestParam.getUsername());
                userMailDO.setDeletionTime(0L);
                try {
                    userMailMapper.insert(userMailDO);
                } catch (DuplicateKeyException dke) {
                    log.error("用户 [{}] 注册邮箱 [{}] 重复", requestParam.getUsername(), requestParam.getMail());
                    throw new ServiceException(UserRegisterErrorCodeEnum.MAIL_REGISTERED);
                }
            }
            String username = requestParam.getUsername();
            userReuseMapper.delete(Wrappers.lambdaQuery(UserReuseDO.class).eq(UserReuseDO::getUsername, username));
            stringRedisTemplate.opsForSet().remove(
                    RedisKeyConstant.USER_REGISTER_REUSE_SHARDING + UserReuseUtil.hashShardingIdx(username), username);
            userRegisterCachePenetrationBloomFilter.add(username);
        } finally {
            lock.unlock();
        }
        return new UserRegisterRespDTO(requestParam.getUsername());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deletion(UserDeletionReqDTO requestParam) {
        String username = UserContext.getUsername();
        if (!Objects.equals(username, requestParam.getUsername())) {
            throw new ClientException("注销账号与登录账号不一致");
        }
        RLock lock = redissonClient.getLock(RedisKeyConstant.LOCK_USER_DELETION + requestParam.getUsername());
        lock.lock();
        try {
            UserQueryActualRespDTO actual = userService.queryActualUserByUsername(username);
            long now = System.currentTimeMillis();
            UserDeletionDO userDeletionDO = new UserDeletionDO();
            userDeletionDO.setIdType(actual.getIdType());
            userDeletionDO.setIdCard(actual.getIdCard());
            userDeletionMapper.insert(userDeletionDO);
            userMapper.update(null, Wrappers.lambdaUpdate(UserDO.class)
                    .eq(UserDO::getUsername, username)
                    .eq(UserDO::getDeletionTime, 0L)
                    .set(UserDO::getDeletionTime, now));
            UserQueryActualRespDTO finalActual = actual;
            if (StrUtil.isNotBlank(actual.getPhone())) {
                userPhoneMapper.update(null, Wrappers.lambdaUpdate(UserPhoneDO.class)
                        .eq(UserPhoneDO::getPhone, finalActual.getPhone())
                        .eq(UserPhoneDO::getDeletionTime, 0L)
                        .set(UserPhoneDO::getDeletionTime, now));
            }
            if (StrUtil.isNotBlank(actual.getMail())) {
                userMailMapper.update(null, Wrappers.lambdaUpdate(UserMailDO.class)
                        .eq(UserMailDO::getMail, finalActual.getMail())
                        .eq(UserMailDO::getDeletionTime, 0L)
                        .set(UserMailDO::getDeletionTime, now));
            }
            distributedCache.delete(UserConstant.LOGIN_TOKEN_KEY_PREFIX + UserContext.getToken());
            UserReuseDO userReuseDO = new UserReuseDO();
            userReuseDO.setUsername(username);
            userReuseMapper.insert(userReuseDO);
            stringRedisTemplate.opsForSet().add(
                    RedisKeyConstant.USER_REGISTER_REUSE_SHARDING + UserReuseUtil.hashShardingIdx(username), username);
        } finally {
            lock.unlock();
        }
    }
}