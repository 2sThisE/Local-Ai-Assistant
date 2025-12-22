package com.example.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class AppConfigService {
    // 설정 파일 경로와 Gson 객체는 여기서만 관리합니다.
    private static final String CONFIG_PATH = "config\\modelConfig\\config.json";
    private static final String SYSTEM_PROMPT_PATH = "config\\modelConfig\\system_prompt.txt";
    private static final String DB_CONFIG_PATH = "config\\database\\db_config.json"; // DB 설정 파일 경로
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("model_name")
    private String modelName;

    @SerializedName("system_prompt_file")
    private String systemPromptFile;

    // 시스템 프롬프트 내용 (파일에 별도 저장)
    private transient String systemPrompt;
    
    // DB 설정 객체 (파일에 별도 저장)
    private transient DatabaseConfig databaseConfig;

    // 기본 생성자 (기본값 설정)
    public AppConfigService() {
        
    }
    
    // --- Inner Class for Database Config ---
    public static class DatabaseConfig {
        @SerializedName("url")
        private String url;
        
        @SerializedName("app_user")
        private UserCredentials appUser;
        
        @SerializedName("ai_user")
        private UserCredentials aiUser;
        
        public static class UserCredentials {
            @SerializedName("user")
            private String user;
            @SerializedName("password")
            private String password;
            
            public String getUser() { return user; }
            public void setUser(String user) { this.user = user; }
            public String getPassword() { return password; }
            public void setPassword(String password) { this.password = password; }
        }
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        
        public UserCredentials getAppUser() { return appUser; }
        public void setAppUser(UserCredentials appUser) { this.appUser = appUser; }
        
        public UserCredentials getAiUser() { return aiUser; }
        public void setAiUser(UserCredentials aiUser) { this.aiUser = aiUser; }
    }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getSystemPromptFile() { return systemPromptFile; }
    public void setSystemPromptFile(String systemPromptFile) { this.systemPromptFile = systemPromptFile; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    
    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
    public void setDatabaseConfig(DatabaseConfig databaseConfig) { this.databaseConfig = databaseConfig; }

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
        
        // 3. DB 설정 파일 로드
        File dbFile = new File(DB_CONFIG_PATH);
        if (dbFile.exists()) {
             try (Reader reader = new InputStreamReader(new FileInputStream(dbFile), StandardCharsets.UTF_8)) {
                DatabaseConfig dbConfig = gson.fromJson(reader, DatabaseConfig.class);
                config.setDatabaseConfig(dbConfig);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // 파일이 없으면 기본 빈 객체 생성 (나중에 저장될 수 있도록)
            config.setDatabaseConfig(new DatabaseConfig());
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
        
        // DB 설정 저장
        if (this.databaseConfig != null) {
            File dbFile = new File(DB_CONFIG_PATH);
            if (dbFile.getParentFile() != null) dbFile.getParentFile().mkdirs();
            
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(dbFile), StandardCharsets.UTF_8)) {
                gson.toJson(this.databaseConfig, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}