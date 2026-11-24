import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Message } from '../../services/chat/types.js'
import styles from './MessageRail.module.css'

/**
 * 右侧消息导航条（对应官方 scrollNav）。
 *
 * 结构与尺寸取自官方产物：
 * - 容器固定 34×300，右侧 16px，垂直居中；背后一层毛玻璃胶囊
 * - 每行 30px 高，右侧一根 8×2 的短横线；当前项缩放 1.5 并变蓝
 * - 悬停整条导航时展开成 240px 面板，显示每条提问的缩略文案
 */
export const MessageRail = ({
  messages,
  scrollRef,
}: {
  messages: Message[]
  scrollRef: React.RefObject<HTMLDivElement | null>
}) => {
  const [activeId, setActiveId] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)

  // 官方只给「用户提问」画点
  const marks = useMemo(() => messages.filter((message) => message.role === 'user'), [messages])

  const findNode = useCallback(
    (id: string) => {
      const root = scrollRef.current
      if (!root) {
        return null
      }
      return root.querySelector<HTMLElement>(`[data-message-id="${CSS.escape(id)}"]`)
    },
    [scrollRef]
  )

  // 滚动时取「视口中线以上最后一条提问」作为当前项
  useEffect(() => {
    const el = scrollRef.current
    if (!el) {
      return
    }
    const update = () => {
      const rect = el.getBoundingClientRect()
      const center = rect.top + rect.height / 2
      let current: string | null = null
      for (const message of marks) {
        const node = findNode(message.id)
        if (!node) {
          continue
        }
        if (node.getBoundingClientRect().top <= center) {
          current = message.id
        } else {
          break
        }
      }
      setActiveId(current ?? marks[0]?.id ?? null)
    }
    update()
    el.addEventListener('scroll', update, { passive: true })
    window.addEventListener('resize', update)
    return () => {
      el.removeEventListener('scroll', update)
      window.removeEventListener('resize', update)
    }
  }, [marks, scrollRef, findNode])

  // 让当前项始终落在导航条的可视区内
  useEffect(() => {
    const list = listRef.current
    if (!list || !activeId) {
      return
    }
    const item = list.querySelector<HTMLElement>(`[data-rail-id="${CSS.escape(activeId)}"]`)
    if (!item) {
      return
    }
    list.scrollTo({ top: item.offsetTop - list.clientHeight / 2 + item.clientHeight / 2, behavior: 'smooth' })
  }, [activeId])

  const jump = useCallback(
    (id: string) => {
      findNode(id)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    },
    [findNode]
  )

  if (marks.length < 2) {
    return null
  }

  return (
    <div
      className={styles.nav}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <div className={styles.bg} />
      <div className={open ? `${styles.wrapper} ${styles.show}` : styles.wrapper}>
        <div ref={listRef} className={styles.page}>
          {marks.map((message, index) => (
            <div
              key={message.id}
              data-rail-id={message.id}
              className={message.id === activeId ? `${styles.item} ${styles.active}` : styles.item}
              role="button"
              tabIndex={0}
              title={message.content.trim().slice(0, 80) || `提问 ${index + 1}`}
              onClick={() => jump(message.id)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault()
                  jump(message.id)
                }
              }}
            >
              <span className={styles.title}>
                {message.content.trim().replace(/\s+/g, ' ') || `提问 ${index + 1}`}
              </span>
              <span className={styles.indicator}>
                <span className={styles.line} />
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
