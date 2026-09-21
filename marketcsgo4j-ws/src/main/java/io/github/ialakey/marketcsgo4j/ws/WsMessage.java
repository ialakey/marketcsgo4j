package io.github.ialakey.marketcsgo4j.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ialakey.marketcsgo4j.json.Json;

/**
 * The Centrifugo JSON protocol, in the little of it this client needs.
 *
 * <p>Version 4 and later, which is what {@code wsprice.csgo.com} speaks: commands
 * are objects with a command name inside them, several may share one frame
 * separated by newlines, and an empty object both ways is the keepalive.
 */
final class WsMessage {

    private WsMessage() {
    }

    static String connect(int id, String token) {
        ObjectNode command = Json.mapper().createObjectNode();
        command.put("id", id);
        command.putObject("connect").put("token", token);
        return command.toString();
    }

    static String subscribe(int id, String channel) {
        ObjectNode command = Json.mapper().createObjectNode();
        command.put("id", id);
        command.putObject("subscribe").put("channel", channel);
        return command.toString();
    }

    /** The reply to the server's keepalive, which is an empty object both ways. */
    static String pong() {
        return "{}";
    }

    static boolean isPing(JsonNode frame) {
        return frame.isObject() && frame.isEmpty();
    }

    /** The publication carried by a push frame, or null when the frame is something else. */
    static JsonNode publicationData(JsonNode frame) {
        JsonNode push = frame.get("push");
        if (push == null) {
            return null;
        }
        JsonNode publication = push.get("pub");
        return publication == null ? null : publication.get("data");
    }

    static String pushChannel(JsonNode frame) {
        JsonNode push = frame.get("push");
        return push == null ? null : Json.asTextOrNull(push.get("channel"));
    }

    /** The error a reply carries, or null when the reply is a success. */
    static String errorOf(JsonNode frame) {
        JsonNode error = frame.get("error");
        if (error == null) {
            return null;
        }
        String message = Json.asTextOrNull(error.get("message"));
        Integer code = Json.asIntegerOrNull(error.get("code"));
        return (message == null ? "unknown error" : message) + (code == null ? "" : " (code " + code + ")");
    }
}
