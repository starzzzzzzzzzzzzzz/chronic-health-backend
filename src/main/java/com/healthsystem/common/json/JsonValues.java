package com.healthsystem.common.json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
public final class JsonValues {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private JsonValues() {}
    public static JsonNode read(String text) { return text==null?JSON.nullNode():JSON.readTree(text); }
    public static String write(Object value) { return JSON.writeValueAsString(value); }
    public static JsonNode tree(Object value) { return JSON.valueToTree(value); }
}
