package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class WriteFileTool implements Tool {

    private final ToolManager toolManager;

    public WriteFileTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "파일을 생성하거나 내용을 덮어씁니다. 기존 파일이 있다면 내용이 사라지니 주의하세요.";
    }

    @Override
    public String getUsage() {
        return "{ \"path\": \"(저장 경로)\", \"content\": \"(내용)\" }";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        if (!params.has("path") || !params.has("content")) {
            return new ToolResult(false, "파라미터 오류: 'path'와 'content' 필드가 필요합니다.", getUsage());
        }

        String pathStr = params.get("path").getAsString();
        String content = params.get("content").getAsString();
        try {
            File file = toolManager.resolvePath(pathStr);
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            Files.writeString(file.toPath(), content);
            return new ToolResult(true, "저장 완료: " + file.getAbsolutePath());
        } catch (IOException e) {
            return new ToolResult(false, "파일 쓰기 실패: " + e.getMessage(), "경로가 올바른지, 쓰기 권한이 있는지 확인하세요.");
        }
    }
}
