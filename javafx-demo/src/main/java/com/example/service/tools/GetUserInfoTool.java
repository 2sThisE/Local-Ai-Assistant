package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;

public class GetUserInfoTool implements Tool {

    private final ToolManager toolManager;

    public GetUserInfoTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "get_user_info";
    }

    @Override
    public String getDescription() {
        return "시스템 사용자 이름, 홈 디렉토리, OS 정보 등을 조회합니다. 경로를 추측하기 전에 사용하세요.";
    }

    @Override
    public String getUsage() {
        return "{} (파라미터 없음)";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        try {
            String userName = System.getProperty("user.name");
            String userHome = System.getProperty("user.home");
            String osName = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
            String osVersion = System.getProperty("os.version");

            JsonObject info = new JsonObject();
            info.addProperty("user_name", userName);
            info.addProperty("user_home", userHome);
            info.addProperty("os_name", osName);
            info.addProperty("os_arch", osArch);
            info.addProperty("os_version", osVersion);
            info.addProperty("current_working_directory", toolManager.getCurrentPath());

            return new ToolResult(true, info.toString());
        } catch (Exception e) {
            return new ToolResult(false, "사용자 정보 조회 실패: " + e.getMessage());
        }
    }
}
