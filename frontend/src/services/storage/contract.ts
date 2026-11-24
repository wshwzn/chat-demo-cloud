import type { Session, SessionSummary } from '../chat/types.js'

/** 附件原件：本地/远端共用同一个 id，会话 doc 里引用它 */
export type StoredFile = { id: string; blob: Blob }

/**
 * 存储层接口。上层（store.ts）只依赖这份契约；
 * 当前唯一实现是服务端（./remote.ts）。
 */
export type ChatStorage = {
  loadSessionSummaries(): Promise<SessionSummary[]>
  loadSession(id: string): Promise<Session | undefined>
  saveSession(session: Session, files?: StoredFile[]): Promise<void>
  renameSession(id: string, title: string): Promise<void>
  deleteSession(id: string): Promise<void>

  saveFiles(files: StoredFile[]): Promise<void>
  loadFile(id: string): Promise<Blob | undefined>

  /** 历史导入的暂存：单模式下由内存缓冲承接，commit 时写入服务端 */
  clearStaging(): Promise<void>
  addStagingSession(session: Session): Promise<void>
  saveStagingFiles(files: StoredFile[]): Promise<void>
  updateStagingFileTypes(types: ReadonlyMap<string, string>): Promise<void>
  commitStaging(): Promise<SessionSummary[]>

  reportStorageError(action: string): (error: unknown) => void
}
