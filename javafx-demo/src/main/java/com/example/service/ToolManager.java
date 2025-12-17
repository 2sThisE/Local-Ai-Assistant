package com.example.service;

import com.example.service.tools.ChDirTool;
import com.example.service.tools.GeminiTool;
import com.example.service.tools.GetUserInfoTool;
import com.example.service.tools.HelpTool;
import com.example.service.tools.ListFilesTool;
import com.example.service.tools.OpenFileTool;
import com.example.service.tools.PwdTool;
import com.example.service.tools.RunPythonTool;
import com.example.service.tools.RunShellTool;
import com.example.service.tools.Tool;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ToolManager {

    private final Map<String, Tool> tools = new HashMap<>();
    private File currentWorkingDirectory;

    public ToolManager() {
        // 초기 작업 디렉토리 설정 (프로젝트 루트 or 사용자 홈)
        this.currentWorkingDirectory = new File(System.getProperty("user.dir"));
        registerTools();
    }

    private void registerTools() {
        addTool(new RunShellTool(this));
        addTool(new RunPythonTool(this));
        addTool(new ListFilesTool(this));
        addTool(new OpenFileTool(this)); // 통합된 파일 도구
        addTool(new PwdTool(this));
        addTool(new ChDirTool(this));
        addTool(new GetUserInfoTool(this));
        addTool(new GeminiTool(this));
        addTool(new HelpTool(this));
    }

    private void addTool(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    // [NEW] 도구 목록 반환
    public Set<String> getRegisteredToolNames() {
        return tools.keySet();
    }

    // [NEW] 특정 도구 반환 (public으로 변경)
    public Tool getTool(String toolName) {
        return tools.get(toolName);
    }

    public ToolResult executeTool(String toolName, JsonObject params) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return new ToolResult(false, "알 수 없는 도구입니다: " + toolName, 
                "사용 가능한 도구인지 확인해주세요. help 도구를 사용하세요");
        }
        try {
            return tool.execute(params);
        } catch (Exception e) {
            e.printStackTrace();
            return new ToolResult(false, "도구 실행 중 예외 발생: " + e.getMessage(), 
                "파라미터 형식이나 시스템 상태를 확인해주세요.");
        }
    }

    public String getToolUsage(String toolName) {
        Tool tool = tools.get(toolName);
        return tool != null ? tool.getUsage() : "정보 없음";
    }

    // --- [State Management & Helpers] ---

    public File getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }

    public void setCurrentWorkingDirectory(File currentWorkingDirectory) {
        this.currentWorkingDirectory = currentWorkingDirectory;
    }

    public String getCurrentPath() {
        return currentWorkingDirectory.getAbsolutePath();
    }

    public File resolvePath(String pathStr) {
        if (".".equals(pathStr)) return currentWorkingDirectory;
        File file = new File(pathStr);
        if (!file.isAbsolute()) {
            file = new File(currentWorkingDirectory, pathStr);
        }
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }
}
