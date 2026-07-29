package org.opengoofy.index12306.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.common.toolkit.AESCryptUtil;
import org.opengoofy.index12306.user.dao.entity.UserDO;
import org.opengoofy.index12306.user.dao.entity.UserDeletionDO;
import org.opengoofy.index12306.user.dao.entity.UserMailDO;
import org.opengoofy.index12306.user.dao.entity.UserPhoneDO;
import org.opengoofy.index12306.user.dao.mapper.UserDeletionMapper;
import org.opengoofy.index12306.user.dao.mapper.UserMailMapper;
import org.opengoofy.index12306.user.dao.mapper.UserMapper;
import org.opengoofy.index12306.user.dao.mapper.UserPhoneMapper;
import org.opengoofy.index12306.user.dto.req.UserUpdateReqDTO;
import org.opengoofy.index12306.user.dto.resp.UserQueryActualRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserQueryRespDTO;
import org.opengoofy.index12306.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * 用户信息服务实现。所有按 username 的查询均带 deletion_time = 0 过滤注销记录。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserDeletionMapper userDeletionMapper;
    private final UserPhoneMapper userPhoneMapper;
    private final UserMailMapper userMailMapper;

    @Override
    public UserQueryRespDTO queryUserByUsername(String username) {
        UserDO userDO = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDeletionTime, 0L));
        if (userDO == null) {
            throw new ClientException("用户不存在，请检查用户名是否正确");
        }
        return BeanUtil.copyProperties(userDO, UserQueryRespDTO.class);
    }

    @Override
    public UserQueryActualRespDTO queryActualUserByUsername(String username) {
        UserDO userDO = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username)
                .eq(UserDO::getDeletionTime, 0L));
        if (userDO == null) {
            throw new ClientException("用户不存在，请检查用户名是否正确");
        }
        return BeanUtil.copyProperties(userDO, UserQueryActualRespDTO.class);
    }

    @Override
    public Integer queryUserDeletionNum(Integer idType, String idCard) {
        // 证件号落库为密文，Wrapper 条件参数不经过 TypeHandler，需显式加密
        Long count = userDeletionMapper.selectCount(Wrappers.lambdaQuery(UserDeletionDO.class)
                .eq(UserDeletionDO::getIdType, idType)
                .eq(UserDeletionDO::getIdCard, AESCryptUtil.encrypt(idCard)));
        return Optional.ofNullable(count).map(Long::intValue).orElse(0);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void update(UserUpdateReqDTO requestParam) {
        UserQueryActualRespDTO actual = queryActualUserByUsername(requestParam.getUsername());
        UserDO userDO = BeanUtil.copyProperties(requestParam, UserDO.class);
        userDO.setId(null);
        userMapper.update(userDO, Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getDeletionTime, 0L));
        long now = System.currentTimeMillis();
        // 手机号变更：旧反查记录标记删除时间，写入新记录
        if (StrUtil.isNotBlank(requestParam.getPhone()) && !Objects.equals(requestParam.getPhone(), actual.getPhone())) {
            userPhoneMapper.update(null, Wrappers.lambdaUpdate(UserPhoneDO.class)
                    .eq(UserPhoneDO::getPhone, actual.getPhone())
                    .eq(UserPhoneDO::getDeletionTime, 0L)
                    .set(UserPhoneDO::getDeletionTime, now));
            UserPhoneDO userPhoneDO = new UserPhoneDO();
            userPhoneDO.setId(SnowflakeIdUtil.nextId());
            userPhoneDO.setPhone(requestParam.getPhone());
            userPhoneDO.setUsername(requestParam.getUsername());
            userPhoneDO.setDeletionTime(0L);
            userPhoneMapper.insert(userPhoneDO);
        }
        if (StrUtil.isNotBlank(requestParam.getMail()) && !Objects.equals(requestParam.getMail(), actual.getMail())) {
            userMailMapper.update(null, Wrappers.lambdaUpdate(UserMailDO.class)
                    .eq(UserMailDO::getMail, actual.getMail())
                    .eq(UserMailDO::getDeletionTime, 0L)
                    .set(UserMailDO::getDeletionTime, now));
            UserMailDO userMailDO = new UserMailDO();
            userMailDO.setId(SnowflakeIdUtil.nextId());
            userMailDO.setMail(requestParam.getMail());
            userMailDO.setUsername(requestParam.getUsername());
            userMailDO.setDeletionTime(0L);
            userMailMapper.insert(userMailDO);
        }
    }
}