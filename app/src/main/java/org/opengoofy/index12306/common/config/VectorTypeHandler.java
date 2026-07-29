package org.opengoofy.index12306.common.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * pgvector 向量类型 TypeHandler。
 * 写入时把 List<Float> 序列化为 "[0.1,0.2,...]" 字面量（依赖 JDBC url stringtype=unspecified 由 PG 侧转 vector），
 * 读取时解析回 List<Float>。
 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(List.class)
public class VectorTypeHandler extends BaseTypeHandler<List<Float>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Float> parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, toLiteral(parameter));
    }

    @Override
    public List<Float> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<Float> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<Float> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    public static String toLiteral(List<Float> vector) {
        return vector.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    public static List<Float> parse(String literal) {
        if (literal == null || literal.length() < 2) {
            return null;
        }
        String body = literal.substring(1, literal.length() - 1);
        if (body.isEmpty()) {
            return new ArrayList<>();
        }
        List<Float> result = new ArrayList<>();
        for (String part : body.split(",")) {
            result.add(Float.parseFloat(part.trim()));
        }
        return result;
    }
}