package com.example.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class AppConfigService {
    // 설정 파일 경로와 Gson 객체는 여기서만 관리합니다.
    private static final String CONFIG_PATH = "config\\modelConfig\\config.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("model_name")
    private String modelName;

    @SerializedName("system_prompt_file")
    private String systemPromptFile;

    // 기본 생성자 (기본값 설정)
    public AppConfigService() {
        
    }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getSystemPromptFile() { return systemPromptFile; }
    public void setSystemPromptFile(String systemPromptFile) { this.systemPromptFile = systemPromptFile; }

    // --- [핵심 기능] 파일에서 불러오기 (Static Factory Method) ---
    public static AppConfigService load() {
        File file = new File(CONFIG_PATH);
        if (!file.exists()) {
            return new AppConfigService(); // 파일 없으면 기본값 반환
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            AppConfigService config = gson.fromJson(reader, AppConfigService.class);
            return (config != null) ? config : new AppConfigService();
        } catch (IOException e) {
            e.printStackTrace();
            return new AppConfigService(); // 에러 나면 기본값
        }
    }

    // --- [핵심 기능] 파일로 저장하기 ---
    public void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_PATH), StandardCharsets.UTF_8)) {
            gson.toJson(this, writer);
            System.out.println("설정 저장 완료: " + this.modelName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}