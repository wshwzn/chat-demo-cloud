/**
 * 存储层入口：只有服务端实现（见 ./remote.ts）。
 *
 * 这里不再有 local/server 两种模式——chat-demo 的会话数据统一存在后端，
 * 浏览器只作临时缓存。旧浏览器数据的迁移需另行处理。
 */
export * from './remote.js'
export type { StoredFile } from './contract.js'
