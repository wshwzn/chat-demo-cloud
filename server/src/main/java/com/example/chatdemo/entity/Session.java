package com.example.chatdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 会话记录。
 *
 * <p>doc 是前端 Session 对象的完整 JSON 原文，后端不解析、不建模；
 * 其余字段是从请求体冗余出来的列，只用于列表排序与按 id 查询。
 */
@TableName("sessions")
public class Session {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String ownerId;

    private String title;

    private Long createdAt;

    private Long updatedAt;

    /** 版本号，每次保存自增，供后续做乐观锁 */
    private Integer rev;

    /** 完整会话 JSON，原样存取 */
    private String doc;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getRev() {
        return rev;
    }

    public void setRev(Integer rev) {
        this.rev = rev;
    }

    public String getDoc() {
        return doc;
    }

    public void setDoc(String doc) {
        this.doc = doc;
    }
}
