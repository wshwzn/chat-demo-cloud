import clsx from 'clsx'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslate } from '../../i18n/index.js'
import { selectSession } from '../../services/chat/store.js'
import { searchConversations, type ChatSearchItem } from '../../services/search/chatSearch.js'
import { IconCloseOutline16, IconSearchOutline16 } from '../base/icons.js'
import { closeMobileSider } from '../sider/mobileSiderState.js'
import { closeChatSearch, useChatSearch } from './chatSearchState.js'
import styles from './ConversationSearch.module.css'

/** 输入防抖，与官方一致的 200ms 量级 */
const DEBOUNCE_MS = 200

/**
 * 全库搜索：输入关键词，服务端从会话正文里检索，返回带高亮片段的结果。
 * 点击结果打开对应会话并滚动到那条消息。
 */
export const ConversationSearch = () => {
  const t = useTranslate()
  const open = useChatSearch((state) => state.open)
  const [query, setQuery] = useState('')
  const [items, setItems] = useState<ChatSearchItem[]>([])
  const [status, setStatus] = useState<'idle' | 'loading' | 'done' | 'error'>('idle')
  const [activeIndex, setActiveIndex] = useState(-1)
  const inputRef = useRef<HTMLInputElement>(null)
  const abortRef = useRef<AbortController | null>(null)

  const reset = useCallback(() => {
    setQuery('')
    setItems([])
    setStatus('idle')
    setActiveIndex(-1)
  }, [])

  useEffect(() => {
    if (!open) {
      reset()
      return
    }
    inputRef.current?.focus()
  }, [open, reset])

  // 输入防抖 + 取消上一次请求
  useEffect(() => {
    if (!open) {
      return
    }
    const keyword = query.trim()
    abortRef.current?.abort()
    if (!keyword) {
      setItems([])
      setStatus('idle')
      setActiveIndex(-1)
      return
    }
    const controller = new AbortController()
    abortRef.current = controller
    setStatus('loading')
    const timer = setTimeout(() => {
      searchConversations(keyword, { signal: controller.signal })
        .then((result) => {
          if (controller.signal.aborted) {
            return
          }
          setItems(result.items)
          setActiveIndex(result.items.length > 0 ? 0 : -1)
          setStatus('done')
        })
        .catch(() => {
          if (!controller.signal.aborted) {
            setStatus('error')
          }
        })
    }, DEBOUNCE_MS)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [query, open])

  const openResult = useCallback((item: ChatSearchItem) => {
    closeChatSearch()
    closeMobileSider()
    selectSession(item.sessionId)
    // 会话是异步加载的，等消息节点出现后再滚动定位
    let tries = 0
    const locate = () => {
      const el = document.querySelector<HTMLElement>(
        `[data-message-id="${CSS.escape(item.messageId)}"]`
      )
      if (el) {
        el.scrollIntoView({ block: 'center' })
        el.classList.add(styles.flash)
        setTimeout(() => el.classList.remove(styles.flash), 1200)
        return
      }
      if (tries++ < 60) {
        requestAnimationFrame(locate)
      }
    }
    requestAnimationFrame(locate)
  }, [])

  const onKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        closeChatSearch()
        return
      }
      if (items.length === 0) {
        return
      }
      if (event.key === 'ArrowDown') {
        event.preventDefault()
        setActiveIndex((index) => (index + 1) % items.length)
      } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        setActiveIndex((index) => (index - 1 + items.length) % items.length)
      } else if (event.key === 'Enter') {
        const item = items[activeIndex]
        if (item) {
          event.preventDefault()
          openResult(item)
        }
      }
    },
    [items, activeIndex, openResult]
  )

  const hint = useMemo(() => {
    if (status === 'loading') {
      return t('chatSearchSearching')
    }
    if (status === 'error') {
      return t('chatSearchFailed')
    }
    if (status === 'done' && items.length === 0) {
      return t('chatSearchNoResult')
    }
    return null
  }, [status, items.length, t])

  if (!open) {
    return null
  }

  return (
    <div className={styles.mask} onMouseDown={closeChatSearch}>
      <div
        className={styles.panel}
        role="dialog"
        aria-modal="true"
        aria-label={t('chatSearchTitle')}
        onMouseDown={(event) => event.stopPropagation()}
        onKeyDown={onKeyDown}
      >
        <div className={styles.inputRow}>
          <span className={styles.searchIcon}>
            <IconSearchOutline16 />
          </span>
          <input
            ref={inputRef}
            className={styles.input}
            value={query}
            placeholder={t('chatSearchPlaceholder')}
            onChange={(event) => setQuery(event.target.value)}
          />
          <button
            type="button"
            className={styles.close}
            aria-label={t('close')}
            onClick={closeChatSearch}
          >
            <IconCloseOutline16 />
          </button>
        </div>

        {hint ? <div className={styles.hint}>{hint}</div> : null}

        {items.length > 0 ? (
          <ul className={styles.results}>
            {items.map((item, index) => (
              <li key={`${item.sessionId}:${item.messageId}`}>
                <button
                  type="button"
                  className={clsx(styles.result, index === activeIndex && styles.active)}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => openResult(item)}
                >
                  <div className={styles.meta}>
                    <span className={styles.sessionTitle}>{item.sessionTitle || t('untitledChat')}</span>
                    <span className={styles.role}>
                      {item.role === 'user' ? t('chatSearchHitUser') : t('chatSearchHitAssistant')}
                    </span>
                  </div>
                  <div className={styles.snippet}>
                    {!item.isBegin ? '…' : null}
                    {item.parts.map((part, partIndex) =>
                      part.highlight ? (
                        <mark key={partIndex} className={styles.mark}>
                          {part.text}
                        </mark>
                      ) : (
                        <span key={partIndex}>{part.text}</span>
                      )
                    )}
                    {!item.isEnd ? '…' : null}
                  </div>
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </div>
  )
}
