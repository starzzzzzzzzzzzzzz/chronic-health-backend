package com.healthsystem.common.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/** 中文展示文案；不翻译协议字段、错误码、枚举或用户填写的业务内容。 */
public final class UserMessages {
    private static final Properties MESSAGES = load();
    private static final List<String> PREFIXES = List.of(
            "AI candidate unavailable: ", "Invalid UTC instant: ",
            "Missing or invalid ", "Unknown request field: ", "Invalid ");

    private UserMessages() {}

    public static String chinese(String message) {
        if (message == null) return null;
        String translated = MESSAGES.getProperty(message);
        if (translated != null) return translated;
        for (String prefix : PREFIXES) {
            if (message.startsWith(prefix)) {
                // 保留后缀中的字段名或错误码，供排错和前端定位。
                return MESSAGES.getProperty(prefix) + message.substring(prefix.length());
            }
        }
        return message;
    }

    private static Properties load() {
        Properties messages = new Properties();
        try (var stream = UserMessages.class.getResourceAsStream("/i18n/user-messages-zh-CN.properties")) {
            if (stream == null) throw new IllegalStateException("User message catalog missing");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                messages.load(reader);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("User message catalog unavailable", ex);
        }
        return messages;
    }
}
