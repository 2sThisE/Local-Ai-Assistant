package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class RunPythonTool implements Tool {

    private final ToolManager toolManager;

    public RunPythonTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "run_python";
    }

    @Override
    public String getDescription() {
        return "파이썬 코드를 실행합니다. 복잡한 계산이나 데이터 처리에 적합합니다.";
    }

    @Override
    public String getUsage() {
        return "{ \"code\": \"(실행할 파이썬 코드)\" }";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        if (!params.has("code")) {
            return new ToolResult(false, "파라미터 오류: 'code' 필드가 없습니다.", getUsage());
        }

        String code = params.get("code").getAsString();
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "-c", "import sys; exec(sys.stdin.read())");
            pb.directory(toolManager.getCurrentWorkingDirectory());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (java.io.OutputStream os = process.getOutputStream();
                 java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(os, Charset.forName("UTF-8"))) {
                writer.write(code);
                writer.flush();
            }

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
                 return new ToolResult(false, "Python Error (Exit Code " + exitCode + "):\n" + output, 
                     "코드에 문법 오류(SyntaxError)나 런타임 에러가 있습니다. print() 문을 추가해서 디버깅해보거나, 코드를 단순화해보세요.");
            }
            return new ToolResult(true, output);

        } catch (Exception e) {
            return new ToolResult(false, "파이썬 실행 실패: " + e.getMessage());
        }
    }
}
