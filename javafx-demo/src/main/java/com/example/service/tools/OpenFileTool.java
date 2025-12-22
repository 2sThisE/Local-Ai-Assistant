package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class OpenFileTool implements Tool {

    private final ToolManager toolManager;

    public OpenFileTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "open_file";
    }

    @Override
    public String getDescription() {
        return "파일을 다양한 모드(읽기/쓰기/바이너리)로 엽니다. (통합 파일 관리 도구)";
    }

    @Override
    public String getUsage() {
        return "{ \"path\": \"파일경로\", \"mode\": \"r|w|rb|wb\", \"content\": \"(쓰기 시 내용)\" }";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        if (!params.has("path") || !params.has("mode")) {
            return new ToolResult(false, "파라미터 오류: 'path'와 'mode'는 필수입니다.", getUsage());
        }

        String pathStr = params.get("path").getAsString();
        String mode = params.get("mode").getAsString().toLowerCase();
        
        // 경로 처리 (상대 경로는 CWD 기준)
        File file = new File(pathStr);
        if (!file.isAbsolute()) {
            file = new File(toolManager.getCurrentWorkingDirectory(), pathStr);
        }
        Path path = file.toPath();

        try {
            switch (mode) {
                case "r": // 텍스트 읽기
                    if (!Files.exists(path)) return new ToolResult(false, "파일이 존재하지 않습니다: '" + pathStr+"'\nchdir툴을 이용하여 작업 디렉토리를 변경하거나 절대경로를 사용하세요");
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    // 너무 긴 파일은 앞부분만 읽기 (옵션) - 현재는 전체 읽기
                    return new ToolResult(true, content);

                case "w": // 텍스트 쓰기
                    if (!params.has("content")) return new ToolResult(false, "쓰기 모드(w)에는 'content'가 필요합니다.");
                    String textToWrite = params.get("content").getAsString();
                    Files.writeString(path, textToWrite, StandardCharsets.UTF_8);
                    return new ToolResult(true, "파일 저장 완료: " + pathStr);

                case "rb": // 바이너리 읽기 (Base64 반환)
                    if (!Files.exists(path)) new ToolResult(false, "파일이 존재하지 않습니다: '" + pathStr+"'\nchdir툴을 이용하여 작업 디렉토리를 변경하거나 절대경로를 사용하세요");
                    byte[] fileBytes = Files.readAllBytes(path);
                    String base64Content = Base64.getEncoder().encodeToString(fileBytes);
                    return new ToolResult(true, base64Content);

                case "wb": // 바이너리 쓰기 (Base64 디코딩 후 저장)
                    if (!params.has("content")) return new ToolResult(false, "바이너리 쓰기 모드(wb)에는 'content'(Base64)가 필요합니다.");
                    String base64ToWrite = params.get("content").getAsString();
                    byte[] decodedBytes = Base64.getDecoder().decode(base64ToWrite);
                    Files.write(path, decodedBytes);
                    return new ToolResult(true, "바이너리 파일 저장 완료: " + pathStr);

                default:
                    return new ToolResult(false, "지원하지 않는 모드입니다: " + mode, "가능한 모드: r, w, rb, wb");
            }
        } catch (Exception e) {
            return new ToolResult(false, "파일 작업 실패 (" + mode + "): " + e.getMessage());
        }
    }
}
