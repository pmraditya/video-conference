package com.example.videoconference;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class SignalingHandler extends TextWebSocketHandler {
    private ConcurrentHashMap<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String> userRooms = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("New connection: " + session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = JsonParser.parseString(message.getPayload()).getAsJsonObject();
        String type = jsonMessage.get("type").getAsString();

        switch (type) {
            case "join":
                handleJoin(session, jsonMessage);
                break;
            case "message":
                handleMessage(session, jsonMessage);
                break;
            case "leave":
                handleLeave(session);
                break;
        }
    }

    private void handleJoin(WebSocketSession session, JsonObject message) {
        String username = message.get("username").getAsString();
        String room = message.get("room").getAsString();

        userSessions.put(session.getId(), session);
        userRooms.put(session.getId(), room);

        rooms.putIfAbsent(room, new CopyOnWriteArraySet<>());
        rooms.get(room).add(session);

        JsonObject response = new JsonObject();
        response.addProperty("type", "system");
        response.addProperty("message", username + " has joined room: " + room);

        broadcastToRoom(room, response);
    }

    private void handleMessage(WebSocketSession sender, JsonObject message) {
        String senderId = sender.getId();
        String room = userRooms.get(senderId);
        if (room == null) return;

        JsonObject response = new JsonObject();
        response.addProperty("type", "message");
        response.addProperty("sender", senderId);
        response.addProperty("content", message.get("content").getAsString());

        broadcastToRoom(room, response);
    }

    private void handleLeave(WebSocketSession session) {
        String sessionId = session.getId();
        String room = userRooms.remove(sessionId);
        userSessions.remove(sessionId);

        if (room != null) {
            rooms.get(room).remove(session);
            if (rooms.get(room).isEmpty()) {
                rooms.remove(room); // Remove empty room
            }

            JsonObject response = new JsonObject();
            response.addProperty("type", "system");
            response.addProperty("message", "A user has left the room.");

            broadcastToRoom(room, response);
        }
    }

    private void broadcastToRoom(String room, JsonObject message) {
        if (!rooms.containsKey(room)) return;
        for (WebSocketSession session : rooms.get(room)) {
            try {
                session.sendMessage(new TextMessage(message.toString()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        handleLeave(session);
    }
}
