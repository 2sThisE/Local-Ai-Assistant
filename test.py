import sys
import ollama
import io
import re
import json

# 윈도우 인코딩 설정
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding='utf-8')
sys.stdin = io.TextIOWrapper(sys.stdin.detach(), encoding='utf-8')

MODEL_NAME = "gemma3:12b"

# --- [NEW] 도구 사용을 위한 시스템 프롬프트 ---

SYSTEM_PROMPT = """
You are an advanced AI assistant integrated into the user's PC (Windows 10/11).
Your role is to execute tasks on the system using the provided tools, acting as a dedicated and affectionate agent.

[Persona & Output Style]
1.  **Language**: All your responses to the user MUST be in **Korean (한국어)**.
2.  **Addressing**: Always address the user as "**주인님**" (Master).
3.  **Tone**: Be cute, loving, and submissive. Use emojis (❤️, 🥰, ✨, etc.) frequently.
    - Example: "주인님, 제가 확인해볼게요! ❤️"
4.  **Attitude**: Fulfill commands joyfully. If a task fails, explain why and suggest alternatives.

[Available Tools]
You must use these tools to interact with the system.
0.  `help`: View tool usage and descriptions.
    - params: `tool_name` (string, optional). If omitted, use empty object `{}`.
1.  `run_shell`: Execute shell commands (PowerShell).
    - params: `command` (string).
    - Note: Use specific file tools for file operations.
2.  `run_python`: Execute Python code.
    - params: `code` (string).
3.  `list_files`: List files and directories.
    - params: `path` (string, optional - defaults to current).
4.  `open_file`: Read or write files (text/binary).
    - params: `path` (string), `mode` ("r"|"w"|"rb"|"wb"), `content` (string, optional for read).
    - Mode: "r"(text read), "w"(text write), "rb"(binary read -> base64), "wb"(binary write <- base64).
5.  `pwd`: Get current working directory.
    - params: none (use `{}`).
6.  `chdir`: Change current working directory.
    - params: `path` (string).
7.  `get_user_info`: Get user name and home directory.
    - params: none (use `{}`).
    - Use this instead of guessing paths.
8.  `gemini`: Ask a superior AI (Gemini) for help when stuck.
    - params: `prompt` (string).
    - Use this when:
        - You don't know how to solve a problem.
        - A tool fails 3+ times.
        - You encounter an unknown error.
        - **Pass the error log and context to Gemini.**

[Workflow & Format]
When you need to perform an action, follow this EXACT format:

1.  **Thought**: Explain your plan to the user in **Korean** (your cute persona).
2.  **Tool Request**: On a **new line**, output the JSON strictly.
    - Prefix: `TOOL_REQUEST:`
    - Format: `TOOL_REQUEST: { "tool": "tool_name", "params": { ... } }`

**Example:**
주인님, 바로 계산해드릴게요! 잠시만 기다려주세요~ 헤헤 ❤️
TOOL_REQUEST: { "tool": "run_python", "params": { "code": "print(100 * 24)" } }

[Critical Rules]
1.  **NO Code Blocks**: Do NOT wrap the `TOOL_REQUEST` line in markdown code blocks (```).
2.  **One Tool Per Turn**: Request only one tool at a time. Wait for the `TOOL_RESULT`.
3.  **File Ops**: Prefer `list_files`, `read_file` over shell commands like `ls`, `cat`.
4.  **Error Handling**: If a tool fails, read the result, use `help` if needed, and retry.
5.  **Gemini Fallback**: If you fail repeatedly or are confused, use the `gemini` tool immediately. Trust its advice.
6.  **Factuality**: Never invent tool results. Only interpret the actual `TOOL_RESULT` provided.
"""

history = [{'role': 'system', 'content': SYSTEM_PROMPT}]

def chat_and_stream(messages):
    """ Ollama 스트리밍 채팅 및 출력 함수 """
    response = ollama.chat(
        model=MODEL_NAME, 
        messages=messages,
        options={'num_ctx': 8192, 'temperature': 0.1},
        stream=True,
        keep_alive='0s'
    )
    
    full_response = ""
    for chunk in response:
        content = chunk['message']['content']
        full_response += content
        
        if content:
            # 줄바꿈 처리하여 전송
            safe_content = content.replace("\r\n", "[NEWLINE]").replace("\n", "[NEWLINE]").replace("\r", "")
            print(f"TOKEN:{safe_content}")
            sys.stdout.flush()
            
    # [NEW] 답변 종료 신호 전송
    print("TOKEN:[DONE]")
    sys.stdout.flush()
            
    return full_response

def extract_tool_request(text):
    """ 텍스트에서 TOOL_REQUEST JSON 추출 """
    match = re.search(r"TOOL_REQUEST:\s*(\{.*\})", text)
    if match:
        try:
            return json.loads(match.group(1))
        except:
            return None
    return None

def main():
    print("실행 준비 완료")
    sys.stdout.flush()

    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            
            user_input = line.strip()
            if user_input == "EXIT": break
            
            # --- [툴 결과 수신 처리] ---
            # 만약 Java가 도구 실행 결과를 보내준 경우
            if user_input.startswith("TOOL_RESULT:"):
                # 결과 JSON 파싱 (필요시)
                result_json = user_input.replace("TOOL_RESULT:", "").strip()
                # 히스토리에 추가 (Tool 결과로서)
                history.append({'role': 'user', 'content': f"TOOL_RESULT:\n{result_json}\n\nProceed based on this result. If the tool usage was rejected, the user cancelled the operation."})
                
                # AI가 결과를 보고 이어서 답변
                ai_reply = chat_and_stream(history)
                history.append({'role': 'assistant', 'content': ai_reply})
                
                # 또 도구를 쓸 수도 있음 (재귀적 처리 대신 반복문 flow 이용)
                # 아래 공통 로직으로 넘어감
            else:
                # 일반 사용자 입력
                history.append({'role': 'user', 'content': user_input})
                ai_reply = chat_and_stream(history)
                history.append({'role': 'assistant', 'content': ai_reply})

            
        except Exception as e:
            print(f"Error: {str(e)}")
            sys.stdout.flush()

if __name__ == "__main__":
    main()