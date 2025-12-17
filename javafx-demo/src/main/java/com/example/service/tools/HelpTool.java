package com.example.service.tools;

import com.example.service.ToolManager;
import com.example.service.ToolResult;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HelpTool implements Tool {

    private final ToolManager toolManager;

    public HelpTool(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "사용 가능한 도구 목록을 보여주거나, 특정 도구의 상세 사용법을 안내합니다.";
    }

    @Override
    public String getUsage() {
        return "{ \"tool_name\": \"(검색할 도구 이름) 또는 (빈 파라미터)\" }";
    }

    @Override
    public ToolResult execute(JsonObject params) {
        // 1. 특정 도구 설명 요청
        if (params.has("tool_name")) {
            String targetToolName = params.get("tool_name").getAsString();
            Tool tool = toolManager.getTool(targetToolName);
            
            if (tool == null) {
                return new ToolResult(false, "도구를 찾을 수 없습니다: " + targetToolName, "전체 도구 목록을 보려면 파라미터 없이 help를 실행하세요.");
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("도구 정보: ").append(tool.getName()).append("\n\n");
            sb.append("설명:\n").append(tool.getDescription()).append("\n\n");
            sb.append("사용법:\n").append(tool.getUsage());
            
            return new ToolResult(true, sb.toString());
        } 
        
        // 2. 전체 도구 목록 요청
        else {
            List<String> toolNames = new ArrayList<>(toolManager.getRegisteredToolNames());
            Collections.sort(toolNames);
            
            StringBuilder sb = new StringBuilder();
            sb.append("사용 가능한 도구 목록:\n\n");
            for (String name : toolNames) {
                sb.append("- ").append(name).append("\n");
            }
            sb.append("\n반드시 { \"tool_name\": \"...\" } 파라미터를 사용하여 도구에 대한 설명과 사용법을 숙지하세요.");
            
            return new ToolResult(true, sb.toString());
        }
    }
}
