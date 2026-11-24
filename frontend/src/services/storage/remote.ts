import type { Session, SessionSummary } from '../chat/types.js'
import type { ChatStorage, StoredFile } from './contract.js'

/**
 * 存储层唯一实现：服务端。
 *
 * 会话与附件都落到后端，浏览器不再保存会话数据；
 * 旧的 IndexedDB 实现见 ./migrate.ts，只用于把本地历史一次性搬到服务端。
 */

const TOKEN_HEADER = 'X-Auth-Token'

const authHeaders = (): Record<string, string> =>
  __STORAGE_TOKEN__ ? { [TOKEN_HEADER]: __STORAGE_TOKEN__ } : {}

const failure = async (method: string, path: string, res: Response): Promise<Error> => {
  let detail = ''
  try {
    detail = (await res.text()).slice(0, 200)
  } catch {
    // 读不到响应体时只保留状态码
  }
  return new Error(`${method} ${path} 失败：${res.status} ${res.statusText}${detail ? ` ${detail}` : ''}`)
}

const sessionPath = (id: string) => `/api/sessions/${encodeURIComponent(id)}`

const uploadFile = async ({ id, blob }: StoredFile): Promise<void> => {
  const form = new FormData()
  form.append('file', blob, id)
  const path = `/api/files?id=${encodeURIComponent(id)}`
  const res = await fetch(path, { method: 'POST', headers: authHeaders(), body: form })
  if (!res.ok) {
    throw await failure('POST', path, res)
  }
}

export const loadSessionSummaries = async (): Promise<SessionSummary[]> => {
  const path = '/api/sessions'
  const res = await fetch(path, { headers: authHeaders() })
  if (!res.ok) {
    throw await failure('GET', path, res)
  }
  return (await res.json()) as SessionSummary[]
}

export const loadSession = async (id: string): Promise<Session | undefined> => {
  const path = sessionPath(id)
  const res = await fetch(path, { headers: authHeaders() })
  if (res.status === 404) {
    return undefined
  }
  if (!res.ok) {
    throw await failure('GET', path, res)
  }
  const body = (await res.json()) as { doc?: string }
  if (!body.doc) {
    return undefined
  }
  return JSON.parse(body.doc) as Session
}

export const saveSession = async (session: Session, files: StoredFile[] = []): Promise<void> => {
  // 先传附件再写 doc，避免另一台设备读到引用了不存在文件的会话
  for (const file of files) {
    await uploadFile(file)
  }
  const path = sessionPath(session.id)
  const res = await fetch(path, {
    method: 'PUT',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({
      doc: JSON.stringify(session),
      title: session.title,
      createdAt: session.createdAt,
      updatedAt: session.updatedAt,
    }),
  })
  if (!res.ok) {
    throw await failure('PUT', path, res)
  }
}

export const renameSession = async (id: string, title: string): Promise<void> => {
  const path = sessionPath(id)
  const res = await fetch(path, {
    method: 'PATCH',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
  if (!res.ok) {
    throw await failure('PATCH', path, res)
  }
}

export const deleteSession = async (id: string): Promise<void> => {
  const path = sessionPath(id)
  const res = await fetch(path, { method: 'DELETE', headers: authHeaders() })
  if (!res.ok && res.status !== 404) {
    throw await failure('DELETE', path, res)
  }
}

/** 清空全部会话（服务端一并清理检索投影） */
export const deleteAllSessions = async (): Promise<void> => {
  const path = '/api/sessions'
  const res = await fetch(path, { method: 'DELETE', headers: authHeaders() })
  if (!res.ok) {
    throw await failure('DELETE', path, res)
  }
}

export const saveFiles = async (files: StoredFile[]): Promise<void> => {
  for (const file of files) {
    await uploadFile(file)
  }
}

export const loadFile = async (id: string): Promise<Blob | undefined> => {
  const path = `/api/files/${encodeURIComponent(id)}`
  const res = await fetch(path, { headers: authHeaders() })
  if (res.status === 404) {
    return undefined
  }
  if (!res.ok) {
    throw await failure('GET', path, res)
  }
  return await res.blob()
}

export const reportStorageError = (action: string) => (error: unknown) => {
  // eslint-disable-next-line no-console
  console.error(`[storage] ${action} failed`, error)
}

/**
 * 历史导入的"暂存"：单模式下没有本地库，用内存缓冲承接，
 * commit 时逐个写入服务端。失败不影响已存在的数据（整体替换语义由调用方保证）。
 */
let stagedSessions: Session[] = []

export const clearStaging = async (): Promise<void> => {
  stagedSessions = []
}

export const addStagingSession = async (session: Session): Promise<void> => {
  stagedSessions.push(session)
}

/** 附件在暂存阶段直接上传，commit 时只剩会话写入 */
export const saveStagingFiles = async (files: StoredFile[]): Promise<void> => {
  for (const file of files) {
    await uploadFile(file)
  }
}

/** 单模式下 MIME 由上传时确定，无需回填 */
// oxlint-disable-next-line no-unused-vars -- 参数为保持存储接口签名一致而保留
export const updateStagingFileTypes = async (_types: ReadonlyMap<string, string>): Promise<void> => {}

export const commitStaging = async (): Promise<SessionSummary[]> => {
  const sessions = stagedSessions
  stagedSessions = []
  // 导入语义是「覆盖全部历史」：先清空服务端，再写入本次导入的会话
  if (sessions.length > 0) {
    await deleteAllSessions()
  }
  const summaries: SessionSummary[] = []
  for (const session of sessions) {
    await saveSession(session)
    summaries.push({
      id: session.id,
      title: session.title,
      createdAt: session.createdAt,
      updatedAt: session.updatedAt,
    })
  }
  return summaries
}

/** 供 contract 类型对照，避免实现与接口漂移 */
export const storage: ChatStorage = {
  loadSessionSummaries,
  loadSession,
  saveSession,
  renameSession,
  deleteSession,
  saveFiles,
  loadFile,
  clearStaging,
  addStagingSession,
  saveStagingFiles,
  updateStagingFileTypes,
  commitStaging,
  reportStorageError,
}
