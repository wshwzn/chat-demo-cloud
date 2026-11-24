import { closeMobileSider, openMobileSider, useMobileSider } from './mobileSiderState.js'

/** 触发阈值（CSS 像素） */
const THRESHOLD = 60

/**
 * 起点是否在可横向滚动的容器里（代码块、宽表格等）。
 * 这些地方右滑应该滚动内容，不该把侧栏拉出来。
 */
const isHorizontallyScrollable = (target: EventTarget | null): boolean => {
  let el = target instanceof Element ? (target as HTMLElement) : null
  for (let depth = 0; el && depth < 6; depth++, el = el.parentElement) {
    const style = getComputedStyle(el)
    if (/(auto|scroll)/.test(style.overflowX) && el.scrollWidth > el.clientWidth) {
      return true
    }
  }
  return false
}

/**
 * 移动端侧边栏手势：
 * - 收起时，向右滑（任意位置）→ 展开
 * - 展开时，向左滑 → 收起
 *
 * 只在水平位移明显大于垂直位移时才响应，避免和消息列表的纵向滚动打架。
 */
export const bindMobileSwipe = (): (() => void) => {
  let startX = 0
  let startY = 0
  let tracking = false
  let handled = false

  const onTouchStart = (event: TouchEvent) => {
    handled = false
    if (event.touches.length !== 1) {
      tracking = false
      return
    }
    const open = useMobileSider.getState().open
    if (!open && isHorizontallyScrollable(event.target)) {
      tracking = false
      return
    }
    const touch = event.touches[0]
    tracking = true
    startX = touch.clientX
    startY = touch.clientY
  }

  const onTouchMove = (event: TouchEvent) => {
    if (!tracking || handled || event.touches.length !== 1) {
      return
    }
    const touch = event.touches[0]
    const dx = touch.clientX - startX
    const dy = touch.clientY - startY
    // 纵向意图优先，直接放弃本次手势
    if (Math.abs(dy) > Math.abs(dx)) {
      tracking = false
      return
    }
    if (Math.abs(dx) < THRESHOLD) {
      return
    }
    handled = true
    if (dx > 0) {
      openMobileSider()
    } else {
      closeMobileSider()
    }
  }

  const onTouchEnd = () => {
    tracking = false
  }

  window.addEventListener('touchstart', onTouchStart, { passive: true })
  window.addEventListener('touchmove', onTouchMove, { passive: true })
  window.addEventListener('touchend', onTouchEnd, { passive: true })
  window.addEventListener('touchcancel', onTouchEnd, { passive: true })

  return () => {
    window.removeEventListener('touchstart', onTouchStart)
    window.removeEventListener('touchmove', onTouchMove)
    window.removeEventListener('touchend', onTouchEnd)
    window.removeEventListener('touchcancel', onTouchEnd)
  }
}
