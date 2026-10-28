<template>
  <header class="site-header">
    <div class="header-inner">
      <!-- 左侧：品牌 -->
      <div class="header-left">
        <button
          v-if="showBackButton"
          class="back-btn"
          type="button"
          aria-label="返回"
          @click="handleBackClick"
        >
          <el-icon :size="16"><ArrowLeft /></el-icon>
        </button>

        <router-link to="/comfyui" class="brand">
          <img class="brand-logo" src="/logo-xiyou.svg" alt="西柚像素工坊" />
          <span class="brand-text">
            <span class="brand-cn">西柚像素工坊</span>
            <span class="brand-en">XIYOU创意工坊</span>
          </span>
        </router-link>
      </div>

      <!-- 中间：主导航 -->
      <nav class="site-nav">
        <button
          v-for="nav in navItems"
          :key="nav.key"
          type="button"
          class="nav-item"
          :class="{ active: activeKey === nav.key }"
          @click="handleNavClick(nav)"
        >
          <img v-if="typeof nav.icon === 'string'" :src="nav.icon" class="nav-svg" alt="" />
          <el-icon v-else :size="16" class="nav-icon"><component :is="nav.icon" /></el-icon>
          <span class="nav-label">{{ nav.label }}</span>
        </button>
      </nav>

      <!-- 右侧：功能入口 -->
      <div class="navbar-right">
        <el-tooltip :content="t('navbar.switchLanguage')" placement="bottom" :show-after="500">
          <div class="navbar-item">
            <LanguageSwitcher />
          </div>
        </el-tooltip>

        <el-tooltip :content="t('navbar.myCredits')" placement="bottom" :show-after="500" v-if="isLoggedIn">
          <div class="navbar-item">
            <UserCreditsBadge />
          </div>
        </el-tooltip>

        <el-tooltip :content="t('navbar.taskManagement')" placement="bottom" :show-after="500" v-if="isLoggedIn">
          <div class="navbar-item">
            <TaskPanel />
          </div>
        </el-tooltip>

        <template v-if="isLoggedIn">
          <el-tooltip :content="t('navbar.userMenu')" placement="bottom" :show-after="500">
            <div class="navbar-item">
              <UserDropdown />
            </div>
          </el-tooltip>
        </template>
        <el-tooltip :content="t('navbar.clickToLogin')" placement="bottom" :show-after="500" v-else>
          <button class="modern-login-button" type="button" @click="handleLoginClick">
            <span class="login-text">{{ t('auth.loginNow') }}</span>
          </button>
        </el-tooltip>
      </div>
    </div>

    <!-- 帮助弹窗 -->
    <el-dialog v-model="helpVisible" width="440px" title="帮助中心" align-center append-to-body class="help-dialog">
      <div class="help-body">
        <p class="help-brand">西柚像素工坊 · XIYOU创意工坊</p>
        <ul class="help-list">
          <li><strong>怎么用？</strong>进入摄影棚挑一个项目，上传照片或填一句话，点「立即拍摄」，稍等片刻即可取片。</li>
          <li><strong>积分怎么算？</strong>每个工作流按次扣积分，卡片与详情页都会标注消耗。</li>
          <li><strong>作品在哪？</strong>生成完成后可在「我的相册」里查看、下载和重新制作。</li>
          <li><strong>需要帮助？</strong>遇到问题可在「我的」菜单里反馈，我们会尽快回复。</li>
        </ul>
      </div>
    </el-dialog>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, QuestionFilled } from '@element-plus/icons-vue'
import LanguageSwitcher from '@/components/common/LanguageSwitcher.vue'
import TaskPanel from '@/components/common/TaskPanel.vue'
import UserDropdown from '@/components/common/UserDropdown.vue'
import UserCreditsBadge from '@/components/common/UserCreditsBadge.vue'
import emitter, { OPEN_REDEMPTION_DIALOG } from '@/utils/eventBusUtil'
import { withAuth } from '@/utils/authGuard'
import { useUserStore } from '@/stores/modules/user'
import { useAuthStore } from '@/stores/modules/auth'
import { Role } from '@/enums/user'
import CreativeIcon from '@/assets/svg/creative.svg'
import AssetsIcon from '@/assets/svg/assets.svg'
import ProfileIcon from '@/assets/svg/profile.svg'
import ExchangeCodeIcon from '@/assets/svg/exchange-code.svg'
import SystemManagementIcon from '@/assets/svg/system-management.svg'

const { t } = useI18n()
const emit = defineEmits(['login-click'])

const route = useRoute()
const router = useRouter()

const userStore = useUserStore()
const authStore = useAuthStore()

const isLoggedIn = computed(() => !!authStore.token && !!userStore.userInfo)

const showBackButton = computed(() => route.meta?.backButton?.show === true)

interface NavItem {
  key: string
  label: string
  icon: unknown
  to?: string
  requiresAdmin?: boolean
}

const isAdmin = computed(() => useUserStore().userInfo?.role === Role.ADMIN)

const navItems = computed<NavItem[]>(() => {
  const items: NavItem[] = [
    { key: 'workbench', label: t('layouts.sidebar.menu.onlineGeneration'), icon: CreativeIcon, to: '/comfyui' },
    { key: 'works', label: t('layouts.sidebar.menu.myWorks'), icon: AssetsIcon, to: '/works' },
    { key: 'profile', label: t('layouts.sidebar.menu.profile'), icon: ProfileIcon, to: '/profile' },
    { key: 'redemption', label: t('layouts.sidebar.menu.redemptionCode'), icon: ExchangeCodeIcon },
    { key: 'help', label: '帮助', icon: QuestionFilled }
  ]
  if (isAdmin.value) {
    items.splice(4, 0, { key: 'system', label: t('layouts.sidebar.menu.systemManagement'), icon: SystemManagementIcon, to: '/system' })
  }
  return items
})

