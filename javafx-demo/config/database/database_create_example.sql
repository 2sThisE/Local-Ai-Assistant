create database AiAssistant;
use AiAssistant;

CREATE TABLE IF NOT EXISTS chat_history (
   	id VARCHAR(36) PRIMARY KEY,  -- Java에서 생성한 UUID 저장
   	role VARCHAR(10) NOT NULL,   -- 'user' 또는 'model' (혹은 'ai')
   	content TEXT NOT NULL,       -- 대화 내용
	timestamp DATETIME DEFAULT CURRENT_TIMESTAMP -- DB에 저장되는 순간의 시간 자동 기록
);

CREATE TABLE IF NOT EXISTS todo_categories (
     id INT AUTO_INCREMENT PRIMARY KEY,
     title VARCHAR(100) NOT NULL UNIQUE, -- 같은 주제 중복 방지
     created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS todo_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    category_id INT,
    content VARCHAR(255) NOT NULL,
    is_completed BOOLEAN DEFAULT FALSE,
    due_date DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 	FOREIGN KEY (category_id) REFERENCES todo_categories(id) ON DELETE CASCADE
 );

CREATE USER 'app_admin'@'%' IDENTIFIED BY 'admin_password';
GRANT ALL PRIVILEGES ON AiAssistant.* TO 'app_admin'@'%';

CREATE USER 'ai_reader'@'%' IDENTIFIED BY 'reader_password';
GRANT SELECT ON AiAssistant.* TO 'ai_reader'@'%';
GRANT INSERT, UPDATE, DELETE ON AiAssistant.todo_categories TO 'ai_reader'@'%';
GRANT INSERT, UPDATE, DELETE ON AiAssistant.todo_items TO 'ai_reader'@'%';

FLUSH PRIVILEGES;