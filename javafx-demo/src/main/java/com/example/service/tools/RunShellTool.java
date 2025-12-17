package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class RunShellTool implements Tool {

    private final ToolManager toolManager;

    public RunShellTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "run_shell";
    }

    @Override
    public String getDescription() {
        return "시스템 쉘(CMD/PowerShell) 명령어를 실행합니다. 파일 관리는 가능한 전용 도구를 사용하세요.";
    }

    @Override
    public String getUsage() {
        return "{ \"command\": \"(실행할 쉘 명령어)\" }";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        if (!params.has("command")) {
            return new ToolResult(false, "파라미터 오류: 'command' 필드가 없습니다.", getUsage());
        }

        String command = params.get("command").getAsString();
        try {
            ProcessBuilder pb;
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            
            if (isWindows) {
                pb = new ProcessBuilder("powershell", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            
            // ToolManager가 관리하는 현재 디렉토리 사용
            pb.directory(toolManager.getCurrentWorkingDirectory());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            Charset charset = isWindows ? Charset.forName("MS949") : Charset.forName("UTF-8");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            
            String output = sb.toString();
            if (exitCode != 0) {
                return new ToolResult(false, "Exit Code " + exitCode + ":\n" + output, "명령어 스펠링이나 옵션을 확인해보세요.");
            }
            return new ToolResult(true, output);

        } catch (Exception e) {
            return new ToolResult(false, "쉘 실행 실패: " + e.getMessage());
        }
    }
}
