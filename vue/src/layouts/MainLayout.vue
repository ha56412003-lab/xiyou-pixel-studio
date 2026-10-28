<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import emitter, { OPEN_AUTH_DIALOG, OPEN_NOTICE_ANNOUNCER } from '@/utils/eventBusUtil'


import TopNavbar from './components/TopNavbar.vue'
import AuthDialog from '@/components/auth/AuthDialog.vue'
import RedemptionCodeDialog from '@/components/common/RedemptionCodeDialog.vue'
import NoticeAnnouncer from '@/components/notice/NoticeAnnouncer.vue'

// const route = useRoute() // 左侧菜单栏下线后暂未使用

// 左侧菜单栏已下线，原 shouldHideSidebar 逻辑随之废弃


const noticeAnnouncerRef = ref<InstanceType<typeof NoticeAnnouncer> | null>(null)

const handleOpenNotice = () => {
  console.log('handleOpenNotice called, ref:', noticeAnnouncerRef.value)
  noticeAnnouncerRef.value?.manualOpen()
}

onMounted(() => {

  console.log('Registering OPEN_NOTICE_ANNOUNCER listener')
  emitter.on(OPEN_NOTICE_ANNOUNCER, handleOpenNotice)
})

onBeforeUnmount(() => {

  emitter.off(OPEN_NOTICE_ANNOUNCER, handleOpenNotice)
})

const handleLogin = () => {
  emitter.emit(OPEN_AUTH_DIALOG)
}
</script>

<template>
  <div>
    <div class="layout-container">

      <!-- 菜单已合并至顶栏 TopNavbar -->
      <div class="main-content full-width">
        <TopNavbar @login-click="handleLogin" />
        <div class="content-area">
          <router-view></router-view>
        </div>
      </div>
    </div>
    <AuthDialog />
    <RedemptionCodeDialog />
    <NoticeAnnouncer ref="noticeAnnouncerRef" />
  </div>
</template>

<style scoped>

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.layout-container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  background-color: #111111;

}

/* Sidebar Styles */
.sidebar {
  width: 200px;
  min-width: 240px;
  background-color: var(--el-bg-color);
  color: var(--el-text-color-primary);

  display: flex;
  flex-direction: column;
  padding: 20px 12px;
  transition: width 0.3s ease, background-color 0.3s ease, color 0.3s ease;

  border-right: 1px solid var(--el-border-color-dark);

}

.main-content {
  flex: 1;
  min-width: 0;
  background-color: #111111;

  color: var(--el-text-color-primary);

  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: background-color 0.3s ease, color 0.3s ease;

}

.main-content.full-width {
  width: 100vw;
}

.content-area {
  flex: 1;

  overflow-y: auto;

  height: calc(100vh - 64px);
  background-color: #111111;
}

.content-area.no-scroll {
  overflow-y: auto;
  height: auto;
  min-height: calc(100vh - 64px);
}

html[class^="theme-dark"] .login-button,
html[class*=" theme-dark"] .login-button {
  background-color: var(--custom-login-button-bg);
  color: var(--custom-login-button-text);
}

html[class^="theme-dark"] .login-button:hover,
html[class*=" theme-dark"] .login-button:hover {
  background-color: var(--custom-login-button-bg);
  filter: brightness(1.1);
}
</style>