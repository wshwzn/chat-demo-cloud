export type SearchPart = { text: string; highlight: boolean }

/** 一条命中：定位到某条消息，附带服务端切好的高亮片段 */
export type ChatSearchItem = {
  sessionId: string
  sessionTitle: string
  messageId: string
  seq: number
  role: 'user' | 'assistant'
  createdAt: number
  parts: SearchPart[]
  isBegin: boolean
  isEnd: boolean
}

export type ChatSearchResult = {
  items: ChatSearchItem[]
  hasMore: boolean
  nextOffset: number
}

const TOKEN_HEADER = 'X-Auth-Token'

/** 全库检索：会话正文的 MySQL 全文索引（ngram） */
export const searchConversations = async (
  query: string,
  options: { limit?: number; offset?: number; signal?: AbortSignal } = {}
): Promise<ChatSearchResult> => {
  const { limit = 20, offset = 0, signal } = options
  const res = await fetch('/api/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(__STORAGE_TOKEN__ ? { [TOKEN_HEADER]: __STORAGE_TOKEN__ } : {}),
    },
    body: JSON.stringify({ query, limit, offset }),
    signal,
  })
  if (!res.ok) {
    throw new Error(`搜索失败：${res.status} ${res.statusText}`)
  }
  return (await res.json()) as ChatSearchResult
}
