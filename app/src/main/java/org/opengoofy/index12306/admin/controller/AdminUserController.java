package org.opengoofy.index12306.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.page.PageResponse;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.user.dao.entity.UserDO;
import org.opengoofy.index12306.user.dao.mapper.UserMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理员用户管理控制器。
 */
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;

    @GetMapping("/list")
    public Result<PageResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        var wrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getDeletionTime, 0L)
                .select(UserDO::getId, UserDO::getUsername, UserDO::getRealName,
                        UserDO::getRegion, UserDO::getUserType, UserDO::getVerifyStatus,
                        UserDO::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(UserDO::getUsername, keyword)
                    .or().like(UserDO::getRealName, keyword));
        }
        wrapper.orderByDesc(UserDO::getCreateTime);
        IPage<UserDO> result = userMapper.selectPage(Page.of(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("region", u.getRegion());
            m.put("userType", u.getUserType());
            m.put("verifyStatus", u.getVerifyStatus());
            m.put("phone", "***");
            m.put("mail", "***");
            m.put("idCard", "***");
            m.put("createTime", u.getCreateTime());
            return m;
        }).toList();
        PageResponse<Map<String, Object>> resp = new PageResponse<>(page, size, result.getTotal());
        resp.setRecords(records);
        return Results.success(resp);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        UserDO u = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getId, id)
                .select(UserDO::getId, UserDO::getUsername, UserDO::getRealName,
                        UserDO::getRegion, UserDO::getIdType, UserDO::getUserType,
                        UserDO::getVerifyStatus, UserDO::getPostCode,
                        UserDO::getCreateTime, UserDO::getUpdateTime));
        if (u == null) {
            return Results.success(null);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        m.put("region", u.getRegion());
        m.put("idType", u.getIdType());
        m.put("userType", u.getUserType());
        m.put("verifyStatus", u.getVerifyStatus());
        m.put("phone", "***");
        m.put("mail", "***");
        m.put("idCard", "***");
        m.put("postCode", u.getPostCode());
        m.put("address", "***");
        m.put("createTime", u.getCreateTime());
        m.put("updateTime", u.getUpdateTime());
        return Results.success(m);
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        UserDO u = userMapper.selectById(id);
        if (u != null) {
            int newType = (u.getUserType() != null && u.getUserType() == 1) ? 0 : 1;
            userMapper.update(null, Wrappers.lambdaUpdate(UserDO.class)
                    .eq(UserDO::getId, id)
                    .set(UserDO::getUserType, newType));
        }
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userMapper.update(null, Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getId, id)
                .set(UserDO::getDeletionTime, System.currentTimeMillis()));
        return Results.success();
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long total = userMapper.selectCount(
                Wrappers.lambdaQuery(UserDO.class).eq(UserDO::getDeletionTime, 0L));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        return Results.success(m);
    }
}
