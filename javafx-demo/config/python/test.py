import sys
import ollama
import io
import re
import json
import os # [NEW] 경로 처리를 위해 추가

# 윈도우 인코딩 설정
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding='utf-8')
sys.stdin = io.TextIOWrapper(sys.stdin.detach(), encoding='utf-8')

# 전역 변수 (load_config에서 설정됨)
MODEL_NAME = "gemma3:12b" 
SYSTEM_PROMPT = ""
history = []

def get_base_path():
    """ 실행 파일(또는 스크립트)의 절대 경로 반환 """
    if getattr(sys, 'frozen', False):
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.abspath(__file__))

def load_config():
    """ config.json과 system_prompt.txt 로드 """
    global MODEL_NAME, SYSTEM_PROMPT, history
    
    base_path = get_base_path()
    config_path = os.path.join(base_path,"..","modelConfig", "config.json")
    
    try:
        # 1. config.json 읽기
        with open(config_path, 'r', encoding='utf-8') as f:
            config = json.load(f)
            MODEL_NAME = config.get("model_name", "gemma3:12b")
            
        # 2. system_prompt.txt 읽기
        prompt_path = os.path.join(base_path, "..","modelConfig", "system_prompt.txt")
        with open(prompt_path, 'r', encoding='utf-8') as f:
            SYSTEM_PROMPT = f.read()
            
        # 3. 히스토리 초기화
        history = [{'role': 'system', 'content': SYSTEM_PROMPT}]
        
        # 디버깅용 (Java 콘솔에서 확인 가능)
        # print(f"DEBUG: Config Loaded. Model: {MODEL_NAME}")
        
    except Exception as e:
        print(f"Error loading config: {e}")
        sys.stdout.flush()
        sys.exit(1)

def chat_and_stream(messages):
    """ Ollama 스트리밍 채팅 및 출력 함수 """
    print("TOKEN:[Thinking]")
    sys.stdout.flush()

    try:
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
                safe_content = content.replace("\r\n", "[NEWLINE]").replace("\n", "[NEWLINE]").replace("\r", "")
                print(f"TOKEN:{safe_content}")
                sys.stdout.flush()
                
        print("TOKEN:[DONE]")
        sys.stdout.flush()
        return full_response
        
    except Exception as e:
        error_msg = f"Ollama Error: {str(e)}"
        print(f"TOKEN:{error_msg}")
        print("TOKEN:[DONE]")
        sys.stdout.flush()
        return error_msg

def main():
    # [NEW] 시작 전 설정 로드
    load_config()
    
    print("실행 준비 완료")
    sys.stdout.flush()

    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            
            user_input = line.strip()
            if user_input == "EXIT": break
            
            # [도구 결과 수신]
            if user_input.startswith("TOOL_RESULT:"):
                result_json = user_input.replace("TOOL_RESULT:", "").strip()
                history.append({'role': 'user', 'content': f"TOOL_RESULT:\n{result_json}\n\nProceed based on this result."})
                
                ai_reply = chat_and_stream(history)
                history.append({'role': 'assistant', 'content': ai_reply})
                
            # [일반 메시지 수신]
            else:
                history.append({'role': 'user', 'content': user_input})
                ai_reply = chat_and_stream(history)
                history.append({'role': 'assistant', 'content': ai_reply})

        except Exception as e:
            print(f"Error: {str(e)}")
            sys.stdout.flush()

if __name__ == "__main__":
    main()