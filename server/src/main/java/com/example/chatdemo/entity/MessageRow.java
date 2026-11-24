package com.example.chatdemo.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 会话消息的派生投影，仅用于全文检索。
 *
 * <p>真相仍然是 sessions.doc；本表可由 doc 随时重建，不参与业务读写。
 */
@TableName("messages")
public class MessageRow {

    private String sessionId;

    private String messageId;

    private String parentId;

    private String role;

    /** 会话内顺序，从 0 开始 */
    private Integer seq;

    private Long createdAt;

    /** 参与检索的纯文本（用户提问 + 回答正文，不含思考与工具块） */
    private String text;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
