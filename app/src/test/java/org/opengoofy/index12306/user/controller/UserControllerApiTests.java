package org.opengoofy.index12306.user.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.user.dto.req.UserLoginReqDTO;
import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3 接口级验收：POST /register 返回统一 Result；has-username / login / check-login 全链路。
 * 事务回滚，不污染数据库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerApiTests {

    @Autowired
    private MockMvc mockMvc;

    private static final String SUFFIX = String.valueOf(System.currentTimeMillis());
    private static final String USERNAME = "p3api_" + SUFFIX;

    @Test
    void testRegisterLoginCheckLoginViaApi() throws Exception {
        UserRegisterReqDTO registerReq = new UserRegisterReqDTO();
        registerReq.setUsername(USERNAME);
        registerReq.setPassword("pwd-" + SUFFIX);
        registerReq.setRealName("API测试");
        registerReq.setIdType(0);
        registerReq.setIdCard("110101199001011234");
        registerReq.setPhone("139" + SUFFIX.substring(SUFFIX.length() - 8));
        registerReq.setMail("p3api_" + SUFFIX + "@example.com");

        mockMvc.perform(post("/api/user-service/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.username").value(USERNAME));

        mockMvc.perform(get("/api/user-service/has-username").param("username", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        // 重复注册返回统一错误结构
        mockMvc.perform(post("/api/user-service/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("0")));

        UserLoginReqDTO loginReq = new UserLoginReqDTO();
        loginReq.setUsernameOrMailOrPhone(USERNAME);
        loginReq.setPassword("pwd-" + SUFFIX);
        MvcResult loginResult = mockMvc.perform(post("/api/user-service/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn();
        JSONObject loginData = JSON.parseObject(loginResult.getResponse().getContentAsString()).getJSONObject("data");
        String accessToken = loginData.getString("accessToken");
        assertNotNull(accessToken);

        mockMvc.perform(get("/api/user-service/check-login").param("accessToken", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.username").value(USERNAME));

        mockMvc.perform(get("/api/user-service/logout").param("accessToken", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }
}