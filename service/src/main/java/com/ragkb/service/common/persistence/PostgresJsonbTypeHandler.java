package com.ragkb.service.common.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL {@code jsonb} 列的 MyBatis TypeHandler。
 *
 * <p>背景：MyBatis-Plus 自带的 {@code JacksonTypeHandler} 在写入时把对象序列化为
 * JSON 后用 {@link PreparedStatement#setString} 绑定为 {@code varchar} 参数；
 * PostgreSQL 的 {@code jsonb} 列拒绝 {@code varchar}→{@code jsonb} 的隐式转换，
 * 抛 {@code "column is of type jsonb but expression is of type character varying"}，
 * 导致 INSERT 直接 500。本 TypeHandler 用 {@link PGobject}（type=jsonb）绑定，
 * 让驱动按 jsonb 类型发送，服务端无需隐式转换。
 *
 * <p>读取保持与 {@code JacksonTypeHandler} 一致：把 JSON 文本反序列化为
 * {@code Object}（{@code List}/{@code Map}），由 MyBatis 按字段声明类型（如
 * {@code List<String>}）经反射赋值（泛型擦除，运行期不校验元素类型）。
 *
 * <p>用法：实体字段标注
 * {@code @TableField(typeHandler = PostgresJsonbTypeHandler.class)}，
 * 并在类上声明 {@code @TableName(autoResultMap = true)}。
 * 等价的替代方案是在 JDBC URL 追加 {@code ?stringtype=unspecified}（全局生效，
 * 但需改数据源配置；本 TypeHandler 为代码级修复，不依赖 URL 参数）。
 */
@MappedTypes(Object.class)
@MappedJdbcTypes({JdbcType.OTHER, JdbcType.VARCHAR})
public class PostgresJsonbTypeHandler extends BaseTypeHandler<Object> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        try {
            pgObject.setValue(OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("cannot serialize value to jsonb", e);
        }
        ps.setObject(i, pgObject);
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("cannot parse jsonb value", e);
        }
    }
}
