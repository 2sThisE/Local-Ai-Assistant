package com.example.service;

import com.example.model.ChatMessage;
import com.example.util.HtmlUtil;
import com.example.view.ChatWebView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import javafx.application.Platform;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatService {

    private final PythonService pythonService = new PythonService();
    private final ToolManager toolManager = new ToolManager();
    private final Gson gson = new Gson();
    private final List<ChatMessage> messageHistory = new ArrayList<>();
    
    private final ChatWebView chatWebView;
    private final Consumer<Boolean> onRespondingStateChange; // 버튼 제어용 콜백

    private StringBuilder currentAiText = new StringBuilder();
    private ChatMessage currentAiMessage = null;
    private String pendingToolJson = null;
    
    private boolean isAiResponding = false;
    private boolean stopRequested = false;

    public ChatService(ChatWebView chatWebView, Consumer<Boolean> onRespondingStateChange) {
        this.chatWebView = chatWebView;
        this.onRespondingStateChange = onRespondingStateChange;
        startPythonService();
    }

    private void startPythonService() {
        pythonService.start(
            message -> { // System Message
                if (stopRequested) return;
                if ("[DONE]".equals(message)) {
                    finishAiMessage();
                } else {
                    if (isAiResponding) finishAiMessage();
                    chatWebView.appendSystemMessage(message);
                }
            },
            token -> { // Streaming Token
                if (stopRequested) return;
                
                if ("[Thinking]".equals(token)) {
                    chatWebView.showSpinner();
                    updateState(true);
                    return;
                }

                if (!isAiResponding) {
                    chatWebView.hideSpinner();
                    currentAiMessage = new ChatMessage("ai", "");
                    messageHistory.add(currentAiMessage);
                    chatWebView.startAiMessage(currentAiMessage.getId());
                    isAiResponding = true;
                    currentAiText.setLength(0);
                }

                if (currentAiMessage == null) return;

                currentAiText.append(token);
                currentAiMessage.setContent(currentAiText.toString());
                chatWebView.streamAiToken(token);
            },
            this::handleToolRequest // Tool Request
        );
    }

    public void sendMessage(String msg) {
        if (msg.trim().isEmpty()) return;
        
        stopRequested = false;
        ChatMessage userMessage = new ChatMessage("user", msg);
        messageHistory.add(userMessage);
        
        chatWebView.appendUserMessage(msg, userMessage.getId());
        updateState(true); // 버튼: 전송 -> 중지
        isAiResponding = false; 
        
        pythonService.sendMessage(msg);
    }

    public void stopGeneration() {
        stopRequested = true;
        isAiResponding = false;
        finishAiMessage();
        chatWebView.hideSpinner();
        chatWebView.appendSystemMessage("⛔ 사용자에 의해 중단되었습니다.");
        updateState(false);
    }

    private void finishAiMessage() {
        if (isAiResponding) {
            renderAndCheckTool(currentAiMessage, currentAiText.toString());
            chatWebView.finishAiMessage();
            isAiResponding = false;
            currentAiText.setLength(0);
            currentAiMessage = null;
            chatWebView.hideSpinner();
            updateState(false);
        }
    }
    
    private void updateState(boolean isResponding) {
        if (onRespondingStateChange != null) {
            Platform.runLater(() -> onRespondingStateChange.accept(isResponding));
        }
    }

    // --- 도구 승인/거절/취소 로직 ---

    public void approveToolExecution() {
        if (pendingToolJson != null) {
            chatWebView.setApprovalRunning();
            handleToolRequest(pendingToolJson);
            pendingToolJson = null;
        }
    }

    public void rejectToolExecution() {
         handleToolCancelOrReject(false);
    }

    public void cancelToolExecution() {
        handleToolCancelOrReject(true);
    }

    private void handleToolCancelOrReject(boolean isCancel) {
        if (pendingToolJson == null) return;
        
        ChatMessage target = getCurrentOrLastAiMessage();
        if (target != null) {
            String status = isCancel ? "취소됨" : "거절됨";
            String color = isCancel ? "#757575" : "#FF5252";
            String msg = isCancel ? "❌ 취소했습니다." : "🚫 거절했습니다.";
            
            String html = String.format(
                "\n\n<div class='approval-container' style='border-color: %s;'>" +
                "<div class='approval-content'><b>[%s]</b> %s</div></div>", 
                color, status, msg
            );
            renderAndCheckTool(target, target.getContent() + html);
            
            if (!isCancel) { 
                // 거절은 AI에게 알려줌
                JsonObject result = new JsonObject();
                result.addProperty("status", "Rejected");
                result.addProperty("output", "User rejected.");
                pythonService.sendToolResult(gson.toJson(result));
            } else {
                // 취소는 그냥 끝냄
                updateState(false);
            }
        }
        pendingToolJson = null;
    }

    // --- 렌더링 및 도구 감지 (가장 중요한 부분) ---

    public void renderAndCheckTool(ChatMessage message, String fullText) {
        if (message == null) return;
        message.setContent(fullText);

        String toolRequestPrefix = "TOOL_REQUEST:";
        String jsonPart = null;
        
        String userTextPart = fullText;
        String resultHtmlPart = "";
        
        // 결과 HTML 분리
        int resultIdx = fullText.indexOf("<div class='approval-container'");
        if (resultIdx != -1) {
            userTextPart = fullText.substring(0, resultIdx);
            resultHtmlPart = fullText.substring(resultIdx);
        }

        // 도구 요청 JSON 추출 (기존 정교한 로직 유지)
        int toolIdx = userTextPart.indexOf(toolRequestPrefix);
        if (toolIdx != -1) {
            String tempPart = userTextPart.substring(toolIdx);
            int jsonStart = tempPart.indexOf("{");
            int jsonEnd = -1;
            
            if (jsonStart != -1) {
                int braceCount = 0;
                boolean inString = false;
                boolean escape = false;
                
                for (int i = jsonStart; i < tempPart.length(); i++) {
                    char c = tempPart.charAt(i);
                    if (escape) { escape = false; continue; }
                    if (c == '\\') { escape = true; continue; }
                    if (c == '"') { inString = !inString; continue; }
                    if (!inString) {
                        if (c == '{') braceCount++;
                        else if (c == '}') {
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
            if (jsonEnd != -1) {
                jsonPart = tempPart.substring(jsonStart, extractEnd).trim();
                
                // 원본 텍스트에서 JSON 제거
                String beforeTool = userTextPart.substring(0, toolIdx);
                String afterTool = tempPart.substring(extractEnd);
                
                // 마크다운 코드블록 제거
                if (beforeTool.trim().endsWith("```") && afterTool.trim().startsWith("```")) {
                    int lastBacktick = beforeTool.lastIndexOf("```");
                    if (lastBacktick != -1) beforeTool = beforeTool.substring(0, lastBacktick);
                    int firstBacktick = afterTool.indexOf("```");
                    if (firstBacktick != -1) afterTool = afterTool.substring(firstBacktick + 3);
                }
                userTextPart = beforeTool + afterTool;
            }
        }

        String renderedMarkdown = HtmlUtil.markdownToHtml(userTextPart);
        chatWebView.refreshMessage(message.getId(), renderedMarkdown + resultHtmlPart);

        // 승인 박스 요청
        if (jsonPart != null && resultHtmlPart.isEmpty()) {
            requestUserPermission(jsonPart);
        } else {
            if ((this.pendingToolJson != null || !resultHtmlPart.isEmpty()) && resultHtmlPart.isEmpty()) {
                this.pendingToolJson = null;
                chatWebView.removeApprovalBox();
            }
        }
    }

    private void requestUserPermission(String jsonStr) {
        try {
            int startIdx = jsonStr.indexOf("{");
            int endIdx = jsonStr.lastIndexOf("}");
            String cleanJson = jsonStr.substring(startIdx, endIdx + 1);
            
            JsonObject request = JsonParser.parseString(cleanJson).getAsJsonObject();
            String toolName = request.has("tool") ? request.get("tool").getAsString() : "unknown";
            String params = request.has("params") ? request.get("params").toString() : "{}";
            
            this.pendingToolJson = jsonStr;
            String msg = "<b>[" + toolName + "]</b><br>" + params;
            chatWebView.showApprovalBox(msg);

        } catch (Exception e) {
            chatWebView.appendSystemMessage("❌ 도구 요청 해석 실패");
        }
    }

    // --- 도구 실행 (백그라운드) ---

    private void handleToolRequest(String jsonStr) {
        new Thread(() -> {
            try {
                int startIdx = jsonStr.indexOf("{");
                int endIdx = jsonStr.lastIndexOf("}");
                String cleanJson = jsonStr.substring(startIdx, endIdx + 1);
                
                JsonReader reader = new JsonReader(new StringReader(cleanJson));
                reader.setLenient(true);
                JsonObject request = JsonParser.parseReader(reader).getAsJsonObject();
                
                String toolName = request.has("tool") ? request.get("tool").getAsString() : "unknown";
                JsonObject params = null;
                ToolResult resultObj;

                if (request.has("params") && request.get("params").isJsonObject()) {
                    params = request.getAsJsonObject("params");
                    resultObj = toolManager.executeTool(toolName, params);
                } else {
                    String usage = toolManager.getToolUsage(toolName);
                    resultObj = new ToolResult(false, "파라미터 오류", "올바른 사용법: " + usage);
                }
                
                boolean finalSuccess = resultObj.isSuccess();
                String finalOutput = resultObj.toAiMessage();

                JsonObject result = new JsonObject();
                result.addProperty("status", finalSuccess ? "success" : "error");
                result.addProperty("output", finalOutput);
                pythonService.sendToolResult(gson.toJson(result));

                Platform.runLater(() -> {
                    ChatMessage target = getCurrentOrLastAiMessage();
                    if (target != null) {
                        String oldContent = target.getContent();
                        String statusIcon = finalSuccess ? "✅" : "❌";
                        String statusColor = finalSuccess ? "#00FF00" : "#FF5252";
                        String safeOutput = finalOutput.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                        
                        String appendContent = "\n\n" +
                                "<div class='approval-container' style='border-color: " + statusColor + ";'>" +
                                    "<div class='approval-content'><b>[" + toolName + "]</b> " + statusIcon + "</div>" +
                                    "<div class='approval-result' style='color: " + statusColor + ";'>" + safeOutput + "</div>" +
                                "</div>";
                        
                        if (!oldContent.contains(finalOutput)) {
                             renderAndCheckTool(target, oldContent + appendContent);
                        }
                    } else {
                        chatWebView.updateApprovalResult(finalOutput, finalSuccess);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                JsonObject errorResult = new JsonObject();
                errorResult.addProperty("status", "error");
                errorResult.addProperty("output", "Java Error: " + e.getMessage());
                pythonService.sendToolResult(gson.toJson(errorResult));
            }
        }).start();
    }

    public String getMessageContent(String id) {
        return messageHistory.stream().filter(m -> m.getId().equals(id))
                .findFirst().map(ChatMessage::getContent).orElse("");
    }

    public void updateMessageContent(String id, String content) {
        messageHistory.stream().filter(m -> m.getId().equals(id))
                .findFirst().ifPresent(m -> renderAndCheckTool(m, content));
    }

    private ChatMessage getCurrentOrLastAiMessage() {
        if (currentAiMessage != null) return currentAiMessage;
        if (!messageHistory.isEmpty()) {
            ChatMessage last = messageHistory.get(messageHistory.size() - 1);
            if ("ai".equals(last.getRole())) return last;
        }
        return null;
    }

    public boolean isResponding() { return isAiResponding || stopRequested; }
    public void shutdown() { pythonService.stop(); }
}