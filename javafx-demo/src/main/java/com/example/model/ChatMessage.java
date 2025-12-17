package com.example.model;

import java.util.UUID;

public class ChatMessage {
    private final String id;
    private final String role; // "user" or "ai"
    private String content; // Raw text

    public ChatMessage(String role, String content) {
        this.id = UUID.randomUUID().toString();
        this.role = role;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
