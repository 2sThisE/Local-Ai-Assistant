package com.example.service;

import com.example.model.ChatMessage;
import com.example.repository.ChatRepository;
import com.example.util.HtmlUtil;
import com.example.view.ChatWebView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import javafx.application.Platform;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatService {

    private final PythonService pythonService = new PythonService();
    private final ToolManager toolManager = new ToolManager();
    private final ChatRepository chatRepository = new ChatRepository(); // 리포지토리 추가
    private final Gson gson = new Gson();
    private final List<ChatMessage> messageHistory = new ArrayList<>();
    
    private final ChatWebView chatWebView;
    private final Consumer<Boolean> onRespondingStateChange; // 버튼 제어용 콜백

    private StringBuilder currentAiText = new StringBuilder();
    private ChatMessage currentAiMessage = null;
    private String pendingToolJson = null;
    
    private boolean isAiResponding = false;
    private boolean stopRequested = false;
    
    // [NEW] 요약 관련 변수
    private int messageSinceLastSummary = 0;
    private boolean isSummarizing = false;

    public ChatService(ChatWebView chatWebView, Consumer<Boolean> onRespondingStateChange) {
        this.chatWebView = chatWebView;
        this.onRespondingStateChange = onRespondingStateChange;
        
        // 초기화: WebView 로딩이 완료된 후 메시지 불러오기 (JS 에러 방지)
        this.chatWebView.setOnReady(this::loadRecentMessages);
        
        startPythonService();
    }
    
    private void loadRecentMessages() {
        // 1. 최근 메시지 로드 및 화면 표시
        List<ChatMessage> recent = chatRepository.findRecentMessages(20);
        messageHistory.addAll(recent);
        
        for (ChatMessage msg : recent) {
            if ("user".equals(msg.getRole())) {
                chatWebView.appendUserMessage(msg.getContent(), msg.getId());
            } else {
                chatWebView.startAiMessage(msg.getId());
                renderAndCheckTool(msg, msg.getContent());
            }
        }
        
        // 2. 마지막 요약 이후 메시지 개수 초기화
        messageSinceLastSummary = chatRepository.countMessagesAfterLastSummary();
        System.out.println("마지막 요약 이후 메시지 개수: " + messageSinceLastSummary);
        
        // 3. Python 모델에 기억 주입 (비동기로 실행하여 UI 블로킹 방지)
        new Thread(this::restoreHistoryToModel).start();
    }
    
    private void restoreHistoryToModel() {
        if (messageHistory.isEmpty()) return;

        try {
            com.google.gson.JsonArray historyArray = new com.google.gson.JsonArray();
            
            // 1. 장기 기억(최신 요약본) 및 마지막 대화 시간 정보 주입
            String lastSummary = chatRepository.getLastSummary();
            StringBuilder systemContext = new StringBuilder();
            
            if (lastSummary != null && !lastSummary.isEmpty()) {
                systemContext.append("Here is the summary of previous conversations:\n").append(lastSummary).append("\n\n");
            }
            
            // 마지막 대화 시간 확인
            if (!messageHistory.isEmpty()) {
                ChatMessage lastMsg = messageHistory.get(messageHistory.size() - 1);
                if (lastMsg.getTimestamp() != null) {
                    systemContext.append("[System Info: Last conversation ended at ")
                                 .append(lastMsg.getTimestamp().toString())
                                 .append("]");
                }
            }
            
            if (systemContext.length() > 0) {
                JsonObject contextObj = new JsonObject();
                contextObj.addProperty("role", "system");
                contextObj.addProperty("content", systemContext.toString());
                historyArray.add(contextObj);
            }
            
            // 2. 단기 기억(최근 대화) 추가
            for (ChatMessage msg : messageHistory) {
                JsonObject obj = new JsonObject();
                String role = msg.getRole();
                if ("ai".equals(role)) role = "assistant";
                else if ("tool".equals(role)) continue; 
                
                obj.addProperty("role", role);
                // HTML 태그 제거 및 툴 결과 포맷팅 적용
                obj.addProperty("content", cleanContentForModel(msg.getContent()));
                
                historyArray.add(obj);
            }
            
            String jsonStr = gson.toJson(historyArray);
            pythonService.sendMessage("RESTORE_AND_GREET:" + jsonStr);
            
        } catch (Exception e) {
            e.printStackTrace();
            chatWebView.appendSystemMessage("⚠️ 기억 복원 중 오류 발생");
        }
    }

    // [NEW] AI에게 보낼 텍스트 정제 (HTML 제거 및 툴 결과 변환)
    private String cleanContentForModel(String content) {
        if (content == null) return "";
        
        // 1. 툴 실행 결과 (approval-result) 추출 및 변환
        // <div class='approval-result'...>내용</div> -> TOOL_RESULT: 내용
        java.util.regex.Pattern resultPattern = java.util.regex.Pattern.compile(
            "<div class='approval-result'[^>]*>(.*?)</div>", 
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher matcher = resultPattern.matcher(content);
        
        StringBuffer sb = new StringBuffer();
        boolean toolResultFound = false;
        
        while (matcher.find()) {
            toolResultFound = true;
            String resultText = matcher.group(1);
            // HTML 엔티티 등 디코딩 필요시 추가 (일단 간단히 줄바꿈만 처리)
            // 여기서는 HTML 태그가 포함된 결과일 수 있으므로, 결과 텍스트 자체는 유지하되
            // 감싸고 있는 approval-container 전체를 날려야 함.
            
            // 하지만 matcher는 result만 찾으므로, 전체 컨테이너를 찾아서 교체하는 게 나음.
            // 따라서 전략 수정: approval-container 전체를 찾음.
        }
        
        // 2. approval-container 전체 덩어리를 찾아서 교체
        java.util.regex.Pattern containerPattern = java.util.regex.Pattern.compile(
            "<div class='approval-container'[^>]*>.*?<div class='approval-result'[^>]*>(.*?)</div>.*?</div>", 
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher containerMatcher = containerPattern.matcher(content);
        
        String processed = containerMatcher.replaceAll(mr -> {
            String result = mr.group(1);
            // 결과 내의 <br> 등을 줄바꿈으로 변경
            result = result.replaceAll("<br\\s*/?>", "\n");
            // HTML 엔티티 디코딩 (간단하게)
            result = result.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", "\"");
            return "\n[TOOL_RESULT: " + result.trim() + "]\n";
        });
        
        // 3. 나머지 자잘한 HTML 태그 제거 (마크다운은 유지)
        // 주의: 마크다운의 <코드블록> 같은 건 건드리면 안 됨.
        // 여기서는 툴 결과 변환이 주 목적이므로, 과도한 태그 제거는 생략하거나 신중하게 적용.
        // 일단 approval-container만 처리해도 충분함.
        
        return processed;
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

                if (!isAiResponding && !isSummarizing) { // 요약 중엔 무시
                    chatWebView.hideSpinner();
                    currentAiMessage = new ChatMessage("ai", "");
                    messageHistory.add(currentAiMessage);
                    
                    // AI 메시지 시작 시점에 일단 빈 내용으로 저장 (ID 확보)
                    chatRepository.save(currentAiMessage);
                    // 메시지 추가됨 -> 카운트 증가
                    messageSinceLastSummary++;
                    
                    chatWebView.startAiMessage(currentAiMessage.getId());
                    isAiResponding = true;
                    currentAiText.setLength(0);
                }

                if (currentAiMessage == null) return;

                currentAiText.append(token);
                currentAiMessage.setContent(currentAiText.toString());
                chatWebView.streamAiToken(token);
            },
            this::handleToolRequest, // Tool Request
            this::handleSummaryResult // [NEW] Summary Result Callback
        );
    }

    // [NEW] 요약 요청 로직
    private void checkAndSummarize() {
        if (messageSinceLastSummary >= 20 && !isSummarizing) {
            System.out.println("요약 트리거 발동! (쌓인 메시지: " + messageSinceLastSummary + ")");
            isSummarizing = true;
            updateState(true); // UI 잠금 (전송 버튼 비활성화 등)
            chatWebView.showSpinner(); // 요약 중임을 알림
            
            new Thread(() -> {
                try {
                    // 1. 요약할 데이터 준비 (최근 30개 + 이전 요약)
                    // offset은 0 (가장 오래된 것부터 가져오려면 정렬 기준 확인 필요)
                    // 여기서는 '요약되지 않은 메시지들'을 가져오는 게 아니라, 문맥 유지를 위해 최근 30개를 가져옴
                    List<ChatMessage> messages = chatRepository.findMessagesForSummary(0, 30); 
                    String prevSummary = chatRepository.getLastSummary();
                    
                    JsonObject request = new JsonObject();
                    request.addProperty("previous_summary", prevSummary);
                    
                    com.google.gson.JsonArray msgArray = new com.google.gson.JsonArray();
                    for (ChatMessage m : messages) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("role", m.getRole());
                        obj.addProperty("content", m.getContent()); // HTML 태그 포함될 수 있음 (텍스트만 추출하면 더 좋음)
                        msgArray.add(obj);
                    }
                    request.add("messages", msgArray);
                    
                    pythonService.sendMessage("SUMMARIZE:" + gson.toJson(request));
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        chatWebView.hideSpinner();
                        chatWebView.appendSystemMessage("⚠️ 요약 요청 실패");
                        isSummarizing = false;
                        updateState(false);
                    });
                }
            }).start();
        }
    }

    // [NEW] 요약 결과 처리
    private void handleSummaryResult(String summaryContent) {
        System.out.println("요약 완료: " + summaryContent);
        
        // DB 저장 (범위 설정이 중요하지만, 일단 현재 시점 기준으로 저장)
        // 정확한 start_msg_id, end_msg_id를 찾으려면 findMessagesForSummary의 결과를 참조해야 함.
        // 여기서는 편의상 가장 최근 메시지 ID를 end로 잡음.
        // TODO: 더 정교한 ID 매핑 필요
        
        String startId = "unknown";
        String endId = "unknown";
        LocalDateTime now = LocalDateTime.now();
        
        if (!messageHistory.isEmpty()) {
            endId = messageHistory.get(messageHistory.size() - 1).getId();
            // startId는 20개 전 메시지 ID... 계산 필요하지만 일단 endId와 동일하게 더미 처리
            startId = endId; 
        }
        
        chatRepository.saveSummary(summaryContent, startId, endId, now, now);
        
        // 상태 초기화
        messageSinceLastSummary = 0; // 카운트 리셋
        isSummarizing = false;
        
        Platform.runLater(() -> {
            chatWebView.hideSpinner();
            // chatWebView.appendSystemMessage("✅ 대화 내용이 요약되었습니다."); // 사용자에게 굳이 안 알려줘도 됨
            updateState(false);
        });
    }

    public void sendMessage(String msg) {
        if (msg.trim().isEmpty() || isSummarizing) return; // 요약 중엔 입력 차단
        
        stopRequested = false;
        ChatMessage userMessage = new ChatMessage("user", msg);
        messageHistory.add(userMessage);
        
        // 사용자 메시지 저장
        chatRepository.save(userMessage);
        // 메시지 추가됨 -> 카운트 증가
        messageSinceLastSummary++;
        
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
            
            // AI 답변 완료 시 최종 내용 DB 업데이트 (HTML 포함된 내용일 수 있음)
            if (currentAiMessage != null) {
                chatRepository.updateContent(currentAiMessage.getId(), currentAiMessage.getContent());
            }
            
            chatWebView.finishAiMessage();
            isAiResponding = false;
            currentAiText.setLength(0);
            currentAiMessage = null;
            chatWebView.hideSpinner();
            updateState(false);
            
            // [NEW] 응답 완료 후 요약 필요 여부 체크
            checkAndSummarize();
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
            
            // 거절/취소 결과도 DB 업데이트
            chatRepository.updateContent(target.getId(), target.getContent());
            
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
                             // 도구 실행 결과 추가 후 DB 업데이트!
                             chatRepository.updateContent(target.getId(), target.getContent());
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
                .findFirst().ifPresent(m -> {
                    renderAndCheckTool(m, content);
                    // DB에도 수정된 내용 반영!
                    chatRepository.updateContent(id, content);
                });
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