# chat-demo-server

chat-demo 的会话持久化后端。**只存会话与附件，不代理 DeepSeek API**——前端仍然直接用浏览器里的 API Key 调 `api.deepseek.com`。

## 技术栈

- Spring Boot 3.5.16（JDK 21）
- MyBatis-Plus 3.5.17
- MySQL 9（库 `chat_demo`）

## 设计要点

`doc` 字段存前端 `Session` 对象的**完整 JSON 原文**，后端不解析、不建模。只有 `title / created_at / updated_at` 是从请求体冗余出来的列，供列表查询与排序使用。

这样做的收益：前端加字段无需后端发版；不会出现「Java 侧模型」与「TS 侧模型」两套真相；导入导出的 zip 格式可以原样复用。

## 建库

```bash
mysql -uroot -p < src/main/resources/db/schema.sql
```

表：

| 表 | 说明 |
|---|---|
| `sessions` | `id / owner_id / title / created_at / updated_at / rev / doc(MEDIUMTEXT)` |
| `files` | 附件元信息；二进制本体在 `app.storage-dir`，文件名即 id |

## 配置

`src/main/resources/application.yml`：

- `spring.datasource.*`：MySQL 连接
- `app.token`：固定口令，前端通过 `X-Auth-Token` 携带；置空则不校验
- `app.storage-dir`：附件目录，默认 `./upload`

## 运行

```bash
mvn spring-boot:run
# 或
mvn -DskipTests package && java -jar target/chat-demo-server-0.1.0.jar
```

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ping` | 免鉴权，用于前端「测试连接」 |
| GET | `/api/sessions` | 列表，仅 `id/title/createdAt/updatedAt`，按更新时间倒序 |
| GET | `/api/sessions/{id}` | 单个会话，含 `doc` |
| PUT | `/api/sessions/{id}` | 新建/覆盖保存；`rev` 自增，并重建该会话的检索投影 |
| PATCH | `/api/sessions/{id}` | 重命名，只改 `title` |
| DELETE | `/api/sessions/{id}` | 删除 |
| DELETE | `/api/sessions` | 删除全部会话（同时清空检索投影） |
| POST | `/api/search` | 全库全文检索 `{query, limit?, offset?}` |
| POST | `/api/search/reindex` | 重建全部会话的检索投影（补历史数据用） |
| POST | `/api/files?id=<可选>` | 上传附件（multipart，字段名 `file`），可自带 id |
| GET | `/api/files/{id}` | 下载附件，带 `Cache-Control: immutable` |

所有 `/api/**`（除 `ping`）都需要请求头 `X-Auth-Token: <app.token>`。

## 搜索是怎么做的

`messages` 表是 `sessions.doc` 的**派生投影**（用户提问 + 回答正文，不含思考与工具块），不参与业务读写，可随时由 `POST /api/search/reindex` 重建。

中文检索用 MySQL 的 `ngram` 分词器：

```sql
ALTER TABLE messages ADD FULLTEXT KEY ft_text (text) WITH PARSER ngram;
```

查询用 `MATCH ... AGAINST(? IN NATURAL LANGUAGE MODE)`，按相关性排序。高亮片段由 Java 在命中位置切三段返回（`parts[{text,highlight}]` + `isBegin/isEnd`），不依赖搜索引擎。

投影失败不影响会话保存：`doc` 始终是唯一真相。

## 前端接入

前端只有一种存储模式：**会话与附件都在服务端**（`src/services/storage/remote.ts`）。浏览器不再保存会话数据。

```bash
# 开发：rspack devServer 把 /api 代理到 8080
pnpm dev

# 生产构建（Windows 环境用 build:win，官方 build 脚本是 Unix 写法）
pnpm build:win
```

搜索入口：侧栏顶部的放大镜按钮，或 `Ctrl/Cmd + K`。输入后 200ms 防抖请求 `/api/search`，回车打开对应会话并滚动到命中的消息。

## 生产部署（nginx）

```nginx
server {
    listen 80;
    server_name your.domain;

    root /var/www/chat-demo;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        # 附件上传，默认 1m 会 413
        client_max_body_size 64m;
        proxy_request_buffering off;
    }
}
```

## 已知限制（v1）

1. **历史导入**：ZIP 导入的暂存改成了内存缓冲 + 逐条写入服务端，已接线但未逐项验证。
2. **无冲突检测**——`rev` 已入库并自增，但暂未做乐观锁校验；多设备同时编辑会以最后一次写入为准。
3. **附件不会随会话删除**——删除会话只删 `sessions` 行，`files` 行与磁盘文件保留，需要清理任务。
4. **单用户**——`owner_id` 固定为 `me`，仅预留字段。
5. **无离线队列**——写失败会提示并保留重试入口，但没有后台重放。
6. **搜索是字面匹配**——ngram 分词不懂同义词与拼音，拼错字搜不到；相关性排序比搜索引擎粗糙。
