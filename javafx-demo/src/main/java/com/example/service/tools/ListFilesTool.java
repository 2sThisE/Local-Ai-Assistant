package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.Arrays;

public class ListFilesTool implements Tool {

    private final ToolManager toolManager;

    public ListFilesTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public String getDescription() {
        return "지정된 경로(또는 현재 경로)의 파일과 폴더 목록을 조회합니다. 쉘(ls/dir)보다 권장됩니다.";
    }

    @Override
    public String getUsage() {
        return "{ \"path\": \"(폴더 경로, 생략 가능)\" }";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        String pathStr = params.has("path") ? params.get("path").getAsString() : ".";
        
        try {
            File dir = toolManager.resolvePath(pathStr);
            if (!dir.exists() || !dir.isDirectory()) {
                return new ToolResult(false, "유효한 디렉토리가 아닙니다: " + dir.getAbsolutePath(), "존재하는 폴더 경로를 입력해주세요.");
            }

            File[] files = dir.listFiles();
            if (files == null) return new ToolResult(false, "목록을 불러올 수 없습니다 (Access Denied 가능성).");

            StringBuilder sb = new StringBuilder();
            sb.append("📂 경로: ").append(dir.getAbsolutePath()).append("\n\n");
            
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            for (File f : files) {
                String type = f.isDirectory() ? "[DIR] " : "[FILE]";
                String size = f.isDirectory() ? "" : String.format("(%d bytes)", f.length());
                sb.append(String.format("%-6s %s %s\n", type, f.getName(), size));
            }
            return new ToolResult(true, sb.toString());
        } catch (Exception e) {
            return new ToolResult(false, "목록 조회 실패: " + e.getMessage());
        }
    }
}
