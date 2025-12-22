package com.example.view; // 패키지 분리 권장

import com.example.controller.AiController; // 또는 인터페이스 사용
import com.example.service.ChatService; 
import com.example.util.HtmlUtil;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

public class ChatWebView {

    private final WebView webView;
    private final WebEngine webEngine;
    private ChatService chatService; // 로직 처리를 위해 필요
    private JavaBridge javaBridge;
    private Runnable onReady; // 로딩 완료 콜백

    public ChatWebView(WebView webView) {
        this.webView = webView;
        this.webEngine = webView.getEngine();
        initialize();
    }

    public void setChatService(ChatService chatService) {
        this.chatService = chatService;
    }
    
    public void setOnReady(Runnable onReady) {
        this.onReady = onReady;
    }

    private void initialize() {
        webEngine.setJavaScriptEnabled(true);
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                this.javaBridge = new JavaBridge();
                window.setMember("app", javaBridge);
                
                // 로딩 완료 콜백 실행
                if (onReady != null) {
                    onReady.run();
                }
            }
        });
        webEngine.loadContent(HtmlUtil.getSkeletonHtml());
    }

    // --- JS 호출 래퍼 메서드 ---

    public void appendUserMessage(String msg, String msgId) {
        runScript("appendUserMessage('" + escapeJs(msg) + "', '" + msgId + "')");
    }

    public void startAiMessage(String msgId) {
        runScript("startAiMessage('" + msgId + "')");
    }

    public void streamAiToken(String token) {
        runScript("streamAiToken('" + escapeJs(token) + "')");
    }

    public void finishAiMessage() {
        runScript("finishAiMessage()");
    }

    public void refreshMessage(String msgId, String html) {
        runScript("refreshMessage('" + msgId + "', '" + escapeJs(html) + "')");
    }

    public void showApprovalBox(String msg) {
        runScript("showApprovalBox('" + escapeJs(msg) + "')");
    }

    public void removeApprovalBox() {
        runScript("removeApprovalBox()");
    }
    
    public void updateApprovalResult(String output, boolean isSuccess) {
        runScript("updateApprovalResult('" + escapeJs(output) + "', " + isSuccess + ")");
    }

    public void showSpinner() { runScript("showLoadingSpinner()"); }
    public void hideSpinner() { runScript("hideLoadingSpinner()"); }
    
    public void appendSystemMessage(String msg) {
        runScript("appendSystemMessage('" + escapeJs(msg) + "')");
    }

    public void setApprovalRunning() {
        runScript("setApprovalRunning()");
    }

    private void runScript(String script) {
        Platform.runLater(() -> webEngine.executeScript(script));
    }

    private String escapeJs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
    }

    // --- JavaBridge 내부 클래스 ---
    public class JavaBridge {
        public void approve() {
            if (chatService != null) chatService.approveToolExecution();
        }

        public void reject() {
            if (chatService != null) chatService.rejectToolExecution();
        }

        public void cancel() {
            if (chatService != null) chatService.cancelToolExecution();
        }

        public void copyMessage(String id) {
            Platform.runLater(() -> {
                String content = chatService.getMessageContent(id);
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent cc = new ClipboardContent();
                cc.putString(content);
                clipboard.setContent(cc);
            });
        }

        public String getMessageContent(String id) {
            return chatService != null ? chatService.getMessageContent(id) : "";
        }

        public void updateMessageContent(String id, String newContent) {
            if (chatService != null) chatService.updateMessageContent(id, newContent);
        }
    }
}