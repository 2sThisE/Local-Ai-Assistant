package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ReadFileTool implements Tool {

    private final ToolManager toolManager;

    public ReadFileTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "텍스트 파일의 내용을 읽어옵니다. 소스 코드 분석이나 로그 확인에 사용하세요.";
    }

    @Override
    public String getUsage() {
        return "{ \"path\": \"(읽을 파일 경로)\" }";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        if (!params.has("path")) {
            return new ToolResult(false, "파라미터 오류: 'path' 필드가 없습니다.", getUsage());
        }

        String pathStr = params.get("path").getAsString();
        try {
            File file = toolManager.resolvePath(pathStr);
            if (!file.exists()) {
                return new ToolResult(false, "파일이 존재하지 않습니다: " + file.getAbsolutePath(), 
                    "경로가 정확한지 확인하세요. 현재 디렉토리는 '" + toolManager.getCurrentPath() + "' 입니다. 'list_files'로 확인해보세요.");
            }
            if (!file.isFile()) {
                return new ToolResult(false, "파일이 아닙니다 (디렉토리일 수 있음): " + file.getAbsolutePath(), 
                    "이 경로는 폴더입니다. 내용을 보려면 'list_files'를 사용하세요.");
            }
            
            String content = Files.readString(file.toPath());
            return new ToolResult(true, content);
        } catch (IOException e) {
            return new ToolResult(false, "파일 읽기 실패: " + e.getMessage(), "파일이 다른 프로그램에서 사용 중이거나 읽기 권한이 없을 수 있습니다.");
        }
    }
}
