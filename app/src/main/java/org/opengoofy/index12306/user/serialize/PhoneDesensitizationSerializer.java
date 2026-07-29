package org.opengoofy.index12306.user.serialize;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 手机号脱敏序列化：138****1234。
 */
public class PhoneDesensitizationSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String phone, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            gen.writeString(phone);
            return;
        }
        gen.writeString(phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4));
    }
}