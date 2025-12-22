package com.example.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class ChatMessage {
    private String id;
    private String role; // "user", "model", "tool", "system" 등
    private String content; // 내용
    private LocalDateTime timestamp; // 생성 시간

    // 1. 새 메시지 생성용 (ID 자동 생성)
    public ChatMessage(String role, String content) {
        this.id = UUID.randomUUID().toString();
        this.role = role;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    // 2. DB 데이터 복원용 (ID, 시간 지정 가능)
    public ChatMessage(String id, String role, String content, LocalDateTime timestamp) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }
    // 필요시 ID 수정이 필요하다면 추가, 보통은 불변이 좋음
    public void setId(String id) { this.id = id; }

    public String getRole() {
        return role;
    }
    public void setRole(String role) { this.role = role; }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
