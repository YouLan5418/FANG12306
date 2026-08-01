const fs = require("fs");
const path = require("path");

// BaseDO 放 app/common/entity 基础类
const baseDir = "D:\\12306\\app\\src\\main\\java\\org\\opengoofy\\index12306\\common\\entity";
fs.mkdirSync(baseDir, { recursive: true });

fs.writeFileSync(path.join(baseDir, "BaseDO.java"), `package org.opengoofy.index12306.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.Date;

/**
 * 数据持久层基础属性，由 MyMetaObjectHandler 自动填充。
 */
@Data
public class BaseDO {

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
`, "utf8");

// 用户域实体目录
const userDir = "D:\\12306\\app\\src\\main\\java\\org\\opengoofy\\index12306\\user\\dao\\entity";
fs.mkdirSync(userDir, { recursive: true });
const userMapperDir = "D:\\12306\\app\\src\\main\\java\\org\\opengoofy\\index12306\\user\\dao\\mapper";
fs.mkdirSync(userMapperDir, { recursive: true });

const AES_TH = "org.opengoofy.index12306.common.config.AESEncryptTypeHandler";

const userEntities = {
  "UserDO.java": `package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;
import org.opengoofy.index12306.common.config.AESEncryptTypeHandler;

/**
 * 用户主表，按 username HASH 分区，主键 (id, username)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_user", autoResultMap = true)
public class UserDO extends BaseDO {

    private Long id;
    private String username;
    private String password;
    private String realName;
    private String region;
    private Integer idType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String idCard;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String phone;

    private String telephone;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String mail;

    private Integer userType;
    private Integer verifyStatus;
    private String postCode;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String address;

    private Long deletionTime;
}
`,
  "UserPhoneDO.java": `package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 手机号反查 username，按 phone HASH 分区，主键 (id, phone)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_phone")
public class UserPhoneDO extends BaseDO {

    private Long id;
    private String username;
    private String phone;
    private Long deletionTime;
}
`,
  "UserMailDO.java": `package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 邮箱反查 username，按 mail HASH 分区，主键 (id, mail)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_mail")
public class UserMailDO extends BaseDO {

    private Long id;
    private String username;
    private String mail;
    private Long deletionTime;
}
`,
  "PassengerDO.java": `package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;
import org.opengoofy.index12306.common.config.AESEncryptTypeHandler;

/**
 * 乘车人，按 username HASH 分区，主键 (id, username)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_passenger", autoResultMap = true)
public class PassengerDO extends BaseDO {

    private Long id;
    private String username;
    private String realName;
    private Integer idType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String idCard;

    private Integer discountType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String phone;

    private Date createDate;
    private Integer verifyStatus;
}
`,
  "UserDeletionDO.java": `package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;
import org.opengoofy.index12306.common.config.AESEncryptTypeHandler;

/**
 * 用户注销记录，单表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_user_deletion", autoResultMap = true)
public class UserDeletionDO extends BaseDO {

    private Long id;
    private Integer idType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String idCard;
}
`,
  "UserReuseDO.java": `package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 注销后可重用的 username 记录，单表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_reuse")
public class UserReuseDO extends BaseDO {

    private Long id;
    private String username;
}
`
};

for (const [name, content] of Object.entries(userEntities)) {
  fs.writeFileSync(path.join(userDir, name), content, "utf8");
}

// 用户域 Mapper（分区表需指定分区键，MyBatis-Plus BaseMapper 即可，分区路由由 PG 自动完成）
const userMappers = {
  "UserMapper.java": `package org.opengoofy.index12306.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.opengoofy.index12306.user.dao.entity.UserDO;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
`,
  "UserPhoneMapper.java": `package org.opengoofy.index12306.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.opengoofy.index12306.user.dao.entity.UserPhoneDO;

@Mapper
public interface UserPhoneMapper extends BaseMapper<UserPhoneDO> {
}
`,
  "UserMailMapper.java": `package org.opengoofy.index12306.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.opengoofy.index12306.user.dao.entity.UserMailDO;

@Mapper
public interface UserMailMapper extends BaseMapper<UserMailDO> {
}
`,
  "PassengerMapper.java": `package org.opengoofy.index12306.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.opengoofy.index12306.user.dao.entity.PassengerDO;

@Mapper
public interface PassengerMapper extends BaseMapper<PassengerDO> {
}
`,
  "UserDeletionMapper.java": `package org.opengoofy.index12306.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.opengoofy.index12306.user.dao.entity.UserDeletionDO;

@Mapper
public interface UserDeletionMapper extends BaseMapper<UserDeletionDO> {
}
`,
  "UserReuseMapper.java": `package org.opengoofy.index12306.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.opengoofy.index12306.user.dao.entity.UserReuseDO;

@Mapper
public interface UserReuseMapper extends BaseMapper<UserReuseDO> {
}
`
};

for (const [name, content] of Object.entries(userMappers)) {
  fs.writeFileSync(path.join(userMapperDir, name), content, "utf8");
}

console.log("P2-T1 用户域: " + Object.keys(userEntities).length + " 实体 + " + Object.keys(userMappers).length + " Mapper 已创建");