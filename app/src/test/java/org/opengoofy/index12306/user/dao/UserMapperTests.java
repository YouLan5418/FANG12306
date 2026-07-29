package org.opengoofy.index12306.user.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.common.toolkit.AESCryptUtil;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.user.dao.entity.PassengerDO;
import org.opengoofy.index12306.user.dao.entity.UserDO;
import org.opengoofy.index12306.user.dao.entity.UserDeletionDO;
import org.opengoofy.index12306.user.dao.entity.UserMailDO;
import org.opengoofy.index12306.user.dao.entity.UserPhoneDO;
import org.opengoofy.index12306.user.dao.entity.UserReuseDO;
import org.opengoofy.index12306.user.dao.mapper.PassengerMapper;
import org.opengoofy.index12306.user.dao.mapper.UserDeletionMapper;
import org.opengoofy.index12306.user.dao.mapper.UserMailMapper;
import org.opengoofy.index12306.user.dao.mapper.UserMapper;
import org.opengoofy.index12306.user.dao.mapper.UserPhoneMapper;
import org.opengoofy.index12306.user.dao.mapper.UserReuseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-T1 验收：用户域 Mapper insert + selectOne 走对 HASH 分区，AES 加密字段读写一致。
 * 事务回滚，不污染数据库。
 */
@SpringBootTest
@Transactional
class UserMapperTests {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserPhoneMapper userPhoneMapper;
    @Autowired
    private UserMailMapper userMailMapper;
    @Autowired
    private PassengerMapper passengerMapper;
    @Autowired
    private UserDeletionMapper userDeletionMapper;
    @Autowired
    private UserReuseMapper userReuseMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String USERNAME = "mapper_test_" + System.currentTimeMillis();
    private static final String PHONE = "13800001234";
    private static final String MAIL = "mapper_test@example.com";
    private static final String ID_CARD = "110101199001011234";

