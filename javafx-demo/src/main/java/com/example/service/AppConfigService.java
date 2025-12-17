package com.example.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class AppConfigService {
    // 설정 파일 경로와 Gson 객체는 여기서만 관리합니다.
    private static final String CONFIG_PATH = "config\\modelConfig\\config.json";
    private static final String SYSTEM_PROMPT_PATH="config\\modelConfig\\system_prompt.txt";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("model_name")
    private String modelName;

    @SerializedName("system_prompt_file")
    private String systemPromptFile;

    // 시스템 프롬프트 내용 (파일에 별도 저장)
    private transient String systemPrompt;

    // 기본 생성자 (기본값 설정)
    public AppConfigService() {
        
    }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getSystemPromptFile() { return systemPromptFile; }
    public void setSystemPromptFile(String systemPromptFile) { this.systemPromptFile = systemPromptFile; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    // --- [핵심 기능] 파일에서 불러오기 (Static Factory Method) ---
    public static AppConfigService load() {
        AppConfigService config = null;
        File file = new File(CONFIG_PATH);
        
        // 1. JSON 설정 로드
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                config = gson.fromJson(reader, AppConfigService.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (config == null) {
            config = new AppConfigService();
        }

        // 2. 시스템 프롬프트 텍스트 파일 로드
        File promptFile = new File(SYSTEM_PROMPT_PATH);
        if (promptFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(promptFile), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                // 파일 끝의 불필요한 줄바꿈 정리
                String loadedPrompt = sb.toString();
                if (loadedPrompt.endsWith("\n")) {
                    loadedPrompt = loadedPrompt.substring(0, loadedPrompt.length() - 1);
                }
                config.setSystemPrompt(loadedPrompt);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            config.setSystemPrompt("");
        }

        return config;
    }

    // --- [핵심 기능] 파일로 저장하기 ---
    public void save() {
        File configFile = new File(CONFIG_PATH);
        if (configFile.getParentFile() != null) configFile.getParentFile().mkdirs();

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            gson.toJson(this, writer);
            System.out.println("설정 저장 완료: " + this.modelName);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 시스템 프롬프트 저장
        if (this.systemPrompt != null) {
            File promptFile = new File(SYSTEM_PROMPT_PATH);
            if (promptFile.getParentFile() != null) promptFile.getParentFile().mkdirs();
            
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(promptFile), StandardCharsets.UTF_8)) {
                writer.write(this.systemPrompt);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}