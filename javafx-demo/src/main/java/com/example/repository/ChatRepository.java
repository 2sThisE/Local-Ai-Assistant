package com.example.repository;

import com.example.model.ChatMessage;
import com.example.service.DatabaseService;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatRepository {

    private static final String INSERT_SQL = "INSERT INTO chat_history (id, role, content, timestamp) VALUES (?, ?, ?, ?)";
    private static final String SELECT_RECENT_SQL = "SELECT id, role, content, timestamp FROM chat_history ORDER BY timestamp DESC LIMIT ?";
    private static final String UPDATE_CONTENT_SQL = "UPDATE chat_history SET content = ? WHERE id = ?";

    // 메시지 저장 (앱 전용 연결 사용)
    public void save(ChatMessage message) {
        Connection conn = DatabaseService.getInstance().getAppConnection();
        if (conn == null) return;

        try (PreparedStatement pstmt = conn.prepareStatement(INSERT_SQL)) {
            pstmt.setString(1, message.getId());
            pstmt.setString(2, message.getRole());
            pstmt.setString(3, message.getContent());
            pstmt.setObject(4, message.getTimestamp()); // LocalDateTime 바로 저장 가능 (JDBC 드라이버 버전에 따라 다름)
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("메시지 저장 실패!");
            e.printStackTrace();
        }
    }

    // 최근 메시지 불러오기
    public List<ChatMessage> findRecentMessages(int limit) {
        List<ChatMessage> messages = new ArrayList<>();
        Connection conn = DatabaseService.getInstance().getAppConnection();
        if (conn == null) return messages;

        try (PreparedStatement pstmt = conn.prepareStatement(SELECT_RECENT_SQL)) {
            pstmt.setInt(1, limit);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String role = rs.getString("role");
                    String content = rs.getString("content");
                    
                    // Timestamp -> LocalDateTime 변환
                    Timestamp ts = rs.getTimestamp("timestamp");
                    LocalDateTime timestamp = (ts != null) ? ts.toLocalDateTime() : null;
                    
                    // DB 데이터로 객체 복원
                    ChatMessage msg = new ChatMessage(id, role, content, timestamp);
                    messages.add(msg);
                }
            }
        } catch (SQLException e) {
            System.err.println("최근 메시지 불러오기 실패!");
            e.printStackTrace();
        }

        // 최신순(DESC) -> 과거순(ASC)으로 뒤집어서 리턴
        Collections.reverse(messages);
        return messages;
    }

    // 메시지 내용 업데이트 (스트리밍 완료 후 또는 툴 실행 결과 추가 시)
    public void updateContent(String id, String newContent) {
        Connection conn = DatabaseService.getInstance().getAppConnection();
        if (conn == null) return;

        try (PreparedStatement pstmt = conn.prepareStatement(UPDATE_CONTENT_SQL)) {
            pstmt.setString(1, newContent);
            pstmt.setString(2, id);
            
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                System.err.println("메시지 업데이트 실패: ID를 찾을 수 없음 -> " + id);
            }
        } catch (SQLException e) {
            System.err.println("메시지 업데이트 중 오류 발생!");
            e.printStackTrace();
        }
    }

    // --- 요약(Summary) 관련 기능 추가 ---

    private static final String INSERT_SUMMARY_SQL = 
        "INSERT INTO chat_summaries (summary_content, start_msg_id, end_msg_id, start_timestamp, end_timestamp) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_LAST_SUMMARY_SQL = 
        "SELECT summary_content FROM chat_summaries ORDER BY id DESC LIMIT 1";
    private static final String COUNT_MESSAGES_SQL = "SELECT COUNT(*) FROM chat_history";
    // 페이징으로 메시지 가져오기 (오래된 순서대로 정렬해야 요약하기 좋음)
    // LIMIT ? OFFSET ? 사용
    private static final String SELECT_MESSAGES_RANGE_SQL = 
        "SELECT id, role, content, timestamp FROM chat_history ORDER BY timestamp ASC LIMIT ? OFFSET ?";


    public void saveSummary(String content, String startId, String endId, LocalDateTime startTs, LocalDateTime endTs) {
        Connection conn = DatabaseService.getInstance().getAppConnection();
        if (conn == null) return;

        try (PreparedStatement pstmt = conn.prepareStatement(INSERT_SUMMARY_SQL)) {
            pstmt.setString(1, content);
            pstmt.setString(2, startId);
            pstmt.setString(3, endId);
            pstmt.setObject(4, startTs);
            pstmt.setObject(5, endTs);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("요약 저장 실패!");
            e.printStackTrace();
        }
    }

    public String getLastSummary() {
        Connection conn = DatabaseService.getInstance().getAppConnection();
        if (conn == null) return "";

        try (PreparedStatement pstmt = conn.prepareStatement(SELECT_LAST_SUMMARY_SQL);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("summary_content");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ""; // 요약 없음
    }

    public int getMessageCount() {
        Connection conn = DatabaseService.getInstance().getAppConnection();
        if (conn == null) return 0;
        try (PreparedStatement pstmt = conn.prepareStatement(COUNT_MESSAGES_SQL);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // 마지막 요약 이후 쌓인 메시지 개수 조회
    public int countMessagesAfterLastSummary() {
        Connection conn = DatabaseService.getInstance().getAppConnection();
        if (conn == null) return 0;

        String lastSummaryEndId = null;
        String getLastSummaryEndIdSql = "SELECT end_msg_id FROM chat_summaries ORDER BY id DESC LIMIT 1";
        
        try (PreparedStatement pstmt = conn.prepareStatement(getLastSummaryEndIdSql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                lastSummaryEndId = rs.getString("end_msg_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (lastSummaryEndId == null) {
            // 요약이 없으면 전체 메시지 개수 반환
            return getMessageCount();
        }

        // 마지막 요약된 메시지의 타임스탬프 가져오기
        LocalDateTime lastSummaryTs = null;
        String getTsSql = "SELECT timestamp FROM chat_history WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(getTsSql)) {
            pstmt.setString(1, lastSummaryEndId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("timestamp");
                    if (ts != null) lastSummaryTs = ts.toLocalDateTime();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        if (lastSummaryTs == null) return getMessageCount(); // 뭔가 꼬였으면 전체 반환

        // 그 이후 메시지 개수 카운트
        String countSql = "SELECT COUNT(*) FROM chat_history WHERE timestamp > ?";
        try (PreparedStatement pstmt = conn.prepareStatement(countSql)) {
            pstmt.setObject(1, lastSummaryTs);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return 0;
    }

    public List<ChatMessage> findMessagesForSummary(int offset, int limit) {
        List<ChatMessage> messages = new ArrayList<>();
        Connection conn = DatabaseService.getInstance().getAppConnection();
        if (conn == null) return messages;

        try (PreparedStatement pstmt = conn.prepareStatement(SELECT_MESSAGES_RANGE_SQL)) {
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String role = rs.getString("role");
                    String content = rs.getString("content");
                    Timestamp ts = rs.getTimestamp("timestamp");
                    LocalDateTime timestamp = (ts != null) ? ts.toLocalDateTime() : null;
                    
                    messages.add(new ChatMessage(id, role, content, timestamp));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }
}
