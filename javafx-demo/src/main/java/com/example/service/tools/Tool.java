package com.example.service.tools;

import com.example.service.ToolResult;
import com.google.gson.JsonObject;

public interface Tool {
    /**
     * 도구의 고유 이름 (예: "run_shell")
     */
    String getName();

    /**
     * 도구에 대한 자세한 설명
     * (이 도구의 목적, 사용 시 주의사항 등을 기술)
     */
    String getDescription();

    /**
     * 도구의 올바른 사용법 (JSON 형식 예시)
     * 예: { "command": "..." }
     */
    String getUsage();

    /**
     * 도구 실행
     * @param params AI가 전달한 파라미터 JSON
     * @return 실행 결과 (성공 여부, 출력, 가이드 포함)
     */
    ToolResult execute(JsonObject params);
}
