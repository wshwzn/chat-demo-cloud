import { IconPanelLeftOutline16, IconSearchOutline16, NewChatIcon } from '../base/icons.js'
import { WhaleLogo } from '../base/WhaleLogo.js'
import { useTranslate } from '../../i18n/index.js'
import { setSiderCollapsed } from '../../services/settings.js'
import { newChat } from '../../services/chat/store.js'
import { IconButton } from '../base/IconButton.js'
import { openChatSearch } from '../search/chatSearchState.js'
import styles from './Sider.module.css'

/** 收起后左上角：鲸鱼标志（点击展开侧栏）+ 按钮组（展开 / 搜索 / 新对话） */
export const CollapsedSider = () => {
  const t = useTranslate()
  return (
    <div className={styles.collapsed}>
      <button
        type="button"
        className={styles.collapsedLogo}
        aria-label={t('expandSiderbarButton')}
        title={t('expandSiderbarButton')}
        onClick={() => setSiderCollapsed(false)}
      >
        <WhaleLogo size={26} />
      </button>
      <div className={styles.collapsedGroup}>
        <IconButton
          size="m"
          tone="primary"
          icon={<IconPanelLeftOutline16 />}
          tooltip={t('expandSiderbarButton')}
          onClick={() => setSiderCollapsed(false)}
        />
        <IconButton
          size="m"
          tone="primary"
          icon={<IconSearchOutline16 />}
          tooltip={t('chatSearchTitle')}
          onClick={() => openChatSearch()}
        />
        <IconButton
          size="m"
          tone="primary"
          icon={<NewChatIcon />}
          tooltip={t('newChat')}
          onClick={newChat}
        />
      </div>
    </div>
  )
}
