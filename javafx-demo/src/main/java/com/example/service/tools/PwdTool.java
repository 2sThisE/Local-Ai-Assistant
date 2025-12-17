package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;

public class PwdTool implements Tool {

    private final ToolManager toolManager;

    public PwdTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "pwd";
    }

    @Override
    public String getDescription() {
        return "현재 작업 디렉토리 경로를 확인합니다.";
    }

    @Override
    public String getUsage() {
        return "{} (파라미터 없음)";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        return new ToolResult(true, toolManager.getCurrentPath());
    }
}
