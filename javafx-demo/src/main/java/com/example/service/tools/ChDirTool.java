package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;
import java.io.File;

public class ChDirTool implements Tool {

    private final ToolManager toolManager;

    public ChDirTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "chdir";
    }

    @Override
    public String getDescription() {
        return "현재 작업 디렉토리(Working Directory)를 변경합니다. 이후 실행되는 쉘/파이썬 명령어는 이 폴더에서 실행됩니다.";
    }

    @Override
    public String getUsage() {
        return "{ \"path\": \"(이동할 경로)\" }";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        if (!params.has("path")) {
            return new ToolResult(false, "파라미터 오류: 'path' 필드가 없습니다.", getUsage());
        }

        String pathStr = params.get("path").getAsString();
        try {
            File newDir = toolManager.resolvePath(pathStr);
            if (newDir.exists() && newDir.isDirectory()) {
                toolManager.setCurrentWorkingDirectory(newDir); // 상태 변경
                return new ToolResult(true, "이동 완료: " + newDir.getAbsolutePath());
            }
 else {
                return new ToolResult(false, "유효하지 않은 디렉토리입니다: " + newDir.getAbsolutePath(), 
                    "경로에 오타가 없는지 확인하거나, 'list_files' 도구로 해당 경로가 실제로 존재하는지 확인해보세요.");
            }
        } catch (Exception e) {
            return new ToolResult(false, "이동 실패: " + e.getMessage());
        }
    }
}
