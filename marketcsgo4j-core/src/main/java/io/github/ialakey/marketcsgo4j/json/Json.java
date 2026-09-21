package io.github.ialakey.marketcsgo4j.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

/**
 * The one JSON configuration this client uses.
 *
 * <p>Two settings here are load-bearing rather than taste.
 *
 * <p>Unknown properties are ignored because this is a third-party API that adds
 * fields without warning, and a client that throws on an unrecognised key turns
 * someone else's harmless release into an outage in ours.
 *
 * <p>Floating point is read as {@link BigDecimal} because the only floats the
 * market sends are money. A balance of 123.45 parsed as a double and multiplied
 * by 100 is 12344.999999999998, and the difference between that and 12345 is a
 * withdrawal that gets refused for one kopeck.
 */
public final class Json {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("could not serialise " + value.getClass().getName(), e);
        }
    }

    /**
     * Reads the market's idea of a boolean.
     *
     * <p>Several endpoints answer with the string {@code "true"} rather than the
     * literal, and at least one answers with 1. Treating a string as truthy by
     * its presence would make {@code "false"} mean success.
     */
    public static boolean asBoolean(JsonNode node, boolean fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.intValue() != 0;
        }
        String text = node.asText();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        return fallback;
    }

    /** The market sends error codes as both numbers and strings. */
    public static Integer asIntegerOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.intValue();
        }
        try {
            return Integer.valueOf(node.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String asTextOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }
}
