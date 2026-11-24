package com.example.chatdemo.controller;

import com.example.chatdemo.entity.StoredFile;
import com.example.chatdemo.mapper.StoredFileMapper;
import com.example.chatdemo.storage.FileStorage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 附件原件上传下载；fileId 不可变，可长期缓存。 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorage storage;
    private final StoredFileMapper mapper;

    public FileController(FileStorage storage, StoredFileMapper mapper) {
        this.storage = storage;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "id", required = false) String id) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
        }
        // 前端自带 id 时沿用，保证会话 doc 里的引用不变；重复上传同一个 id 视为覆盖（幂等）
        String fileId = storage.isValidId(id) ? id : storage.newId();
        storage.save(fileId, file);
        mapper.insert(storage.metaFrom(fileId, file));
        return ResponseEntity.ok(Map.of("id", fileId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        if (!storage.isValidId(id)) {
            return ResponseEntity.notFound().build();
        }
        StoredFile meta = mapper.selectById(id);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = storage.resolve(id);
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = meta.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(meta.getContentType());
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().getHeaderValue())
                .body(new FileSystemResource(path));
    }
}
