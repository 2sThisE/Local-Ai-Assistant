package com.example.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class PythonService {

    // 경로 설정
    private static final String PYTHON_SCRIPT_PATH = "config/python/test.py";
    private static final String PYTHON_EXE = "python";

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Thread listenerThread;
    private boolean isRunning = false;

    // 파이썬 프로세스 시작
    public void start(Consumer<String> onMessage, Consumer<String> onToken, Consumer<String> onToolRequest, Consumer<String> onSummaryResult) {
        try {
            ProcessBuilder pb = new ProcessBuilder(PYTHON_EXE, "-u", PYTHON_SCRIPT_PATH);
            pb.redirectErrorStream(true);
            process = pb.start();
            isRunning = true;
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            // 백그라운드에서 메시지 수신 대기
            listenerThread = new Thread(() -> {
                try {
                    String line;
                    while (isRunning && (line = reader.readLine()) != null) {

                        // 1. 스트리밍 토큰 (AI 답변)
                        if (line.startsWith("TOKEN:")) {
                            String token = line.substring(6).replace("[NEWLINE]", "\n");
                            // [NEW] 종료 신호 감지
                            if ("[DONE]".equals(token)) {
                                if (onMessage != null) onMessage.accept("[DONE]");
                            } else {
                                if (onToken != null) onToken.accept(token);
                            }
                        }
                        // 2. [NEW] 요약 결과 수신
                        else if (line.startsWith("SUMMARY_RESULT:")) {
                            String summary = line.substring(15).replace("[NEWLINE]", "\n");
                            if (onSummaryResult != null) onSummaryResult.accept(summary);
                        }
                        // 3. [NEW] 도구 사용 요청 (JSON)
                        else if (line.contains("TOOL_REQUEST:")) {
                            // "TOOL_REQUEST: { ... }" 형태에서 JSON만 추출
                            int idx = line.indexOf("TOOL_REQUEST:");
                            String json = line.substring(idx + 13).trim();
                            if (onToolRequest != null) onToolRequest.accept(json);
                        }
                        // 4. 일반 시스템 메시지
                        else {
                            String finalLine = line.replace("[NEWLINE]", "\n");
                            if (onMessage != null) onMessage.accept(finalLine);
                        }
                    }
                } catch (IOException e) {
                    if (isRunning) e.printStackTrace();
                }
            });
            listenerThread.setDaemon(true);
            listenerThread.start();

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("파이썬 프로세스를 시작할 수 없습니다.");
        }
    }

    // 일반 사용자 메시지 전송
    public void sendMessage(String message) {
        sendRaw(message);
    }

    // [NEW] 도구 실행 결과 전송
    public void sendToolResult(String resultJson) {
        // 파이썬이 "TOOL_RESULT: " 접두어로 인식하도록 약속됨
        sendRaw("TOOL_RESULT: " + resultJson);
    }

    // 내부 전송 로직
    private void sendRaw(String text) {
        if (writer != null && isRunning) {
            try {
                writer.write(text);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 프로세스 종료
    public void stop() {
        isRunning = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (process != null && process.isAlive()) process.destroy();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
