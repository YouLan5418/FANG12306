package org.opengoofy.index12306.common.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.opengoofy.index12306.common.toolkit.AESCryptUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * AES 加密字段 TypeHandler。
 * 写入数据库时加密，读取时解密，业务层无感知。
 */
@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(String.class)
public class AESEncryptTypeHandler extends BaseTypeHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(AESEncryptTypeHandler.class);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, AESCryptUtil.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decryptSafely(rs.getString(columnName), columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decryptSafely(rs.getString(columnIndex), String.valueOf(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decryptSafely(cs.getString(columnIndex), String.valueOf(columnIndex));
    }

    private String decryptSafely(String ciphertext, String column) {
        try {
            return AESCryptUtil.decrypt(ciphertext);
        } catch (RuntimeException ex) {
            log.warn("敏感字段解密失败，column={}，可能是历史数据密钥不一致或脏数据，已按空值返回", column);
            return null;
        }
    }
}
