# chat-demo-cloud · frontend

> 本目录派生自 [deepseek-ai/chat-demo](https://github.com/deepseek-ai/chat-demo)（MIT）。
> **原版是纯静态前端，会话存浏览器 IndexedDB；本项目改造成数据存服务器**，
> 存储后端见 [../server](../server)，部署见 [../deploy](../deploy)。
> 原版 README 的 "No application backend is required" 对本目录已不适用。

保留官方那份界面，同时做了下面这些改动。

## 存储层：IndexedDB → 服务端

新增 `src/services/storage/`，把存储抽象成一份契约：

```
contract.ts    ChatStorage 接口，上层（store.ts）只依赖它
remote.ts      唯一实现：/api/sessions 与 /api/files
index.ts       入口，只导出服务端实现
```

`src/services/chat/db.ts` 仍保留，但只用于历史遗留数据的读取，不再参与日常读写。

一个容易漏的连带改动：`history/exportArchive.ts` 原本直接 `import '../db.js'`
从 IndexedDB 取附件原件，存储换成服务端后这里会读到空数据——导出 ZIP 时图片会全部丢失。
已改为走存储层 `loadFile()`。

## 新增的检索

| 文件 | 作用 |
|---|---|
| `services/search/chatSearch.ts` | 调 `POST /api/search`，维护查询状态与防抖 |
| `components/search/ConversationSearch.tsx` | 搜索浮层：结果分组、高亮片段、键盘上下选择 |
| `components/search/chatSearchState.ts` | 浮层开关状态 |

后端用 MySQL ngram 全文索引，返回的片段已带高亮标记（`parts[{text, highlight}]`），
前端只负责渲染。

## 界面改造

### 官方资源

| 文件 | 来源 |
|---|---|
| `components/base/WhaleLogo.tsx` | 官方 `favicon.svg`，单 path，走 `currentColor` |
| `components/base/WordmarkLogo.tsx` | 官方 `web-logo.svg`（143×23），10 个 path 的矢量字标 |
| `src/index.html` | 官方 favicon 以 base64 data URI 内嵌，`file://` 打开也能显示 |

侧栏标题区按官方样式给 logo 容器固定 `143×23`，不做缩放。

### 消息导航条

`components/messages/MessageRail.tsx` 复刻官方右侧 `scrollNav`：

- 只给**用户提问**画标记（回答不占位）
- 常态是 34×300 的毛玻璃胶囊，每行 30px，标记是 8×2 的短横线
- 当前项缩放 1.5 并变蓝
- 悬停展开成 240px 面板，显示每条提问的缩略文案，10ms 淡入
- 内边距 `15px 0 15px 24px`

这些数值不是估的，是从官方产物里挖出来的。做法见
[../docs/architecture.md](../docs/architecture.md#6-界面数值怎么来的)。

### 移动端

| 改动 | 说明 |
|---|---|
| `components/sider/mobileSwipe.ts` | 任意位置右滑唤出侧栏（不只屏幕边缘），左滑关闭 |
| `components/sider/Sider.tsx` | 抽屉从覆盖改为**推挤**：侧栏 `translateX` 滑入、主区同步右移，260ms `cubic-bezier(.32,.72,0,1)`，配半透明遮罩 |
| `app/backHandler.ts` | 接管安卓返回键：依次关闭 搜索 → 设置 → 侧栏，都没有才退出 |
| `components/session/MobileHeader.tsx` | 顶栏 `[菜单][标题][新建]` |
| `components/session/Welcome.tsx` | 问候语前加鲸鱼 |
| `markdown/markdown.css` | 正文行高 26px、段间距 18px（对齐官方 App） |
| `sider/SessionItem.module.css` | 侧栏标题 15px、分组标题 13px |
| `session/SessionView.module.css` | 说明文字更淡、输入框更贴底 |

尺寸都是和官方 App 在同一台设备、同一 dpr 下逐行量墨迹高度得出的。

### 附件选择

`ChatInput.tsx` 放了**两个**隐藏 input：

- `accept="image/*"` → 移动端弹 Android 13+ 系统照片选择器（网格直选、多选、无存储权限）
- `accept=<一长串扩展名>` → 系统文件管理器

移动端点回形针弹菜单「从相册选择 / 选择文件」，桌面端保持点一下直接选文件。
`base/Menu.tsx` 为此加了 `side` / `align` 参数（移动端从按钮上方右对齐展开）。

## 其他

- 标签页标题跟随当前会话（`App.tsx`），空会话回落到 `DeepSeek`
- 深浅色选中态颜色补了 `--dsw-specific-sidebar-nav-item-active-fg`
- 新增文案 key：`uploadFromGallery`、`uploadFromFiles`
- 移除了搜索浮层底部的快捷键提示条（官方没有）

## 开发

```sh
pnpm install
pnpm dev          # 需要后端在 localhost:8080（devServer 已配 /api 代理）
pnpm type
pnpm lint
pnpm build
```

生产构建**不需要**设置 `STORAGE_TOKEN`：口令由 nginx 反代注入
（见 `../deploy/nginx-chat-demo.conf.example`），打包进前端等于公开密钥。
只有本地没有反代调试时，才临时 `STORAGE_TOKEN=xxx pnpm dev`。

## 许可

沿用官方 MIT 许可，见 [LICENSE](./LICENSE)。界面与品牌资产版权归 DeepSeek 所有。