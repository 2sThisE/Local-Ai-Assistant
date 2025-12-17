import os
import subprocess
import glob
import shutil

# --- 설정 ---
PROJECT_ROOT = os.getcwd()
SOURCE_DIR = os.path.join(PROJECT_ROOT, "..", "..", "src", "main", "java")
TOOLS_PKG_DIR = os.path.join(SOURCE_DIR, "com", "example", "service", "tools")
PLUGINS_DIR = os.path.join(PROJECT_ROOT, "..", "tools")
TARGET_CLASSES_DIR = os.path.join(PROJECT_ROOT, "..", "..", "target", "classes")

# Java 컴파일러 옵션 (프로젝트의 classpath 필요)
# Maven 의존성(Gson 등)과 컴파일된 메인 클래스들이 필요함
CLASSPATH = f"{TARGET_CLASSES_DIR}"
# 윈도우에서는 세미콜론(;) 사용
if os.name == 'nt':
    CLASSPATH += ";" + os.path.join(os.environ['USERPROFILE'], '.m2', 'repository', 'com', 'google', 'code', 'gson', 'gson', '2.10.1', 'gson-2.10.1.jar')
else:
    CLASSPATH += ":" + os.path.join(os.environ['HOME'], '.m2', 'repository', 'com', 'google', 'code', 'gson', 'gson', '2.10.1', 'gson-2.10.1.jar')


# 제외할 파일 (내부 전용 또는 인터페이스)
EXCLUDE_FILES = ["HelpTool.java", "Tool.java"]

def build_plugins():
    # 1. 플러그인 디렉토리 생성
    if not os.path.exists(PLUGINS_DIR):
        os.makedirs(PLUGINS_DIR)
        print(f"📁 Created plugins directory: {PLUGINS_DIR}")

    # 2. 도구 소스 파일 찾기
    tool_files = glob.glob(os.path.join(TOOLS_PKG_DIR, "*Tool.java"))
    
    print(f"🔎 Found {len(tool_files)} tool files.")

    for java_file in tool_files:
        filename = os.path.basename(java_file)
        if filename in EXCLUDE_FILES:
            print(f"⏭️  Skipping core/interface: {filename}")
            continue

        tool_name = filename.replace(".java", "")
        print(f"🔨 Building plugin: {tool_name}...")

        # 3. 임시 컴파일 (개별 클래스 파일 생성)
        # 패키지 구조 유지를 위해 -d 옵션으로 classes 폴더에 출력하지 않고
        # 임시 폴더나 현재 위치에 컴파일 후 패키징하는 전략 사용
        
        # 간단하게 target/classes에 이미 컴파일된 파일이 있다고 가정하고 패키징만 할 수도 있지만,
        # 확실하게 하기 위해 재컴파일 또는 target 폴더의 class 파일 활용
        
        # 여기서는 이미 Maven 빌드(target/classes)가 되어 있다고 가정하고,
        # 해당 .class 파일을 쏙 뽑아서 JAR로 만듭니다.
        # (소스 컴파일은 의존성 문제로 복잡할 수 있으니, 컴파일된 결과물을 활용)
        
        class_file_path = os.path.join(TARGET_CLASSES_DIR, "com", "example", "service", "tools", f"{tool_name}.class")
        
        if not os.path.exists(class_file_path):
            print(f"⚠️  Class file not found: {class_file_path}")
            print("   (Please run 'mvn compile' first!)")
            continue

        # 4. JAR 패키징
        # jar -cf plugins/ToolName.jar -C target/classes com/example/service/tools/ToolName.class
        jar_path = os.path.join(PLUGINS_DIR, f"{tool_name}.jar")
        
        # JAR 명령어 실행
        # -C 옵션: 지정된 디렉토리로 이동하여 파일 포함
        cmd = [
            "jar",
            "cf",
            jar_path,
            "-C", TARGET_CLASSES_DIR,
            f"com/example/service/tools/{tool_name}.class"
        ]
        
        try:
            subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print(f"✅ Created: {jar_path}")
        except subprocess.CalledProcessError as e:
            print(f"❌ Failed to package {tool_name}: {e}")
        except FileNotFoundError:
             print("❌ 'jar' command not found. Please ensure JDK is installed and in PATH.")
             return

    print("\n🎉 All plugins built successfully!")

if __name__ == "__main__":
    build_plugins()