    @Test
    void testUserInsertAndSelectOneHitPartition() {
        UserDO user = new UserDO();
        long id = SnowflakeIdUtil.nextId();
        user.setId(id);
        user.setUsername(USERNAME);
        user.setPassword("hashed-password");
        user.setRealName("测试用户");
        user.setRegion("0");
        user.setIdType(0);
        user.setIdCard(ID_CARD);
        user.setPhone(PHONE);
        user.setMail(MAIL);
        user.setDeletionTime(0L);
        assertEquals(1, userMapper.insert(user));

        UserDO actual = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getId, id)
                .eq(UserDO::getUsername, USERNAME));
        assertNotNull(actual);
        // 加密字段读取后解密为明文，读写一致
        assertEquals(PHONE, actual.getPhone());
        assertEquals(ID_CARD, actual.getIdCard());
        assertEquals(MAIL, actual.getMail());
        // 落库为密文
        String rawPhone = jdbcTemplate.queryForObject(
                "SELECT phone FROM t_user WHERE id = ? AND username = ?", String.class, id, USERNAME);
        assertNotEquals(PHONE, rawPhone);
        assertEquals(PHONE, AESCryptUtil.decrypt(rawPhone));
        // 数据落在 HASH 子分区
        String partition = jdbcTemplate.queryForObject(
                "SELECT tableoid::regclass::text FROM t_user WHERE id = ? AND username = ?",
                String.class, id, USERNAME);
        assertTrue(partition.startsWith("t_user_p"), "t_user 应落在子分区，实际: " + partition);
    }

    @Test
    void testPhoneMailReverseLookupPartition() {
        UserPhoneDO phoneDO = new UserPhoneDO();
        phoneDO.setId(SnowflakeIdUtil.nextId());
        phoneDO.setUsername(USERNAME);
        phoneDO.setPhone(PHONE);
        phoneDO.setDeletionTime(0L);
        assertEquals(1, userPhoneMapper.insert(phoneDO));

        UserPhoneDO phoneActual = userPhoneMapper.selectOne(new LambdaQueryWrapper<UserPhoneDO>()
                .eq(UserPhoneDO::getPhone, PHONE));
        assertNotNull(phoneActual);
        assertEquals(USERNAME, phoneActual.getUsername());
        String phonePartition = jdbcTemplate.queryForObject(
                "SELECT tableoid::regclass::text FROM t_user_phone WHERE phone = ?", String.class, PHONE);
        assertTrue(phonePartition.startsWith("t_user_phone_p"), "t_user_phone 应落在子分区，实际: " + phonePartition);

        UserMailDO mailDO = new UserMailDO();
        mailDO.setId(SnowflakeIdUtil.nextId());
        mailDO.setUsername(USERNAME);
        mailDO.setMail(MAIL);
        mailDO.setDeletionTime(0L);
        assertEquals(1, userMailMapper.insert(mailDO));

        UserMailDO mailActual = userMailMapper.selectOne(new LambdaQueryWrapper<UserMailDO>()
                .eq(UserMailDO::getMail, MAIL));
        assertNotNull(mailActual);
        assertEquals(USERNAME, mailActual.getUsername());
        String mailPartition = jdbcTemplate.queryForObject(
                "SELECT tableoid::regclass::text FROM t_user_mail WHERE mail = ?", String.class, MAIL);
        assertTrue(mailPartition.startsWith("t_user_mail_p"), "t_user_mail 应落在子分区，实际: " + mailPartition);
    }

    @Test
    void testPassengerInsertSelectEncryptConsistent() {
        PassengerDO passenger = new PassengerDO();
        passenger.setId(SnowflakeIdUtil.nextId());
        passenger.setUsername(USERNAME);
        passenger.setRealName("乘车人甲");
        passenger.setIdType(0);
        passenger.setIdCard(ID_CARD);
        passenger.setDiscountType(0);
        passenger.setPhone(PHONE);
        passenger.setVerifyStatus(0);
        assertEquals(1, passengerMapper.insert(passenger));

        // 注意：Wrapper 条件参数不经过 TypeHandler，加密字段作查询条件需先显式加密
        PassengerDO actual = passengerMapper.selectOne(new LambdaQueryWrapper<PassengerDO>()
                .eq(PassengerDO::getUsername, USERNAME)
                .eq(PassengerDO::getIdCard, AESCryptUtil.encrypt(ID_CARD)));
        assertNotNull(actual);
        assertEquals(ID_CARD, actual.getIdCard());
        assertEquals(PHONE, actual.getPhone());

        String rawIdCard = jdbcTemplate.queryForObject(
                "SELECT id_card FROM t_passenger WHERE username = ? AND id_card = ?",
                String.class, USERNAME, AESCryptUtil.encrypt(ID_CARD));
        assertNotEquals(ID_CARD, rawIdCard);
        String partition = jdbcTemplate.queryForObject(
                "SELECT tableoid::regclass::text FROM t_passenger WHERE username = ?",
                String.class, USERNAME);
        assertTrue(partition.startsWith("t_passenger_p"), "t_passenger 应落在子分区，实际: " + partition);
    }

    @Test
    void testUserDeletionAndReuse() {
        UserDeletionDO deletion = new UserDeletionDO();
        deletion.setId(SnowflakeIdUtil.nextId());
        deletion.setIdType(0);
        deletion.setIdCard(ID_CARD);
        assertEquals(1, userDeletionMapper.insert(deletion));

        UserDeletionDO deletionActual = userDeletionMapper.selectOne(new LambdaQueryWrapper<UserDeletionDO>()
                .eq(UserDeletionDO::getIdCard, AESCryptUtil.encrypt(ID_CARD)));
        assertNotNull(deletionActual);
        assertEquals(ID_CARD, deletionActual.getIdCard());

        UserReuseDO reuse = new UserReuseDO();
        reuse.setId(SnowflakeIdUtil.nextId());
        reuse.setUsername(USERNAME);
        assertEquals(1, userReuseMapper.insert(reuse));

        UserReuseDO reuseActual = userReuseMapper.selectOne(new LambdaQueryWrapper<UserReuseDO>()
                .eq(UserReuseDO::getUsername, USERNAME));
        assertNotNull(reuseActual);
        assertEquals(USERNAME, reuseActual.getUsername());
    }
}
