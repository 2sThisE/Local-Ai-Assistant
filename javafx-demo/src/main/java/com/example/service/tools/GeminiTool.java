package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class GeminiTool implements Tool {

    private final ToolManager toolManager;

    public GeminiTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "gemini";
    }

    @Override
    public String getDescription() {
        return "상위 AI(Gemini)에게 도움을 요청합니다. 문제 해결이 어렵거나 조언이 필요할 때 사용하세요.";
    }

    @Override
    public String getUsage() {
        return "{ \"prompt\": \"(질문 내용)\" }";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        if (!params.has("prompt")) {
            return new ToolResult(false, "파라미터 오류: 'prompt' 필드가 없습니다.", getUsage());
        }

        String prompt = params.get("prompt").getAsString();
        String safePrompt = prompt.replace("\"", "\\\"");
        
        // 기존 쉘 실행 로직 재사용 (RunShellTool과 중복되지만, 일단 독립적으로 구현)
        // 실제로는 Gemini API를 호출해야 하지만, 현재는 쉘 커맨드(gemini)를 사용하는 구조
        String command = "gemini \"" + safePrompt + "\"";
        
        try {
            ProcessBuilder pb;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb = new ProcessBuilder("powershell", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            
            pb.directory(toolManager.getCurrentWorkingDirectory());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            Charset charset = Charset.forName("UTF-8");
                              
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            
            String output = sb.toString();
            if (exitCode != 0) {
                return new ToolResult(false, "Gemini 호출 실패 (Exit Code " + exitCode + "):\n" + output);
            }
            return new ToolResult(true, output);

        } catch (Exception e) {
            return new ToolResult(false, "Gemini 실행 오류: " + e.getMessage());
        }
    }
}
