package com.example.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseService {

    private static DatabaseService instance;

    private Connection appConnection; // 앱 전용 (관리자)
    private Connection aiConnection;  // AI 전용 (읽기 전용)

    private DatabaseService() {
        // 싱글톤이므로 생성자는 private
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    // 앱용 (관리자) 연결
    public Connection getAppConnection() {
        try {
            if (appConnection == null || appConnection.isClosed()) {
                appConnection = createConnection(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return appConnection;
    }

    // AI용 (읽기 전용) 연결
    public Connection getAiConnection() {
        try {
            if (aiConnection == null || aiConnection.isClosed()) {
                aiConnection = createConnection(false);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return aiConnection;
    }

    private Connection createConnection(boolean isAppUser) throws SQLException {
        AppConfigService config = AppConfigService.load();
        AppConfigService.DatabaseConfig dbConfig = config.getDatabaseConfig();

        if (dbConfig == null || dbConfig.getUrl() == null) {
            System.err.println("데이터베이스 설정이 없습니다.");
            return null;
        }

        String url = dbConfig.getUrl();
        String user;
        String password;

        if (isAppUser) {
            AppConfigService.DatabaseConfig.UserCredentials creds = dbConfig.getAppUser();
            if (creds == null) throw new SQLException("App user credentials not found");
            user = creds.getUser();
            password = creds.getPassword();
        } else {
            AppConfigService.DatabaseConfig.UserCredentials creds = dbConfig.getAiUser();
            if (creds == null) throw new SQLException("AI user credentials not found");
            user = creds.getUser();
            password = creds.getPassword();
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL Driver not found");
            e.printStackTrace();
            return null;
        }

        Connection conn = DriverManager.getConnection(url, user, password);
        System.out.println((isAppUser ? "[APP]" : "[AI]") + " 데이터베이스 연결 성공! ❤️");
        return conn;
    }

    public void closeAll() {
        try {
            if (appConnection != null && !appConnection.isClosed()) appConnection.close();
            if (aiConnection != null && !aiConnection.isClosed()) aiConnection.close();
            System.out.println("모든 데이터베이스 연결을 종료했습니다.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
