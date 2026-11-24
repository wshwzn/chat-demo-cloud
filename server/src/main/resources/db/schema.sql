-- chat-demo 会话持久化，MySQL 8/9
-- 表名与列名统一小写加下划线：Windows 的 lower_case_table_names=1，Linux 默认是 0（区分大小写）

CREATE DATABASE IF NOT EXISTS chat_demo
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE chat_demo;

CREATE TABLE IF NOT EXISTS sessions (
  id          VARCHAR(64)  NOT NULL COMMENT '会话 id，由前端生成',
  owner_id    VARCHAR(64)  NOT NULL DEFAULT 'me' COMMENT '预留多用户维度',
  title       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '会话标题，列表展示用',
  created_at  BIGINT       NOT NULL DEFAULT 0 COMMENT '毫秒时间戳',
  updated_at  BIGINT       NOT NULL DEFAULT 0 COMMENT '毫秒时间戳，列表按它倒序',
  rev         INT          NOT NULL DEFAULT 1 COMMENT '版本号，便于后续做乐观锁',
  doc         MEDIUMTEXT   NOT NULL COMMENT '完整会话 JSON，后端不解析',
  PRIMARY KEY (id),
  KEY idx_owner_updated (owner_id, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '会话文档表';

CREATE TABLE IF NOT EXISTS files (
  id           VARCHAR(64)  NOT NULL COMMENT '附件 id，同时是磁盘文件名',
  name         VARCHAR(255)          DEFAULT NULL COMMENT '原始文件名',
  content_type VARCHAR(128)          DEFAULT NULL COMMENT 'MIME',
  size         BIGINT       NOT NULL DEFAULT 0,
  created_at   BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '附件原件元信息，二进制在 app.storage-dir';

-- 全文检索用的派生投影：可由 sessions.doc 随时重建，不参与业务读写
CREATE TABLE IF NOT EXISTS messages (
  session_id  VARCHAR(64)  NOT NULL,
  message_id  VARCHAR(64)  NOT NULL,
  parent_id   VARCHAR(64)           DEFAULT NULL,
  role        VARCHAR(16)  NOT NULL,
  seq         INT          NOT NULL DEFAULT 0 COMMENT '会话内顺序',
  created_at  BIGINT       NOT NULL DEFAULT 0,
  text        MEDIUMTEXT   NOT NULL COMMENT '用户提问 + 回答正文，不含思考与工具块',
  PRIMARY KEY (session_id, message_id),
  KEY idx_session_seq (session_id, seq)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '检索投影';

-- 中文全文索引：MySQL 默认按空格分词，中文必须用 ngram
ALTER TABLE messages ADD FULLTEXT KEY ft_text (text) WITH PARSER ngram;
