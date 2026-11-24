# 部署说明

三个部分各归各位：**前端静态文件**交给 nginx 直接发，**后端**跑容器，**Nginx** 负责同源反代和口令注入。

```
浏览器 ──/──▶ nginx ──静态文件──▶ dist/
        └─/api─▶ nginx（注入 X-Auth-Token）──▶ 后端 :8080 ──▶ MySQL
```

## 为什么前端不配 API 地址

前端所有请求都是相对路径 `/api/...`，和后端同源，因此：

- 没有跨域问题，不需要 CORS 预检
- 前端走 https 时不会触发混合内容拦截
- 构建产物换域名不用重新打包

## 第一步：数据库 + 后端

```bash
# 1) 构建 jar（需要 JDK 21 + Maven）
cd ../server
mvn -DskipTests package

# 2) 配置环境变量
cd ../deploy
cp ../.env.example ../.env
vi ../.env                       # 填 MySQL 密码和 APP_TOKEN

# 3) 启动
docker compose up -d
docker compose logs -f backend   # 看到 Started ChatDemoApplication 就绪
```

`docker compose` 会自动把 `server/src/main/resources/db/schema.sql` 灌进 MySQL，
建出 `sessions` / `files` / `messages` 三张表（含中文 ngram 全文索引）。

自检：

```bash
curl -s http://127.0.0.1:8080/api/ping
# {"time":...,"ok":true,"service":"chat-demo-server"}
```

> 如果你已经有 MySQL，跳过 compose 里的 mysql 服务，把 `SPRING_DATASOURCE_*`
> 指过去，再手动执行 `mysql -uroot -p < server/src/main/resources/db/schema.sql`。

## 第二步：前端构建

```bash
cd ../frontend
pnpm install
pnpm build          # 产物在 dist/
```

**不要设置 `STORAGE_TOKEN`**——口令由 nginx 注入。若本地调试没有反代，
才临时用：

```bash
STORAGE_TOKEN=dev-token pnpm dev
```

## 第三步：Nginx

1. 把 `frontend/dist/` 整个目录放到站点根目录，例如 `/www/sites/你的域名/index/`
2. 把 `nginx-chat-demo.conf.example` 复制到站点配置，替换其中的 token 和端口
3. 确认匹配规则用 **`^~ /api`**，不是普通前缀 `/api`

`^~` 的作用是「前缀命中后不再检查正则 location」。少了它，一旦站点后面加了
这类静态资源缓存规则：

```nginx
location ~ .*\.(png|jpg|css|js)$ { expires 7d; }
```

那么 `GET /api/files/xxx.png`（附件下载）会被正则抢走，nginx 去磁盘找文件 → 404。

验证：

```bash
# 同机测试要用 --resolve 带上正确 Host，否则命中默认 server 返回 404
curl -s --resolve your-domain.example.com:443:127.0.0.1 https://your-domain.example.com/api/ping
```

## 安全模型

| 项 | 做法 |
|---|---|
| DeepSeek API Key | 只存浏览器 localStorage，由浏览器直连 `api.deepseek.com`，**从不经过本后端** |
| 后端接口 | 固定口令，由 nginx 注入 `X-Auth-Token`，不出现在前端产物里 |
| 后端端口 | 只监听 `127.0.0.1`，公网只暴露 nginx |
| 数据库 | 不映射端口 |

**这个口令不是登录系统。** 它只挡「知道地址就乱调接口」的扫描器。
所有访问者看到的是同一份数据，没有账号隔离。要真正限制访问，请在 nginx 层加：

- `auth_basic`（最简单，访问时输一次账号密码）
- IP 白名单
- 或只绑到内网 / VPN

如果打算多人使用，需要自己补账号体系——当前数据表里有 `owner_id` 列，
但后端没有实现按用户过滤。

## 常见问题

**打开页面提示「对话列表加载失败」**
→ 后端返回了 401。检查 nginx 注入的 `X-Auth-Token` 与容器环境变量 `APP_TOKEN` 是否一致。

**`curl 127.0.0.1/api/ping` 返回 404**
→ 域名站点绑定了 `server_name`，请求头 Host 不匹配就落到默认 server。用 `--resolve` 或 `-H 'Host: ...'`。

**导出/导入中文搜不到**
→ 全文索引依赖 MySQL 的 ngram 分词器。确认建表语句里带了 `WITH PARSER ngram`，
且 MySQL 版本 ≥ 5.7。MySQL 8.0 自带，无需装插件。

**附件能传但打不开**
→ 检查 `APP_STORAGE_DIR` 挂载点和宿主机权限。文件二进制在本后端磁盘上，不在数据库里。