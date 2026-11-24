# 设计取舍

记录几个当时纠结过、结论不那么显然的决定，避免后续改坏。

## 1. 会话文档整体存 JSON，不在后端建模

`sessions.doc` 是 `MEDIUMTEXT`，存前端 `Session` 对象的原文。后端的 `Session` 实体
只有 `id / owner_id / title / created_at / updated_at / rev / doc` 七个字段，
**不解析 `doc` 里的消息结构**。

被否掉的方案是在 Java 侧建 `Message` / `Block` 一套模型，把消息拆表存。理由：

- 会把「同一个数据结构」维护成两份，前端加一个字段就要后端发版
- 官方导入导出的 ZIP 格式会立刻对不上
- 消息块类型（text / think / tool / file …）还在演进，拆表等于追着改

代价是后端无法按内容查询会话。这个需求由 `messages` 投影表单独解决——
两张表各司其职，doc 是唯一真相，messages 是可丢弃的索引。

## 2. `messages` 是派生投影，不是业务表

```
sessions.doc（唯一真相）
      │ reproject
      ▼
messages（派生，可随时整表删除重建）
```

因此：

- 不加外键。它随时可被 `DELETE` 全表后重建，外键只会增加重建时的顺序约束；
  导入上万条会话时逐行外键检查也是实打实的开销
- 清理孤儿行靠显式的 `SearchService.clear(sessionId)`，以及
  `POST /api/search/reindex` 全表重建兜底
- 想换 Elasticsearch 之类的方案，直接扔掉这张表即可，`sessions` 不受影响

## 3. 为什么是 `PRIMARY KEY (session_id, message_id)`

`message_id` 不是全局唯一 ID，而是 DeepSeek API 返回的**会话内序号**——
每个会话都从 `1` 开始。

实测一个真实库里 16832 行只有 394 个不同的 `message_id`，`1` 出现 2633 次。
所以：

- 单列主键 `PRIMARY KEY (message_id)` 会直接违反唯一约束（`ERROR 1062`）
- 必须靠 `(session_id, message_id)` 联合起来才能标识一条消息

顺带说明 `seq`：它是后端按数组位置重算的顺序号，`message_id` 不能当顺序用——
API 返回的编号会跳号（`1, 10, 11, 12...`），而 `seq` 一定是连续的。

另外 `seq` 的自增写在「文本非空」判断之外，所以内容为空的消息（只有思考块、
没有正文）也占一个序号，表里的 `seq` 会有空洞。这是故意的：`seq` 表示
「它在原始对话里的位置」，这样搜索结果才能和前端数组下标对上。

## 4. 口令由 Nginx 注入，而不是打包进前端

最初把 `STORAGE_TOKEN` 编译进前端产物，结果是：

```bash
$ grep -o 'dev-token' dist/static/js/*.js
dev-token          # 任何人都能读到，等于没有鉴权
```

改成反向代理注入后：

```nginx
location ^~ /api {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header X-Auth-Token "…";   # 覆盖客户端传来的同名头
}
```

验证过三种情况：

| 场景 | 结果 |
|---|---|
| 浏览器不带 token | 200（nginx 补上） |
| 浏览器伪造 token | 200（被 nginx 覆盖，伪造无效） |
| 绕过 nginx 直连后端 | 401 |

配套地，后端端口只绑 `127.0.0.1`，不暴露公网。

⚠️ 这**不是登录系统**。所有访问者共享同一份数据，`owner_id` 列虽然建了但后端没用它过滤。
要限制访问得上 `auth_basic` / IP 白名单。

## 5. `^~ /api` 而不是 `/api`

Nginx 的 location 优先级：

```
=  精确匹配
^~ 前缀匹配（命中后停止检查正则）
~  正则匹配
普通前缀
```

如果写成普通前缀 `/api`，nginx 匹配之后**还会继续检查所有正则 location**。
一旦站点加了这类规则：

