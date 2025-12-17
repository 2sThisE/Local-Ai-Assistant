package com.example.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.example.service.AppConfigService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class SettingsController {

    @FXML private VBox rootBox;
    @FXML private ComboBox<String> modelComboBox;

    // [NEW] 좌측 메뉴 리스트
    @FXML private ListView<String> menuList;

    // [NEW] 우측 패널들
    @FXML private VBox aiPanel;
    @FXML private VBox displayPanel;
    @FXML private VBox aboutPanel;

    @FXML
    public void initialize() {
        // 1. 메뉴 항목 추가
        menuList.getItems().addAll("AI 모델 설정", "화면 설정", "정보");

        // 2. 리스트 선택 이벤트 리스너
        menuList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            switchPanel(newVal);
        });

        // 3. 초기 선택값 설정
        menuList.getSelectionModel().select(0);
        AppConfigService config = AppConfigService.load();
        modelComboBox.setValue(config.getModelName());
        fetchOllamaModels();
    }

    private void fetchOllamaModels() {
        new Thread(() -> {
            try {
                // Ollama 로컬 API 엔드포인트
                URL url = new URL("http://localhost:11434/api/tags");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000); // 2초 안에 응답 없으면 포기

                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    // JSON 파싱
                    Gson gson = new Gson();
                    JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                    JsonArray models = json.getAsJsonArray("models");

                    List<String> modelNames = new ArrayList<>();
                    for (JsonElement model : models) {
                        // "name": "gemma2:9b" 형태
                        modelNames.add(model.getAsJsonObject().get("name").getAsString());
                    }

                    // UI 업데이트는 반드시 JavaFX 스레드에서
                    Platform.runLater(() -> {
                        String current = modelComboBox.getValue();
                        modelComboBox.getItems().setAll(modelNames);
                        // 목록을 가져왔는데 현재 설정된 값이 목록에 있다면 다시 선택해줌
                        modelComboBox.setValue(current);
                    });
                }
            } catch (Exception e) {
                System.err.println("Ollama 모델 목록을 가져올 수 없습니다: " + e.getMessage());
                // 실패해도 기존에 설정된 텍스트는 유지되므로 괜찮음
            }
        }).start();
    }

    // 패널 교체 로직
    private void switchPanel(String menuName) {
        if (menuName == null) return;

        // 모든 패널 숨기기
        aiPanel.setVisible(false);
        displayPanel.setVisible(false);
        aboutPanel.setVisible(false);

        // 선택된 것만 보이기
        switch (menuName) {
            case "AI 모델 설정":
                aiPanel.setVisible(true);
                break;
            case "화면 설정":
                displayPanel.setVisible(true);
                break;
            case "정보":
                aboutPanel.setVisible(true);
                break;
        }
    }
    

    @FXML
    private void closeSettings() {
        AppConfigService config = new AppConfigService();
        String selectedModel = modelComboBox.getValue();
        config.setModelName(selectedModel);
        config.save();
        setVisible(false);
    }

    public void setVisible(boolean visible) {
        rootBox.setVisible(visible);
    }

    public boolean isVisible() {
        return rootBox.isVisible();
    }

}