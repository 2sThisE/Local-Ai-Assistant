import sys
import ollama
import io
import re
import json
import os
from datetime import datetime

# 윈도우 인코딩 설정
sys.stdout = io.TextIOWrapper(sys.stdout.detach(), encoding='utf-8')
sys.stdin = io.TextIOWrapper(sys.stdin.detach(), encoding='utf-8')

# 전역 변수
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
    # 경로가 틀릴 경우를 대비해 상위 폴더 등 경로 확인 필요 (현재 구조 유지)
    config_path = os.path.join(base_path, "..", "modelConfig", "config.json")
    prompt_path = os.path.join(base_path, "..", "modelConfig", "system_prompt.txt")
    
    try:
        # 1. config.json 읽기
        with open(config_path, 'r', encoding='utf-8') as f:
            config = json.load(f)
            MODEL_NAME = config.get("model_name", "gemma3:12b")
            
        # 2. system_prompt.txt 읽기
        with open(prompt_path, 'r', encoding='utf-8') as f:
            SYSTEM_PROMPT = f.read()
            
        # 3. 히스토리 초기화
        history = [{'role': 'system', 'content': SYSTEM_PROMPT}]
        
    except Exception as e:
        print(f"TOKEN:Error loading config: {e}") # Java가 알 수 있게 토큰으로 출력
        sys.stdout.flush()
        # 설정 로드 실패 시에도 일단 기본값으로 진행하거나 종료
        # sys.exit(1) 

def get_current_time_info():
    """ 현재 시간 정보와 시간대 문자열 반환 """
    now_obj = datetime.now()
    hour = now_obj.hour
    
    time_period = ""
    if 5 <= hour < 12: time_period = "Morning (아침)"
    elif 12 <= hour < 18: time_period = "Afternoon (오후)"
    elif 18 <= hour < 22: time_period = "Evening (저녁)"
    else: time_period = "Night (밤/새벽)"
    
    now_str = now_obj.strftime("%Y-%m-%d %H:%M:%S %A")
    return now_str, time_period

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
    global history
    load_config()
    
    print("실행 준비 완료")
    sys.stdout.flush()

    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            
            user_input = line.strip()
            if user_input == "EXIT": break
            
            # -------------------------------------------------------------
            # 1. 도구 결과 수신 (TOOL_RESULT)
            # -------------------------------------------------------------
            if user_input.startswith("TOOL_RESULT:"):
                result_json = user_input.replace("TOOL_RESULT:", "").strip()
                history.append({'role': 'user', 'content': f"TOOL_RESULT:\n{result_json}\n\nProceed based on this result."})
                
                ai_reply = chat_and_stream(history)
                history.append({'role': 'assistant', 'content': ai_reply})
            
            # -------------------------------------------------------------
            # 2. 대화 내역 복원 및 환영 인사 (RESTORE_AND_GREET)
            # -------------------------------------------------------------
            elif user_input.startswith("RESTORE_AND_GREET:"):
                json_str = user_input.replace("RESTORE_AND_GREET:", "").strip()
                try:
                    restored_history = json.loads(json_str)
                    
                    # 히스토리 재구성 (System Prompt + 복원된 대화)
                    new_history = [{'role': 'system', 'content': SYSTEM_PROMPT}]
                    new_history.extend(restored_history)
                    history = new_history
                    
                    # 현재 시간 계산 (함수 활용)
                    now_str, time_period = get_current_time_info()
                    time_info = f"[System Info: User reconnected. Current Time is {now_str} ({time_period}).]"

                    # 인사를 위한 임시 히스토리 생성 (Hidden Context 주입)
                    temp_history = history.copy()
                    temp_history.append({
                        'role': 'system', 
                        'content': f"""
                            [HIDDEN CONTEXT]
                            {time_info}
                            [/HIDDEN CONTEXT]

                            INSTRUCTION:
                            Based on the restored conversation history and the current time above, greet the user naturally.
                            If the last conversation was recent, act as if continuing.
                            If it was long ago, welcome them back.
                            IMPORTANT: Do NOT output the [System Info] or [HIDDEN CONTEXT] block. Just speak naturally.
                            """
                    })
                    
                    # 인사말 생성 및 저장
                    ai_reply = chat_and_stream(temp_history)
                    history.append({'role': 'assistant', 'content': ai_reply})
                    
                except Exception as e:
                    print(f"TOKEN:Error restoring history: {e}")
                    print("TOKEN:[DONE]")
                    sys.stdout.flush()

            # -------------------------------------------------------------
            # 3. 요약 요청 처리 (SUMMARIZE)
            # -------------------------------------------------------------
            elif user_input.startswith("SUMMARIZE:"):
                json_str = user_input.replace("SUMMARIZE:", "").strip()
                try:
                    data = json.loads(json_str)
                    prev_summary = data.get("previous_summary", "")
                    messages = data.get("messages", [])
                    
                    # 메시지 텍스트 변환
                    conversation_text = ""
                    for msg in messages:
                        role = msg.get("role", "unknown")
                        content = msg.get("content", "")
                        conversation_text += f"{role}: {content}\n"
                    
                    summary_prompt = f"""
                        You are an expert summarizer.
                        Previous Summary:
                        {prev_summary}

                        Recent Conversation:
                        {conversation_text}

                        Task:
                        Summarize the recent conversation, merging it with the previous summary to create a concise, updated summary of the entire context.
                        Focus on key decisions, user preferences, and important events.
                        Keep it under 5 sentences. Korean language is preferred if the conversation is in Korean.
                        """
                    # [수정] 하드코딩 대신 설정된 모델 사용 (혹은 가벼운 모델이 있다면 그것으로 분기 가능)
                    response = ollama.chat(
                        model='exaone3.5:7.8b', 
                        messages=[{'role': 'user', 'content': summary_prompt}],
                        options={'temperature': 0.1, 'num_ctx': 8192} # 문맥 길이 확보
                    )
                    
                    summary_result = response['message']['content']
                    # 줄바꿈 처리하여 전송
                    safe_summary = summary_result.replace("\r\n", "[NEWLINE]").replace("\n", "[NEWLINE]").replace("\r", "")
                    
                    print(f"SUMMARY_RESULT:{safe_summary}")
                    sys.stdout.flush()

                except Exception as e:
                    print(f"SUMMARY_RESULT:Error summarizing: {e}")
                    sys.stdout.flush()

            # -------------------------------------------------------------
            # 4. 일반 메시지 수신 (기본)
            # -------------------------------------------------------------
            else:
                # [수정] 일반 대화일 때만 시간 정보를 계산하여 주입
                now_str, time_period = get_current_time_info()
                timed_input = f"[System Info: Current Time is {now_str}. It is currently {time_period}.]\n{user_input}"
                
                history.append({'role': 'user', 'content': timed_input})
                ai_reply = chat_and_stream(history)
                history.append({'role': 'assistant', 'content': ai_reply})

        except Exception as e:
            # 치명적 에러 발생 시에도 Java 루프가 안 끊기게 처리
            error_msg = f"Critical Python Error: {str(e)}"
            print(f"TOKEN:{error_msg}")
            print("TOKEN:[DONE]")
            sys.stdout.flush()

if __name__ == "__main__":
    main()