const activeKey = computed(() => {
  if (route.path.startsWith('/works')) return 'works'
  if (route.path.startsWith('/profile')) return 'profile'
  if (route.path.startsWith('/system')) return 'system'
  if (route.path.startsWith('/comfyui')) return 'workbench'
  return ''
})

const helpVisible = ref(false)

const handleNavClick = (nav: NavItem) => {
  if (nav.key === 'help') {
    helpVisible.value = true
    return
  }
  if (nav.key === 'redemption') {
    withAuth(() => {
      emitter.emit(OPEN_REDEMPTION_DIALOG)
    }, t('layouts.authRequired.redemptionCode'))
    return
  }
  if (nav.to && route.path !== nav.to) {
    router.push(nav.to)
  }
}

const handleBackClick = () => {
  const backTo = route.meta?.backButton?.to as string | undefined
  if (backTo) {
    router.push(backTo)
  } else if (window.history.length > 1) {
    router.go(-1)
  } else {
    router.push('/comfyui')
  }
}

const handleLoginClick = () => {
  emit('login-click')
}
</script>

<style scoped>
.site-header {
  flex-shrink: 0;
  height: 64px;
  background: rgba(11, 11, 15, 0.92);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid #1e1e26;
  position: relative;
  z-index: 20;
}

.header-inner {
  max-width: 1440px;
  height: 100%;
  margin: 0 auto;
  padding: 0 32px;
  display: flex;
  align-items: center;
  gap: 24px;
}

/* 品牌 */
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.back-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: #17171d;
  border: 1px solid #26262e;
  color: #c9c9d2;
  cursor: pointer;
  transition: all 0.18s ease;
}

.back-btn:hover {
  border-color: #7c5cff;
  color: #ffffff;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  min-width: 0;
}

.brand-logo {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 7px;
}

.brand-text {
  display: flex;
  align-items: baseline;
  gap: 8px;
  white-space: nowrap;
}

.brand-cn {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #f4f4f7;
}

.brand-en {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.14em;
  color: #7f7f8c;
  text-transform: uppercase;
}

/* 主导航 */
.site-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: #a3a3b0;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.18s ease, background 0.18s ease, border-color 0.18s ease;
}

.nav-item:hover {
  color: #ececf2;
  background: #1a1a21;
}

.nav-item.active {
  color: #ffffff;
  background: rgba(124, 92, 255, 0.16);
  border-color: rgba(124, 92, 255, 0.45);
}

.nav-item.active .nav-icon {
  color: #a78bfa;
}

.nav-icon {
  color: inherit;
  display: inline-flex;
}

.nav-svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

/* 右侧功能 */
.navbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  padding-left: 18px;
  margin-left: 6px;
  border-left: 1px solid #1f1f27;
}

.navbar-item {
  display: flex;
  align-items: center;
}

.modern-login-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  padding: 0 18px;
  border: none;
  border-radius: 10px;
  background: #7c5cff;
  color: #ffffff;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.modern-login-button:hover {
  background: #6a49f2;
  box-shadow: 0 6px 18px rgba(124, 92, 255, 0.35);
}



.help-body {
  padding: 0 4px 6px;
}

.help-brand {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 700;
  color: #7c5cff;
}

.help-list {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.9;
}

/* 响应式 */
@media (max-width: 1100px) {
  .brand-en {
    display: none;
  }
  .nav-item {
    padding: 0 11px;
  }
}

@media (max-width: 860px) {
  .header-inner {
    padding: 0 16px;
    gap: 12px;
  }
  .nav-label {
    display: none;
  }
  .nav-item {
    padding: 0 10px;
  }
  .navbar-right {
    gap: 6px;
    padding-left: 10px;
  }
}

@media (max-width: 520px) {
  .brand-cn {
    font-size: 15px;
  }
  .modern-login-button .login-text {
    display: none;
  }
  .modern-login-button {
    width: 36px;
    padding: 0;
  }
}
</style>

<style>
/* 帮助中心弹窗深色化（append-to-body，必须用全局样式覆盖 Element Plus 主题变量） */
html .el-dialog.help-dialog {
  --el-bg-color: #17171c;
  --el-bg-color-overlay: #17171c;
  --el-dialog-bg-color: #17171c;
  --el-text-color-primary: #f0f0f5;
  --el-text-color-regular: #c8c8d0;
  --el-border-color: #2f2f38;
  --el-border-color-light: #2a2a32;
  background: #17171c !important;
  border: 1px solid #2a2a32;
  border-radius: 16px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.65);
  height: auto !important;
  max-height: 88vh !important;
  align-self: center !important;
  margin: 0 auto !important;
}
html .el-dialog.help-dialog .el-dialog__header {
  padding-bottom: 10px;
  margin-right: 0;
  border-bottom: 1px solid #26262e;
}
html .el-dialog.help-dialog .el-dialog__title {
  color: #f0f0f5;
  font-weight: 700;
}
html .el-dialog.help-dialog .el-dialog__headerbtn .el-dialog__close { color: #8b8b99; }
html .el-dialog.help-dialog .el-dialog__headerbtn:hover .el-dialog__close { color: #7c5cff; }
html .el-dialog.help-dialog .help-body { padding: 6px 2px 10px; }
html .el-dialog.help-dialog .help-brand {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 700;
  color: #a78bfa;
}
html .el-dialog.help-dialog .help-list {
  margin: 0;
  padding-left: 18px;
  color: #c8c8d0;
  font-size: 13px;
  line-height: 1.75;
}
html .el-dialog.help-dialog .help-list li { margin-bottom: 10px; }
html .el-dialog.help-dialog .help-list strong { color: #f0f0f5; font-weight: 700; }
</style>
