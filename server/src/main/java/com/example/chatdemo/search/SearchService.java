package com.example.chatdemo.search;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.chatdemo.entity.MessageRow;
import com.example.chatdemo.entity.SearchHit;
import com.example.chatdemo.mapper.MessageMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 sessions.doc 投影成 messages 行，并提供全文检索与高亮片段。
 *
 * <p>投影失败不影响会话保存：doc 仍是唯一真相，投影可随时重建。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /** 片段上下文长度（命中位置前后各取多少字符） */
    private static final int SNIPPET_BEFORE = 60;
    private static final int SNIPPET_AFTER = 120;

    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public SearchService(MessageMapper messageMapper, ObjectMapper objectMapper) {
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    /** 重建某个会话的检索投影：先删后插，整体覆盖 */
    public void reproject(String sessionId, String doc) {
        try {
            List<MessageRow> rows = extract(sessionId, doc);
            messageMapper.delete(new QueryWrapper<MessageRow>().eq("session_id", sessionId));
            for (MessageRow row : rows) {
                messageMapper.insert(row);
            }
        } catch (Exception e) {
            log.warn("投影失败，跳过检索索引 sessionId={}", sessionId, e);
        }
    }

    public void clear(String sessionId) {
        messageMapper.delete(new QueryWrapper<MessageRow>().eq("session_id", sessionId));
    }

    /** 清空全部投影（删除所有会话时调用） */
    public void clearAll() {
        messageMapper.delete(null);
    }

    public List<SearchHit> search(String query, int limit, int offset) {
        return messageMapper.search(query, limit, offset);
    }

    /** 从 doc 中抽取可检索文本：用户提问全文 + 助手回答的正文块（跳过思考与工具块） */
    private List<MessageRow> extract(String sessionId, String doc) throws Exception {
        List<MessageRow> rows = new ArrayList<>();
        if (doc == null || doc.isBlank()) {
            return rows;
        }
        JsonNode root = objectMapper.readTree(doc);
        JsonNode messages = root.path("messages");
        if (!messages.isArray()) {
            return rows;
        }
        int seq = 0;
        for (JsonNode message : messages) {
            String role = message.path("role").asText("");
            String text = "user".equals(role) ? userText(message) : assistantText(message);
            if (!text.isBlank()) {
                MessageRow row = new MessageRow();
                row.setSessionId(sessionId);
                row.setMessageId(message.path("id").asText(""));
                row.setParentId(message.path("parentId").isNull() ? null : message.path("parentId").asText(null));
                row.setRole(role);
                row.setSeq(seq);
                row.setCreatedAt(message.path("createdAt").asLong(0L));
                row.setText(text);
                rows.add(row);
            }
            seq++;
        }
        return rows;
    }

    private String userText(JsonNode message) {
        return message.path("content").asText("");
    }

    private String assistantText(JsonNode message) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : message.path("blocks")) {
            if ("text".equals(block.path("type").asText()) && !block.path("isTemplate").asBoolean(false)) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(block.path("text").asText(""));
            }
        }
        return sb.toString();
    }

    /** 命中位置切三段：前文 / 命中 / 后文；找不到原文命中时退化为开头摘要 */
    public Snippet snippet(String text, String query) {
        if (text == null || text.isEmpty()) {
            return new Snippet(List.of(), true, true);
        }
        int index = query == null || query.isEmpty()
                ? -1
                : text.toLowerCase().indexOf(query.toLowerCase());
        if (index < 0) {
            int end = Math.min(text.length(), SNIPPET_BEFORE + SNIPPET_AFTER);
            return new Snippet(List.of(new Part(text.substring(0, end), false)), true, end < text.length());
        }
        int start = Math.max(0, index - SNIPPET_BEFORE);
        int end = Math.min(text.length(), index + query.length() + SNIPPET_AFTER);
        List<Part> parts = new ArrayList<>();
        if (start > 0) {
            parts.add(new Part(text.substring(start, index), false));
        }
        parts.add(new Part(text.substring(index, index + query.length()), true));
        if (end > index + query.length()) {
            parts.add(new Part(text.substring(index + query.length(), end), false));
        }
        return new Snippet(parts, start == 0, end == text.length());
    }

    public record Part(String text, boolean highlight) {
    }

    public record Snippet(List<Part> parts, boolean isBegin, boolean isEnd) {
    }
}
