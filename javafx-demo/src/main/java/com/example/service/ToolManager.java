package com.example.service;

import com.example.service.tools.HelpTool;
import com.example.service.tools.Tool;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ToolManager {

    private final Map<String, Tool> tools = new HashMap<>();
    private File currentWorkingDirectory;
    private final File pluginsDirectory;

    public ToolManager() {
        // 초기 작업 디렉토리 설정 (프로젝트 루트 or 사용자 홈)
        this.currentWorkingDirectory = new File(System.getProperty("user.dir"));
        
        // 플러그인 디렉토리 설정 (현재 작업 디렉토리 하위 'plugins')
        this.pluginsDirectory = new File(this.currentWorkingDirectory, "config/tools");
        if (!this.pluginsDirectory.exists()) {
            boolean created = this.pluginsDirectory.mkdirs();
            if (created) {
                System.out.println("플러그인 디렉토리가 생성되었습니다: " + this.pluginsDirectory.getAbsolutePath());
            }
        }

        registerTools();
    }

    private void registerTools() {
        addTool(new HelpTool(this));
        loadExternalPlugins();
    }
    
    private void loadExternalPlugins() {
        File[] jarFiles = pluginsDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) return;

        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                URL[] urls = { new URL("jar:file:" + jarFile.getAbsolutePath() + "!/") };
                URLClassLoader loader = URLClassLoader.newInstance(urls, Thread.currentThread().getContextClassLoader());

                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                        continue;
                    }

                    // 클래스 이름 변환 (com/example/MyTool.class -> com.example.MyTool)
                    String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                    
                    try {
                        loadToolClass(className, loader);
                    } catch (Throwable t) {
                        // 개별 클래스 로드 실패는 무시하고 계속 진행
                        // System.err.println("클래스 검사 실패 (" + className + "): " + t.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("JAR 파일 읽기 실패 (" + jarFile.getName() + "): " + e.getMessage());
            }
        }
    }

    private void loadToolClass(String className, ClassLoader loader) {
        try {
            Class<?> clazz = (loader != null) ? Class.forName(className, true, loader) : Class.forName(className);

            // Tool 인터페이스 구현 여부, 인터페이스/추상클래스 제외
            if (Tool.class.isAssignableFrom(clazz) && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
                // 이미 등록된 도구(예: 수동 등록한 HelpTool)는 건너뛰거나 덮어쓰기
                // 여기서는 인스턴스를 생성해보고 이름을 확인합니다.
                
                try {
                    Constructor<?> constructor = clazz.getConstructor(ToolManager.class);
                    Tool tool = (Tool) constructor.newInstance(this);
                    
                    // 중복 등록 방지 로직 (선택 사항)
                    if (tools.containsKey(tool.getName())) {
                         // 이미 수동 등록된 Core 도구라면 스킵 (HelpTool 등)
                         // 만약 플러그인으로 덮어쓰고 싶다면 제거 후 추가
                         if (tool instanceof HelpTool) return; 
                    }
                    
                    addTool(tool);
                } catch (NoSuchMethodException e) {
                    // ToolManager를 받는 생성자가 없는 경우 무시
                }
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // 로드할 수 없는 클래스 무시
        } catch (Exception e) {
            System.err.println("도구 인스턴스화 실패 (" + className + "): " + e.getMessage());
        }
    }

    private void addTool(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    // [NEW] 도구 목록 반환
    public Set<String> getRegisteredToolNames() {
        return tools.keySet();
    }

    // [NEW] 특정 도구 반환 (public으로 변경)
    public Tool getTool(String toolName) {
        return tools.get(toolName);
    }

    public ToolResult executeTool(String toolName, JsonObject params) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return new ToolResult(false, "알 수 없는 도구입니다: " + toolName, 
                "사용 가능한 도구인지 확인해주세요. help 도구를 사용하세요");
        }
        try {
            return tool.execute(params);
        } catch (Exception e) {
            e.printStackTrace();
            return new ToolResult(false, "도구 실행 중 예외 발생: " + e.getMessage(), 
                "파라미터 형식이나 시스템 상태를 확인해주세요.");
        }
    }

    public String getToolUsage(String toolName) {
        Tool tool = tools.get(toolName);
        return tool != null ? tool.getUsage() : "정보 없음";
    }

    // --- [State Management & Helpers] ---

    public File getCurrentWorkingDirectory() {
        return currentWorkingDirectory;
    }

    public void setCurrentWorkingDirectory(File currentWorkingDirectory) {
        this.currentWorkingDirectory = currentWorkingDirectory;
    }

    public String getCurrentPath() {
        return currentWorkingDirectory.getAbsolutePath();
    }

    public File resolvePath(String pathStr) {
        if (".".equals(pathStr)) return currentWorkingDirectory;
        File file = new File(pathStr);
        if (!file.isAbsolute()) {
            file = new File(currentWorkingDirectory, pathStr);
        }
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }
}
