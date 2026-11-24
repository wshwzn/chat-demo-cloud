package com.example.chatdemo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.chatdemo.entity.Session;
import com.example.chatdemo.mapper.SessionMapper;
import com.example.chatdemo.search.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话读写接口。
 *
 * <p>doc 作为不透明字符串原样存取，服务端不解析会话结构；
 * title / createdAt / updatedAt 由客户端提供，仅用于列表与排序。
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    /** 单用户场景，预留 owner 维度 */
    private static final String OWNER = "me";

    private final SessionMapper mapper;
    private final SearchService searchService;

    public SessionController(SessionMapper mapper, SearchService searchService) {
        this.mapper = mapper;
        this.searchService = searchService;
    }

    /** 会话列表：只返回摘要，不带 doc */
    @GetMapping
    public List<SessionSummaryDto> list() {
        QueryWrapper<Session> qw = new QueryWrapper<Session>()
                .select("id", "title", "created_at", "updated_at")
                .orderByDesc("updated_at");
        return mapper.selectList(qw).stream()
                .map(s -> new SessionSummaryDto(s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt()))
                .toList();
    }

    /** 单个会话：带 doc 原文 */
    @GetMapping("/{id}")
    public ResponseEntity<SessionDto> get(@PathVariable String id) {
        Session s = mapper.selectById(id);
        if (s == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new SessionDto(
                s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt(), s.getRev(), s.getDoc()));
    }

    /** 新建或覆盖保存；rev 每次自增 */
    @PutMapping("/{id}")
    public ResponseEntity<?> put(@PathVariable String id, @RequestBody SaveRequest req) {
        if (req == null || req.doc() == null || req.doc().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "doc 不能为空"));
        }
        long now = System.currentTimeMillis();
        long updatedAt = req.updatedAt() == null ? now : req.updatedAt();

        Session existing = mapper.selectById(id);
        if (existing == null) {
            Session s = new Session();
            s.setId(id);
            s.setOwnerId(OWNER);
            s.setTitle(req.title() == null ? "" : req.title());
            s.setCreatedAt(req.createdAt() == null ? now : req.createdAt());
            s.setUpdatedAt(updatedAt);
            s.setRev(1);
            s.setDoc(req.doc());
            mapper.insert(s);
            searchService.reproject(s.getId(), s.getDoc());
            return ResponseEntity.ok(new SaveResponse(s.getId(), s.getRev()));
        }

        existing.setDoc(req.doc());
        if (req.title() != null) {
            existing.setTitle(req.title());
        }
        existing.setUpdatedAt(updatedAt);
        existing.setRev(existing.getRev() == null ? 1 : existing.getRev() + 1);
        mapper.updateById(existing);
        // 重建检索投影；失败不影响保存（doc 仍是唯一真相）
        searchService.reproject(existing.getId(), existing.getDoc());
        return ResponseEntity.ok(new SaveResponse(existing.getId(), existing.getRev()));
    }

    /** 重命名：只改 title，不碰 doc */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> rename(@PathVariable String id, @RequestBody TitleRequest req) {
        Session existing = mapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        existing.setTitle(req == null || req.title() == null ? "" : req.title());
        existing.setUpdatedAt(System.currentTimeMillis());
        mapper.updateById(existing);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        mapper.delete(null);
        searchService.clearAll();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        mapper.deleteById(id);
        searchService.clear(id);
        return ResponseEntity.noContent().build();
    }

    public record SessionSummaryDto(String id, String title, Long createdAt, Long updatedAt) {
    }

    public record SessionDto(String id, String title, Long createdAt, Long updatedAt, Integer rev, String doc) {
    }

    public record SaveRequest(String doc, String title, Long createdAt, Long updatedAt) {
    }

    public record TitleRequest(String title) {
    }

    public record SaveResponse(String id, Integer rev) {
    }
}
