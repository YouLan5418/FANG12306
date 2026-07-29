package org.opengoofy.index12306.user.serialize;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 证件号脱敏序列化：保留前 4 后 4。
 */
public class IdCardDesensitizationSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String idCard, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (StrUtil.isBlank(idCard) || idCard.length() < 8) {
            gen.writeString(idCard);
            return;
        }
        gen.writeString(idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4));
    }
}