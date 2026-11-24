import { closeChatSearch, useChatSearch } from '../components/search/chatSearchState.js'
import { closeSettings, useSettingsDialog } from '../components/settings/settingsDialogState.js'
import { closeMobileSider, useMobileSider } from '../components/sider/mobileSiderState.js'

/**
 * 系统返回键的统一处理（由原生壳调用 window.__dsHandleBack）。
 *
 * 顺序：先关闭浮层（搜索 → 设置 → 侧边栏），都没有可关的才返回 'exit'，
 * 由原生决定是退到后台还是继续。
 */
export type BackResult = 'handled' | 'exit'

export const handleBack = (): BackResult => {
  if (useChatSearch.getState().open) {
    closeChatSearch()
    return 'handled'
  }
  if (useSettingsDialog.getState().open) {
    closeSettings()
    return 'handled'
  }
  if (useMobileSider.getState().open) {
    closeMobileSider()
    return 'handled'
  }
  return 'exit'
}

/** 挂到 window 上供 WebView 调用；返回清理函数 */
export const registerBackHandler = (): (() => void) => {
  const target = window as Window & { __dsHandleBack?: () => BackResult }
  target.__dsHandleBack = handleBack
  return () => {
    delete target.__dsHandleBack
  }
}