```nginx
location ~ .*\.(png|jpg|css|js)$ { expires 7d; }
```

那么 `GET /api/files/xxx.png`（附件下载）会被正则抢走，nginx 去磁盘找文件 → 404。
加 `^~` 就是提前免疫。

## 6. 界面数值怎么来的

一开始是照截图估，偏差很大（比如消息导航条估成 20×3px，实际是 8×2px + 缩放 1.5）。

后来发现官方产物是公开的，且 JS 里有 CSS Module 的语义→哈希类名映射表：

```js
{ scrollNav:"_189b4a0", scrollNavItem:"_81e7b5e",
  itemIndicator:"ef46fbc6", indicatorLine:"fae5876e", active:"_19d617c" }
```

拿到哈希类名后去 `main.css` 里搜规则，就是精确值。

**但光查 CSS 不够**：有些样式是运行时内联注入的。例如导航条的内边距：

```js
const CY = { top: 15, inlineStart: 24, inlineEnd: 0, bottom: 15 }
page: { "--scroll-nav-page-padding": "15px 0px 15px 24px" }
```

CSS 里只写了 `padding: var(--scroll-nav-page-padding)`，变量值在 JS 里。所以正确做法是
**CSS 和 JS 一起翻**：静态样式搜 CSS，动态变量搜 JS。

## 7. 移动端字号对齐

用同一台设备、同样 dpr 的截图逐行量墨迹高度和基线间隔：

| 元素 | 官方 | 调整前 | 调整后 |
|---|---|---|---|
| 会话标题 | 40–41 物理 px | 44–45 | 41–42 |
| 分组标题 | 34 | 37 | 36 |
| 正文行距 | 54.6 图内 px（≈26 CSS） | 59.7（28 CSS） | 55.1（26 CSS） |

发现一个容易误判的结论：**正文字号其实两边一样**（墨迹高度都是 30.8px），
差的是行高和段间距。只看「每行塞多少字」会误以为是字号问题。

至于「同样字号官方每行多一个字」——那是字体差异：官方是原生 Compose 用系统字体
（小米上是 MiSans），WebView 里只能落到 Noto Sans CJK，字面更宽。
在 WebView 里换不到 MiSans，所以这是取舍：保住字形大小，接受每行少一个字。

## 8. 为什么不用 Gradle 打 APK

安卓壳只有一个 Activity，引 Gradle 会带来几百 MB 的依赖和慢得多的构建。
改用 SDK 自带的工具链直连：

```
aapt2 compile → aapt2 link → javac → d8 → zipalign → apksigner
```

`android/build.ps1` 是完整实现，约 100 行。

踩过的坑：

1. **d8 必须对整个 `classes` 目录做 dex**，只传 `MainActivity.class` 会漏掉
   匿名内部类（`MainActivity$1`），运行时炸 `NoClassDefFoundError`
2. build-tools 34 自带的 d8 是 dev 版，处理 `android.jar` 类型的**匿名子类**时
   会抛 NPE。把 `WebViewClient` / `WebChromeClient` 的子类写成**命名静态嵌套类**
   就能绕开
3. 程序化 `element.click()` 唤不出系统文件选择器——WebView 要求文件选择必须由
   **用户手势**触发。调试时要用 CDP 的 `Input.dispatchTouchEvent` 派发真实触摸

## 9. 图片选择器走系统 Photo Picker

`<input type="file">` 在 Android 上会弹系统选择器，但界面取决于 `accept`：

- `accept="image/*"` → Android 13+ 的 `ACTION_PICK_IMAGES`，自带照片网格、多选、**无需存储权限**
- `accept=".pdf,.png,.jpg,…"`（一长串扩展名）→ 文件管理器，目录列表

所以前端放了**两个**隐藏 input：图片走前者，其他文件走后者。移动端回形针弹菜单让用户选，
桌面端保持点一下直接选文件。