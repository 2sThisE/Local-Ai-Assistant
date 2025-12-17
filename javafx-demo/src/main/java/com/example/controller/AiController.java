package com.example.controller;

import com.example.service.ChatService;
import com.example.view.ChatWebView;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

public class AiController {

    @FXML private VBox settingsOverlay;
    @FXML private WebView webView;
    @FXML private TextField inputField;
    @FXML private SettingsController settingsOverlayController;
    @FXML private Button sendButton;
    @FXML private TextField modelNameField;

    // 분리된 클래스 사용
    private ChatWebView chatWebView;
    private ChatService chatService;

    @FXML
    public void initialize() {
        // 1. 화면(View) 객체 생성
        chatWebView = new ChatWebView(webView);

        // 2. 서비스(Logic) 객체 생성
        // (두 번째 인자는 버튼 상태를 바꾸는 콜백 함수입니다)
        chatService = new ChatService(chatWebView, this::toggleButtonState);
        
        // 3. View와 Service 연결 (서로 호출해야 함)
        chatWebView.setChatService(chatService);
        
        settingsOverlay.setVisible(false);
        
    }

    @FXML
    protected void onSendMessage() {
        // 이미 응답 중이면 '중지' 요청
        if (chatService.isResponding()) {
            chatService.stopGeneration();
            return;
        }

        String msg = inputField.getText();
        if (msg == null || msg.trim().isEmpty()) return;

        chatService.sendMessage(msg);
        inputField.clear();
    }

    @FXML
    protected void toggleSettings() {
        if (settingsOverlayController != null) {
            settingsOverlayController.setVisible(!settingsOverlayController.isVisible());
        }
    }

    // ChatService에서 호출하여 버튼 모양을 바꿈
    private void toggleButtonState(boolean isResponding) {
        if (sendButton == null) return;
        
        if (isResponding) {
            sendButton.setText("중지");
            if (!sendButton.getStyleClass().contains("stop-button")) {
                sendButton.getStyleClass().add("stop-button");
            }
        } else {
            sendButton.setText("전송");
            sendButton.getStyleClass().remove("stop-button");
        }
    }

    public void shutdown() {
        if (chatService != null) chatService.shutdown();
    }
}