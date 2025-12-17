package com.example.service;

public class ToolResult {
    private final boolean success;
    private final String output;
    private final String guide; // AI를 위한 조언 (null일 수 있음)

    public ToolResult(boolean success, String output, String guide) {
        this.success = success;
        this.output = output;
        this.guide = guide;
    }

    public ToolResult(boolean success, String output) {
        this(success, output, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getGuide() {
        return guide;
    }
    
    // AI에게 보여줄 최종 메시지 포맷팅
    public String toAiMessage() {
        if (success) {
            return output;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("실패: ").append(output);
            if (guide != null && !guide.isEmpty()) {
                sb.append("\n[가이드]: ").append(guide);
            }
            return sb.toString();
        }
    }
}
