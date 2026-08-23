package com.foggyframework.analytics.runtime.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preserves exact JSON number tokens before Function value normalization. */
public final class AnalyticsFunctionParametersDeserializer
        extends StdDeserializer<Map<String, Object>> {

    public AnalyticsFunctionParametersDeserializer() {
        super(Map.class);
    }

    @Override
    public Map<String, Object> deserialize(
            JsonParser parser,
            DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            token = parser.nextToken();
        }
        if (token != JsonToken.START_OBJECT) {
            throw JsonMappingException.from(
                    parser,
                    "Analytics parameters must be a JSON object");
        }
        return object(parser, context);
    }

    private static Map<String, Object> object(
            JsonParser parser,
            DeserializationContext context) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String key = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            result.put(key, value(parser, context, valueToken));
        }
        return result;
    }

    private static List<Object> array(
            JsonParser parser,
            DeserializationContext context) throws IOException {
        List<Object> result = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            result.add(value(parser, context, parser.currentToken()));
        }
        return result;
    }

    private static Object value(
            JsonParser parser,
            DeserializationContext context,
            JsonToken token) throws IOException {
        return switch (token) {
            case START_OBJECT -> object(parser, context);
            case START_ARRAY -> array(parser, context);
            case VALUE_STRING -> parser.getText();
            case VALUE_NUMBER_INT -> parser.getBigIntegerValue();
            case VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            default -> throw context.weirdStringException(
                    token == null ? "null" : token.name(),
                    Object.class,
                    "Unsupported Analytics parameter JSON token");
        };
    }
}
