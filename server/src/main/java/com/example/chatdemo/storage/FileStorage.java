package com.example.chatdemo.storage;

import com.example.chatdemo.entity.StoredFile;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/** 附件原件落本地磁盘，文件名即 fileId，避免路径穿越。 */
@Service
public class FileStorage {

    /** 允许前端自带 id（会话 doc 里引用的就是它），只限制为安全字符 */
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final Path root;

    public FileStorage(@Value("${app.storage-dir:./upload}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
    }

    public String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    public void save(String id, MultipartFile file) {
        Path target = resolve(id);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("保存附件失败: " + id, e);
        }
    }

    public Path resolve(String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("非法的附件 id");
        }
        Path target = root.resolve(id).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的附件路径");
        }
        return target;
    }

    public StoredFile metaFrom(String id, MultipartFile file) {
        StoredFile meta = new StoredFile();
        meta.setId(id);
        meta.setName(file.getOriginalFilename());
        meta.setContentType(file.getContentType());
        meta.setSize(file.getSize());
        meta.setCreatedAt(System.currentTimeMillis());
        return meta;
    }
}
