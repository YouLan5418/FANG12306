package org.opengoofy.index12306.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.common.core.UserInfoDTO;
import org.opengoofy.index12306.framework.convention.exception.AbstractException;
import org.opengoofy.index12306.user.dao.entity.UserDO;
import org.opengoofy.index12306.user.dao.mapper.UserMapper;
import org.opengoofy.index12306.user.dto.req.PassengerRemoveReqDTO;
import org.opengoofy.index12306.user.dto.req.PassengerReqDTO;
import org.opengoofy.index12306.user.dto.req.UserDeletionReqDTO;
import org.opengoofy.index12306.user.dto.req.UserLoginReqDTO;
import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.opengoofy.index12306.user.dto.req.UserUpdateReqDTO;
import org.opengoofy.index12306.user.dto.resp.PassengerRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserLoginRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserQueryRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserRegisterRespDTO;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3 验收：注册（布隆/责任链/锁/多表写入/回写布隆）、三种方式登录、checkLogin/logout、
 * 乘车人 CRUD（脱敏返回）、用户信息更新、注销与用户名复用。
 * 事务回滚，不污染数据库。
 */
@SpringBootTest
@Transactional
class UserServiceFlowTests {

    @Autowired
    private UserLoginService userLoginService;
    @Autowired
    private UserService userService;
    @Autowired
    private PassengerService passengerService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RBloomFilter<String> userRegisterCachePenetrationBloomFilter;

    private static final String SUFFIX = String.valueOf(System.currentTimeMillis());
    private static final String ID_CARD = "1101011990010" + SUFFIX.substring(SUFFIX.length() - 5);

    /** 每个用例使用独立用户名/手机号/邮箱，避免布隆过滤器与 Redis 复用集合跨用例污染 */
    private String username(String tag) {
        return "p3" + tag + "_" + SUFFIX;
    }

    private String phone(String tag) {
        return "139" + Math.abs((tag + SUFFIX).hashCode() % 100000000);
    }

    private String mail(String tag) {
        return "p3" + tag + "_" + SUFFIX + "@example.com";
    }

    private UserRegisterReqDTO buildRegisterReq(String username, String phone, String mail) {
        UserRegisterReqDTO req = new UserRegisterReqDTO();
        req.setUsername(username);
        req.setPassword("pwd-" + SUFFIX);
        req.setRealName("P3测试");
        req.setIdType(0);
        req.setIdCard(ID_CARD);
        req.setPhone(phone);
        req.setMail(mail);
        return req;
    }

    private void mockLoginUser(String username) {
        UserContext.setUser(UserInfoDTO.builder()
                .userId("1").username(username).realName("P3测试").token("mock-token").build());
    }

    @AfterEach
    void clearContext() {
        UserContext.removeUser();
    }

    @Test
    void testRegisterBloomAndDuplicateRejected() {
        String USERNAME = username("dup"), PHONE = phone("dup"), MAIL = mail("dup");
        assertTrue(userLoginService.hasUsername(USERNAME), "注册前用户名应可用");

        UserRegisterRespDTO response = userLoginService.register(buildRegisterReq(USERNAME, PHONE, MAIL));
        assertEquals(USERNAME, response.getUsername());
        assertTrue(userRegisterCachePenetrationBloomFilter.contains(USERNAME), "注册后布隆过滤器应含新用户");
        assertFalse(userLoginService.hasUsername(USERNAME), "注册后用户名应不可用");

        assertThrows(AbstractException.class,
                () -> userLoginService.register(buildRegisterReq(USERNAME, PHONE, MAIL)), "重复用户名应被拒");
    }

    @Test
    void testRegisterParamChainValidation() {
        String PHONE = phone("chain"), MAIL = mail("chain");
        UserRegisterReqDTO req = buildRegisterReq("p3chain_" + SUFFIX, PHONE, MAIL);
        req.setPassword(null);
        assertThrows(AbstractException.class, () -> userLoginService.register(req));
    }

    @Test
    void testLoginThreeWaysAndLogout() {
        String USERNAME = username("login"), PHONE = phone("login"), MAIL = mail("login");
        userLoginService.register(buildRegisterReq(USERNAME, PHONE, MAIL));

        UserLoginReqDTO byUsername = new UserLoginReqDTO();
        byUsername.setUsernameOrMailOrPhone(USERNAME);
        byUsername.setPassword("pwd-" + SUFFIX);
        UserLoginRespDTO tokenByUsername = userLoginService.login(byUsername);
        assertNotNull(tokenByUsername.getAccessToken());

        UserLoginReqDTO byPhone = new UserLoginReqDTO();
        byPhone.setUsernameOrMailOrPhone(PHONE);
        byPhone.setPassword("pwd-" + SUFFIX);
        assertNotNull(userLoginService.login(byPhone).getAccessToken());

        UserLoginReqDTO byMail = new UserLoginReqDTO();
        byMail.setUsernameOrMailOrPhone(MAIL);
        byMail.setPassword("pwd-" + SUFFIX);
        assertNotNull(userLoginService.login(byMail).getAccessToken());

        UserLoginRespDTO checked = userLoginService.checkLogin(tokenByUsername.getAccessToken());
        assertNotNull(checked, "Redis 应有登录态");
        assertEquals(USERNAME, checked.getUsername());

        userLoginService.logout(tokenByUsername.getAccessToken());
        assertNull(userLoginService.checkLogin(tokenByUsername.getAccessToken()), "登出后 token 应失效");

        UserLoginReqDTO wrongPwd = new UserLoginReqDTO();
        wrongPwd.setUsernameOrMailOrPhone(USERNAME);
        wrongPwd.setPassword("wrong");
        assertThrows(AbstractException.class, () -> userLoginService.login(wrongPwd));
    }

