package com.example.controller;

import com.example.model.ChatMessage;
import com.example.service.PythonService;
import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.example.util.HtmlUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject; 

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class AiController {

    @FXML
    private WebView webView;

    @FXML
    private TextField inputField;

    @FXML
    private Button sendButton; // [NEW] 버튼 제어

    private WebEngine webEngine;
    private final PythonService pythonService = new PythonService();
    private final ToolManager toolManager = new ToolManager(); 
    private boolean isAiResponding = false;
    private boolean stopRequested = false; // [NEW] 중단 요청 플래그
    private final Gson gson = new Gson();
    
    private StringBuilder currentAiText = new StringBuilder();

    // 대기 중인 도구 요청 JSON을 저장할 변수
    private String pendingToolJson = null;

    // 메시지 저장소 
    private final List<ChatMessage> messageHistory = new ArrayList<>();
    // 현재 생성 중인 AI 메시지 객체
    private ChatMessage currentAiMessage = null;

    // JavaBridge 객체가 GC되지 않도록 강력한 참조 유지
    private JavaBridge javaBridge;

    @FXML
    public void initialize() {
        webEngine = webView.getEngine();
        
        // 자바스크립트 활성화 (필수)
        webEngine.setJavaScriptEnabled(true);
        
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                // HTML(JS)에서 Java를 'app'이라는 이름으로 부를 수 있게 연결
                JSObject window = (JSObject) webEngine.executeScript("window");
                // 멤버 변수에 할당하여 GC 방지
                javaBridge = new JavaBridge();
                window.setMember("app", javaBridge);
                
                startPythonService();
            }
        });

        webEngine.loadContent(HtmlUtil.getSkeletonHtml());
    }

    // --- (PythonService 관련 코드는 기존과 동일) ---
    private void startPythonService() {
        pythonService.start(
            message -> Platform.runLater(() -> {
                if (stopRequested) return; // [NEW] 중단 요청 시 무시

                if ("[DONE]".equals(message)) {
                    finishAiMessage();
                } else {
                    if (isAiResponding) finishAiMessage();
                    webEngine.executeScript("appendSystemMessage('" + escapeJs(message) + "')");
                }
            }),
            token -> Platform.runLater(() -> {
                if (stopRequested) return; // [NEW] 중단 요청 시 무시

                // [NEW] Python의 생각 시작 신호 감지
                if ("[Thinking]".equals(token)) {
                    webEngine.executeScript("showLoadingSpinner()");
                    toggleButtonState(true);
                    return;
                }

                if (!isAiResponding) {
                    // [NEW] 첫 토큰 수신 시 스피너 제거 및 메시지 시작
                    webEngine.executeScript("hideLoadingSpinner()");
                    
                    // 새로운 AI 메시지 객체 생성 및 저장
                    currentAiMessage = new com.example.model.ChatMessage("ai", "");
                    messageHistory.add(currentAiMessage);
                    
                    // 화면에 AI 메시지 박스 시작 (ID 전달)
                    webEngine.executeScript("startAiMessage('" + currentAiMessage.getId() + "')");
                    
                    isAiResponding = true;
                    currentAiText.setLength(0);
                }
                
                // [NPE 방지] 만약 초기화에 실패했다면 토큰 무시
                if (currentAiMessage == null) return;

                currentAiText.append(token);
                // 실시간으로 원본 텍스트 업데이트
                currentAiMessage.setContent(currentAiText.toString());
                webEngine.executeScript("streamAiToken('" + escapeJs(token) + "')");
            }),
            toolRequestJson -> handleToolRequest(toolRequestJson) // 파이썬이 직접 보낼 때 대응
        );
    }
    
    // AI 메시지 종료 시 도구 요청 감지 및 처리
    private void finishAiMessage() {
        if (isAiResponding) {
            String fullText = currentAiText.toString();
            
            // 공통 렌더링 로직 호출
            renderAndCheckTool(currentAiMessage, fullText);
            
            webEngine.executeScript("finishAiMessage()");
            isAiResponding = false;
            currentAiText.setLength(0);
            currentAiMessage = null;
            
            // [NEW] 버튼 상태 복구 및 스피너 안전 제거
            toggleButtonState(false);
            webEngine.executeScript("hideLoadingSpinner()");
        }
    }

    // [NEW] 버튼 상태 토글 (전송 <-> 중지)
    private void toggleButtonState(boolean responding) {
        if (sendButton == null) return;
        
        if (responding) {
            sendButton.setText("중지");
            if (!sendButton.getStyleClass().contains("stop-button")) {
                sendButton.getStyleClass().add("stop-button");
            }
        } else {
            sendButton.setText("전송");
            sendButton.getStyleClass().remove("stop-button");
        }
    }

    // [NEW] 생성 중단 처리
    private void stopGeneration() {
        stopRequested = true;
        isAiResponding = false;
        
        // UI 정리
        finishAiMessage(); // 현재 메시지 마무리 (또는 버리기)
        webEngine.executeScript("hideLoadingSpinner()"); // [FIX] 스피너 확실히 제거
        webEngine.executeScript("appendSystemMessage('⛔ 사용자에 의해 중단되었습니다.')");
        
        toggleButtonState(false);
    }

    // 메시지 렌더링 및 도구 요청 파싱 공통 로직 (핵심 수정)
    private void renderAndCheckTool(ChatMessage message, String fullText) {
        if (message == null) return;

        // 최종 내용 업데이트
        message.setContent(fullText);

        String toolRequestPrefix = "TOOL_REQUEST:";
        String jsonPart = null;
        
        // 1. 텍스트 분리
        String userTextPart = fullText;
        String resultHtmlPart = "";
        
        // 결과 HTML이 있는지 확인 (approval-container 클래스로 판단)
        int resultIdx = fullText.indexOf("<div class='approval-container'");
        if (resultIdx != -1) {
            userTextPart = fullText.substring(0, resultIdx);
            resultHtmlPart = fullText.substring(resultIdx);
        }

        // 2. 도구 요청 JSON 추출
        int toolIdx = userTextPart.indexOf(toolRequestPrefix);
        if (toolIdx != -1) {
            // TOOL_REQUEST 부분 찾기
            String tempPart = userTextPart.substring(toolIdx);
            
            // [FIX] 괄호 카운팅을 통한 정교한 JSON 추출 로직
            int jsonStart = tempPart.indexOf("{");
            int jsonEnd = -1;
            
            if (jsonStart != -1) {
                int braceCount = 0;
                boolean inString = false;
                boolean escape = false;
                
                for (int i = jsonStart; i < tempPart.length(); i++) {
                    char c = tempPart.charAt(i);
                    
                    if (escape) {
                        escape = false;
                        continue;
                    }
                    
                    if (c == '\\') {
                        escape = true;
                        continue;
                    }
                    
                    if (c == '"') {
                        inString = !inString;
                        continue;
                    }
                    
                    if (!inString) {
                        if (c == '{') {
                            braceCount++;
                        } else if (c == '}') {
                            braceCount--;
                            if (braceCount == 0) {
                                jsonEnd = i;
                                break;
                            }
                        }
                    }
                }
            }
            
            int extractEnd = (jsonEnd != -1) ? jsonEnd + 1 : tempPart.length();
            
            // JSON이 완성되지 않았다면(닫는 괄호가 없다면) 아직 추출하지 않음 (스트리밍 중일 수 있음)
            if (jsonEnd != -1) {
                String rawJson = tempPart.substring(jsonStart, extractEnd).trim(); // jsonStart부터 추출
                jsonPart = rawJson;
                
                // [FIX] 원본 텍스트에서 JSON 부분 도려내기
                String beforeTool = userTextPart.substring(0, toolIdx);
                String afterTool = tempPart.substring(extractEnd); 

                // 만약 TOOL_REQUEST가 코드블록(```)으로 감싸져 있었다면, 앞뒤의 ```도 제거
                if (beforeTool.trim().endsWith("```") && afterTool.trim().startsWith("```")) {
                    int lastBacktick = beforeTool.lastIndexOf("```");
                    if (lastBacktick != -1) {
                        beforeTool = beforeTool.substring(0, lastBacktick);
                    }
                    
                    int firstBacktick = afterTool.indexOf("```");
                    if (firstBacktick != -1) {
                        afterTool = afterTool.substring(firstBacktick + 3);
                    }
                }
                userTextPart = beforeTool + afterTool;
            }
        }

        // 3. 화면 업데이트
        String renderedMarkdown = HtmlUtil.markdownToHtml(userTextPart);
        String finalHtml = renderedMarkdown + resultHtmlPart;
        
        webEngine.executeScript("refreshMessage('" + message.getId() + "', '" + escapeJs(finalHtml) + "')");

        // 4. 도구 요청 처리 (결과가 아직 없고, JSON이 있을 때만 승인 요청)
        if (jsonPart != null && resultHtmlPart.isEmpty()) {
            requestUserPermission(jsonPart);
        } else {
            if (this.pendingToolJson != null && resultHtmlPart.isEmpty()) {
                this.pendingToolJson = null;
                webEngine.executeScript("removeApprovalBox()"); 
            }
            if (!resultHtmlPart.isEmpty()) {
                this.pendingToolJson = null;
                webEngine.executeScript("removeApprovalBox()");
            }
        }
    }

    // 승인 박스 띄우기
    private void requestUserPermission(String jsonStr) {
        try {
            int startIdx = jsonStr.indexOf("{");
            int endIdx = jsonStr.lastIndexOf("}");
            
            if (startIdx == -1 || endIdx == -1) return;

            String cleanJson = jsonStr.substring(startIdx, endIdx + 1);
            JsonReader reader = new JsonReader(new StringReader(cleanJson));
            reader.setLenient(true);
            JsonObject request = JsonParser.parseReader(reader).getAsJsonObject();
            
            String toolName = request.has("tool") ? request.get("tool").getAsString() : "unknown";
            String params = request.has("params") ? request.get("params").toString() : "{}";
            
            this.pendingToolJson = jsonStr;
            String msg = "<b>[" + toolName + "]</b><br>" + params;
            webEngine.executeScript("showApprovalBox('" + escapeJs(msg) + "')");

        } catch (Exception e) {
            String errorMsg = "JSON 파싱 실패:<br>" + e.getMessage();
            webEngine.executeScript("appendSystemMessage('❌ 도구 요청을 해석할 수 없습니다.')");
            
            JsonObject errorResult = new JsonObject();
            errorResult.addProperty("status", "error");
            errorResult.addProperty("output", "JSON 파싱 실패: 형식을 확인해주세요. (" + e.getMessage() + ")");
            pythonService.sendToolResult(gson.toJson(errorResult));
        }
    }
    

    // [수정] 실제 도구 실행 (ToolManager 위임 + ToolResult 활용)
    private void handleToolRequest(String jsonStr) {
        // UI가 멈추지 않게 별도 스레드에서 실행
        new Thread(() -> {
            try {
                int startIdx = jsonStr.indexOf("{");
                int endIdx = jsonStr.lastIndexOf("}");
                String cleanJson = jsonStr.substring(startIdx, endIdx + 1);
                
                JsonReader reader = new JsonReader(new StringReader(cleanJson));
                reader.setLenient(true);
                JsonObject request = JsonParser.parseReader(reader).getAsJsonObject();
                
                String toolName = request.has("tool") ? request.get("tool").getAsString() : "unknown";
                
                // [FIX] params 타입 검사 및 안전한 처리
                JsonObject params = null;
                ToolResult resultObj;

                if (request.has("params") && request.get("params").isJsonObject()) {
                    params = request.getAsJsonObject("params");
                    resultObj = toolManager.executeTool(toolName, params);
                } else {
                    // params가 없거나 객체가 아니면 에러 처리
                    String usage = toolManager.getToolUsage(toolName);
                    String guide = "파라미터 형식이 잘못되었습니다. 'params'는 반드시 JSON 객체 { ... } 형태여야 합니다.\n" +
                                   "올바른 사용법: " + usage;
                    resultObj = new ToolResult(false, "잘못된 도구 호출 (파라미터 오류)", guide);
                }
                
                boolean finalSuccess = resultObj.isSuccess();
                String finalOutput = resultObj.toAiMessage(); // 가이드 포함

                // 1. 결과 JSON 생성 (Python 전송용)
                JsonObject result = new JsonObject();
                result.addProperty("status", finalSuccess ? "success" : "error");
                result.addProperty("output", finalOutput);
                pythonService.sendToolResult(gson.toJson(result));

                // 2. 화면 갱신
                Platform.runLater(() -> {
                    ChatMessage targetMessage = currentAiMessage;
                    if (targetMessage == null && !messageHistory.isEmpty()) {
                        ChatMessage lastMsg = messageHistory.get(messageHistory.size() - 1);
                        if ("ai".equals(lastMsg.getRole())) {
                            targetMessage = lastMsg;
                        }
                    }

                    if (targetMessage != null) {
                        String oldContent = targetMessage.getContent();
                        String statusIcon = finalSuccess ? "✅" : "❌";
                        String statusColor = finalSuccess ? "#00FF00" : "#FF5252";
                        String safeOutput = finalOutput.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                        
                        // 결과 박스 HTML 생성
                        String appendContent = "\n\n" +
                                "<div class='approval-container' style='border-color: " + statusColor + ";'>" +
                                    "<div class='approval-content'>" +
                                        "<b>[" + toolName + "]</b> " + statusIcon +
                                    "</div>" +
                                    "<div class='approval-result' style='color: " + statusColor + ";'>" +
                                        safeOutput +
                                    "</div>" +
                                "</div>";
                        
                        if (!oldContent.contains(finalOutput)) {
                            String newContent = oldContent + appendContent;
                            renderAndCheckTool(targetMessage, newContent);
                        }
                    }
                    else {
                         String safeOutput = escapeJs(finalOutput); 
                         webEngine.executeScript("updateApprovalResult('" + safeOutput + "', " + finalSuccess + ")");
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                JsonObject errorResult = new JsonObject();
                errorResult.addProperty("status", "error");
                errorResult.addProperty("output", "Java 오류: " + e.getMessage());
                pythonService.sendToolResult(gson.toJson(errorResult));
            }
        }).start();
    }

    @FXML
    protected void onSendMessage() {
        // [NEW] 중단 요청 처리
        if (isAiResponding) { // 여기는 실제로 응답 중일 때만 들어옴 (true일 때)
            stopGeneration();
            return;
        }
        
        // 버튼이 '중지' 상태이지만 isAiResponding이 false인 경우 (대기 상태 등)
        // -> 이 경우도 중단으로 처리해야 함 (스피너만 돌고 있는 상태)
        if (stopRequested) {
             stopGeneration();
             return;
        }

        String msg = inputField.getText();
        if (msg.trim().isEmpty()) return;

        // 새로운 메시지 시작 전 중단 플래그 초기화
        stopRequested = false;

        ChatMessage userMessage = new ChatMessage("user", msg);
        messageHistory.add(userMessage);

        webEngine.executeScript("appendUserMessage('" + escapeJs(msg) + "', '" + userMessage.getId() + "')");
        
        // [NEW] 상태 변경: 응답 중은 아니지만(false), 스피너는 Python 신호 대기
        isAiResponding = false; 
        toggleButtonState(true);
        // webEngine.executeScript("showLoadingSpinner()"); // [REMOVED] Python [Thinking] 신호로 대체

        pythonService.sendMessage(msg);
        inputField.clear();
    }

    private String escapeJs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
    }

    public void shutdown() {
        pythonService.stop();
    }

    // JavaBridge (그대로 유지)
    public class JavaBridge {
        public void approve() {
            Platform.runLater(() -> {
                if (pendingToolJson != null) {
                    webEngine.executeScript("setApprovalRunning()");
                    handleToolRequest(pendingToolJson);
                    pendingToolJson = null;
                }
            });
        }

        public void reject() {
            Platform.runLater(() -> {
                if (pendingToolJson != null) {
                    ChatMessage targetMessage = currentAiMessage;
                    if (targetMessage == null && !messageHistory.isEmpty()) {
                        ChatMessage lastMsg = messageHistory.get(messageHistory.size() - 1);
                        if ("ai".equals(lastMsg.getRole())) targetMessage = lastMsg;
                    }

                    if (targetMessage != null) {
                        String oldContent = targetMessage.getContent();
                        String appendContent = "\n\n" +
                                "<div class='approval-container' style='border-color: #FF5252;'>" +
                                    "<div class='approval-content'>" +
                                        "<b>[거절됨]</b> 🚫 사용자가 도구 실행을 거절했습니다." +
                                    "</div>" +
                                "</div>";
                        renderAndCheckTool(targetMessage, oldContent + appendContent);
                    } else {
                        webEngine.executeScript("removeApprovalBox()");
                        webEngine.executeScript("appendSystemMessage('🚫 실행을 거절했습니다.')");
                    }
                    
                    JsonObject result = new JsonObject();
                    result.addProperty("status", "Rejected");
                    result.addProperty("output", "user cancelled the operation.");
                    pythonService.sendToolResult(gson.toJson(result));
                    
                    pendingToolJson = null;
                }
            });
        }

        // [NEW] 아예 취소 (AI에게 데이터 전송 X)
        public void cancel() {
            Platform.runLater(() -> {
                if (pendingToolJson != null) {
                    webEngine.executeScript("removeApprovalBox()");
                    webEngine.executeScript("appendSystemMessage('❌ 도구 실행을 취소했습니다. (AI에게 전송되지 않음)')");
                    
                    pendingToolJson = null;
                    toggleButtonState(false);
                }
            });
        }

        public void copyMessage(String id) {
            Platform.runLater(() -> {
                messageHistory.stream()
                    .filter(m -> m.getId().equals(id))
                    .findFirst()
                    .ifPresent(m -> {
                        Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                        ClipboardContent content = new javafx.scene.input.ClipboardContent();
                        content.putString(m.getContent());
                        clipboard.setContent(content);
                    });
            });
        }

        public String getMessageContent(String id) {
            return messageHistory.stream()
                    .filter(m -> m.getId().equals(id))
                    .findFirst()
                    .map(ChatMessage::getContent)
                    .orElse("");
        }

        public void updateMessageContent(String id, String newContent) {
            Platform.runLater(() -> {
                messageHistory.stream()
                    .filter(m -> m.getId().equals(id))
                    .findFirst()
                    .ifPresent(m -> renderAndCheckTool(m, newContent));
            });
        }
    }
}