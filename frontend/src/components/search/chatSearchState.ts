import { create } from 'zustand'

/** 全库搜索面板的开合状态；从侧栏搜索按钮或 Ctrl+K 打开 */
type ChatSearchState = { open: boolean }

export const useChatSearch = create<ChatSearchState>(() => ({ open: false }))

export const openChatSearch = () => useChatSearch.setState({ open: true })

export const closeChatSearch = () => useChatSearch.setState({ open: false })