    @Test
    void testPassengerCrudAndMasking() throws Exception {
        String USERNAME = username("psg"), PHONE = phone("psg");
        mockLoginUser(USERNAME);
        PassengerReqDTO saveReq = new PassengerReqDTO();
        saveReq.setRealName("乘车人甲");
        saveReq.setIdType(0);
        saveReq.setIdCard(ID_CARD);
        saveReq.setDiscountType(0);
        saveReq.setPhone(PHONE);
        passengerService.savePassenger(saveReq);

        List<PassengerRespDTO> passengers = passengerService.listPassenger(USERNAME);
        assertEquals(1, passengers.size());
        PassengerRespDTO passenger = passengers.get(0);
        assertEquals(ID_CARD, passenger.getIdCard(), "业务层返回明文");

        // Jackson 序列化时脱敏
        String json = new ObjectMapper().writeValueAsString(passenger);
        assertTrue(json.contains("****"), "序列化结果应脱敏: " + json);
        assertFalse(json.contains(ID_CARD), "序列化结果不应含完整证件号");

        // 重复证件号拒绝
        assertThrows(AbstractException.class, () -> passengerService.savePassenger(saveReq));

        // 修改
        PassengerReqDTO updateReq = new PassengerReqDTO();
        updateReq.setId(passenger.getId());
        updateReq.setRealName("乘车人乙");
        updateReq.setIdCard(ID_CARD);
        updateReq.setPhone(PHONE);
        passengerService.updatePassenger(updateReq);
        assertEquals("乘车人乙", passengerService.listPassenger(USERNAME).get(0).getRealName());

        // 删除
        PassengerRemoveReqDTO removeReq = new PassengerRemoveReqDTO();
        removeReq.setIds(List.of(passenger.getId()));
        passengerService.removePassenger(removeReq);
        assertTrue(passengerService.listPassenger(USERNAME).isEmpty());
    }

    @Test
    void testUpdateThenLoginWithNewPhone() {
        String USERNAME = username("upd"), PHONE = phone("upd"), MAIL = mail("upd");
        userLoginService.register(buildRegisterReq(USERNAME, PHONE, MAIL));
        String newPhone = "137" + SUFFIX.substring(SUFFIX.length() - 8);

        UserUpdateReqDTO updateReq = new UserUpdateReqDTO();
        updateReq.setUsername(USERNAME);
        updateReq.setPhone(newPhone);
        updateReq.setRealName("改名用户");
        userService.update(updateReq);

        UserQueryRespDTO queried = userService.queryUserByUsername(USERNAME);
        assertEquals("改名用户", queried.getRealName());

        UserLoginReqDTO byNewPhone = new UserLoginReqDTO();
        byNewPhone.setUsernameOrMailOrPhone(newPhone);
        byNewPhone.setPassword("pwd-" + SUFFIX);
        assertNotNull(userLoginService.login(byNewPhone).getAccessToken(), "新手机号应能登录");

        UserLoginReqDTO byOldPhone = new UserLoginReqDTO();
        byOldPhone.setUsernameOrMailOrPhone(PHONE);
        byOldPhone.setPassword("pwd-" + SUFFIX);
        assertThrows(AbstractException.class, () -> userLoginService.login(byOldPhone), "旧手机号应失效");
    }

    @Test
    void testDeletionAndUsernameReuse() {
        String USERNAME = username("del"), PHONE = phone("del"), MAIL = mail("del");
        userLoginService.register(buildRegisterReq(USERNAME, PHONE, MAIL));
        mockLoginUser(USERNAME);

        UserDeletionReqDTO deletionReq = new UserDeletionReqDTO();
        deletionReq.setUsername(USERNAME);
        userLoginService.deletion(deletionReq);

        assertTrue(userLoginService.hasUsername(USERNAME), "注销后用户名应可复用");
        assertThrows(AbstractException.class, () -> userService.queryUserByUsername(USERNAME), "注销后查不到用户");

        // 用原用户名 + 原手机号重新注册成功
        UserRegisterRespDTO reRegistered = userLoginService.register(buildRegisterReq(USERNAME, PHONE, MAIL));
        assertEquals(USERNAME, reRegistered.getUsername());
        assertEquals(1, userService.queryUserDeletionNum(0, ID_CARD), "注销记录应有一条");

        // 旧数据应带 deletion_time 标记，当前有效记录只有一条
        Long activeCount = userMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, USERNAME)
                .eq(UserDO::getDeletionTime, 0L));
        assertEquals(1, activeCount);
    }
}