package com.example.chatdemo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.chatdemo.entity.SearchHit;
import com.example.chatdemo.entity.Session;
import com.example.chatdemo.mapper.SessionMapper;
import com.example.chatdemo.search.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 跨会话全文检索 */
@RestController
public class SearchController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final SearchService searchService;
    private final SessionMapper sessionMapper;

    public SearchController(SearchService searchService, SessionMapper sessionMapper) {
        this.searchService = searchService;
        this.sessionMapper = sessionMapper;
    }

    /** 重建全部会话的检索投影：用于补历史数据（doc 是唯一真相，投影可随时重建） */
    @PostMapping("/api/search/reindex")
    public ResponseEntity<?> reindex() {
        var sessions = sessionMapper.selectList(new QueryWrapper<Session>().select("id", "doc"));
        for (Session session : sessions) {
            searchService.reproject(session.getId(), session.getDoc());
        }
        return ResponseEntity.ok(Map.of("reindexed", sessions.size()));
    }

    @PostMapping("/api/search")
    public ResponseEntity<?> search(@RequestBody SearchRequest req) {
        String query = req == null || req.query() == null ? "" : req.query().trim();
        if (query.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query 不能为空"));
        }
        int limit = req.limit() == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, req.limit()));
        int offset = req.offset() == null ? 0 : Math.max(0, req.offset());

        // 多取一条用于判断是否还有下一页
        List<SearchHit> hits = searchService.search(query, limit + 1, offset);
        boolean hasMore = hits.size() > limit;
        if (hasMore) {
            hits = hits.subList(0, limit);
        }

        List<Map<String, Object>> items = hits.stream().map(hit -> {
            SearchService.Snippet snippet = searchService.snippet(hit.getText(), query);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", hit.getSessionId());
            item.put("sessionTitle", hit.getSessionTitle());
            item.put("messageId", hit.getMessageId());
            item.put("seq", hit.getSeq());
            item.put("role", hit.getRole());
            item.put("createdAt", hit.getCreatedAt());
            item.put("parts", snippet.parts());
            item.put("isBegin", snippet.isBegin());
            item.put("isEnd", snippet.isEnd());
            return item;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "items", items,
                "hasMore", hasMore,
                "nextOffset", offset + items.size()));
    }

    public record SearchRequest(String query, Integer limit, Integer offset) {
    }
}
