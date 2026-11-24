package com.example.chatdemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.chatdemo.entity.MessageRow;
import com.example.chatdemo.entity.SearchHit;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MessageMapper extends BaseMapper<MessageRow> {

    /** MySQL 全文检索；ngram 分词器负责中文切分，score 用自然语言模式的相关性 */
    @Select("""
            SELECT m.session_id   AS sessionId,
                   s.title        AS sessionTitle,
                   m.message_id   AS messageId,
                   m.seq          AS seq,
                   m.role         AS role,
                   m.created_at   AS createdAt,
                   m.text         AS text,
                   MATCH(m.text) AGAINST(#{q} IN NATURAL LANGUAGE MODE) AS score
              FROM messages m
              JOIN sessions s ON s.id = m.session_id
             WHERE MATCH(m.text) AGAINST(#{q} IN NATURAL LANGUAGE MODE)
             ORDER BY score DESC, m.created_at DESC
             LIMIT #{limit} OFFSET #{offset}
            """)
    List<SearchHit> search(@Param("q") String q, @Param("limit") int limit, @Param("offset") int offset);
}
