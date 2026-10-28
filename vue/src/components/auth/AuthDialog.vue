<script setup lang="ts">
import { ElDialog, ElLink, ElTabs, ElTabPane, ElNotification } from 'element-plus'
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import emitter, { OPEN_AUTH_DIALOG, LOGIN_SUCCESS } from '@/utils/eventBusUtil'
import LoginForm from './components/LoginForm.vue'
import RegisterForm from './components/RegisterForm.vue'
import CodeLoginForm from './components/CodeLoginForm.vue'
import ForgotPasswordForm from './components/ForgotPasswordForm.vue'
import { useAuthStore } from '@/stores'
import { useUserStore } from '@/stores/modules/user'

const { t } = useI18n()
const authStore = useAuthStore()
const userStore = useUserStore()

const visible = ref(false)
const activeTab = ref('password') // 'password', 'code'
const viewState = ref('login') // 'login', 'register', 'forgotPassword'

const passwordLoginLoading = ref(false)
const codeLoginLoading = ref(false)
const registerLoading = ref(false)
const forgotPasswordLoading = ref(false)

const handleClose = () => {
  visible.value = false
}

const openDialog = () => {
  visible.value = true
  activeTab.value = 'password'
  viewState.value = 'login'
}

const handleLogin = async (loginForm: { account: string; password: string }) => {
  // 注意：这里接收的参数是 account，但 API 需要的是 email
  passwordLoginLoading.value = true
  try {
    const success = await authStore.passwordLogin(loginForm.account, loginForm.password)
    if (success) {
      // 登录成功后获取用户信息并刷新页面
      ElNotification.success({
        title: t('common.success'),
        message: t('auth.loginSuccess')
      })
      handleClose()
      await userStore.handleLoginSuccess()
      // 触发登录成功事件
      emitter.emit(LOGIN_SUCCESS)
    }
  } finally {
    passwordLoginLoading.value = false
  }
}

const handleRegister = async (registerForm: { email: string; account?: string; code: string; password: string }) => {
  registerLoading.value = true
  try {
    const success = await authStore.register(registerForm)
    if (success) {
      ElNotification.success({
        title: t('common.success'),
        message: t('auth.registerSuccess')
      })
      viewState.value = 'login'
    }
  } finally {
    registerLoading.value = false
  }
}

const handleCodeLogin = async (codeLoginForm: { email: string; code: string }) => {
  codeLoginLoading.value = true
  try {
    const success = await authStore.emailLogin(codeLoginForm.email, codeLoginForm.code)
    if (success) {
      // 登录成功后获取用户信息并刷新页面
      ElNotification.success({
        title: t('common.success'),
        message: t('auth.loginSuccess')
      })
      handleClose()
      await userStore.handleLoginSuccess()
      // 触发登录成功事件
      emitter.emit(LOGIN_SUCCESS)
    }
  } finally {
    codeLoginLoading.value = false
  }
}

const handlePasswordReset = async (data: { email: string; code: string; password: string }) => {
  forgotPasswordLoading.value = true
  try {
    const success = await authStore.forgotPassword(data)
    if (success) {
      ElNotification.success({
        title: t('common.success'),
        message: t('auth.resetPasswordSuccess')
      })
      viewState.value = 'login'
    }
  } finally {
    forgotPasswordLoading.value = false
  }
}

const handleGetLoginCode = async (email: string, password?: string) => {
  if (viewState.value === 'login' || viewState.value === 'register' || viewState.value === 'forgotPassword') {
    try {
      // 根据当前视图状态决定是否需要提供密码
      if (viewState.value === 'login') {
        // 邮箱验证码登录时不需要密码
        await authStore.getVerificationCode(email, '')
      } else {
        // 注册和忘记密码需要密码
        await authStore.getVerificationCode(email, password || '')
      }
    } catch (error) {
      console.error('Failed to send verification code:', error)
    }
  }
}

onMounted(() => {
  emitter.on(OPEN_AUTH_DIALOG, openDialog)
})

onUnmounted(() => {
  emitter.off(OPEN_AUTH_DIALOG, openDialog)
})
</script>

<template>
  <el-dialog
    v-model="visible"
    width="450px"
    :before-close="handleClose"
    align-center
    class="auth-dialog"
    :append-to-body="true"
    :lock-scroll="true"
    :close-on-click-modal="false"
  >
    <div class="auth-dialog-header">
      <img class="dialog-logo" src="/logo-xiyou.svg" alt="西柚像素工坊" />
      <div class="logo-text">
        <span class="logo-cn">西柚像素工坊</span>
        <span class="logo-en">XIYOU创意工坊</span>
      </div>
    </div>

    <div class="auth-dialog-content">
      <div v-if="viewState === 'login'">
        <el-tabs v-model="activeTab" class="login-tabs">
          <el-tab-pane :label="t('auth.passwordLogin')" name="password">
            <LoginForm @login="handleLogin" :loading="passwordLoginLoading" />
          </el-tab-pane>
          <el-tab-pane :label="t('auth.codeLogin')" name="code">
            <CodeLoginForm 
              @login-with-code="handleCodeLogin" 
              @send-code="handleGetLoginCode"
              :loading="codeLoginLoading" 
            />
          </el-tab-pane>
        </el-tabs>
        <div class="extra-actions">
          <el-link type="primary" :underline="false" style="font-size: 12px;" @click="viewState = 'forgotPassword'">
            {{ t('auth.forgotPassword') }}
          </el-link>
        </div>
      </div>
      
      <RegisterForm 
        v-else-if="viewState === 'register'" 
        @register="handleRegister" 
        @send-code="handleGetLoginCode" 
        :loading="registerLoading"
      />
      <ForgotPasswordForm 
        v-else-if="viewState === 'forgotPassword'" 
        @reset-password="handlePasswordReset" 
        @send-code="handleGetLoginCode"
        :loading="forgotPasswordLoading" 
      />

      <div class="auth-toggle">
        <span v-if="viewState === 'login'">
          {{ t('auth.noAccount') }} <el-link type="primary" @click="viewState = 'register'">{{ t('auth.registerNow') }}</el-link>
        </span>
        <span v-if="viewState === 'register'">
          {{ t('auth.hasAccount') }} <el-link type="primary" @click="viewState = 'login'">{{ t('auth.loginNow') }}</el-link>
        </span>
        <span v-if="viewState === 'forgotPassword'">
          <el-link type="primary" @click="viewState = 'login'">{{ t('auth.backToLogin') }}</el-link>
        </span>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.auth-dialog {
  :deep(.el-dialog__body) {
    padding: 0;
  }
}

.auth-dialog-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 18px 0;
  padding-bottom: 6px;
  background: var(--el-bg-color);
}

.dialog-logo {
  width: 46px;
  height: 46px;
  margin-bottom: 8px;
  border-radius: 12px;
}

.logo-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  margin: 0;
  text-align: center;
}

.logo-cn {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: var(--el-text-color-primary);
  line-height: 1.25;
}

.logo-en {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #7c5cff;
}

.auth-dialog-content {
  width: 100%;
  padding: 8px 20px 16px;
}
.auth-dialog-content :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.auth-dialog-content :deep(.el-form-item) {
  margin-bottom: 12px;
}

.login-tabs {
  width: 100%;
  :deep(.el-tabs__nav) {
    width: 100%;
    display: flex;
  }
  :deep(.el-tabs__item) {
    flex: 1;
    text-align: center;
    font-size: 16px;
  }
}

.auth-actions {
  margin: 16px 0;
  width: 100%;
  text-align: right;
}

.login-actions {
  display: flex;
  justify-content: space-between;
  width: 100%;
}

.extra-actions {
  margin-top: 0px;
  text-align: right;
  width: 100%;
}

.auth-toggle {
  margin-top: 16px;
  text-align: center;
  width: 100%;
  font-size: 14px;
}

.auth-toggle .el-link {
  font-size: 14px;
  vertical-align: baseline;
}
</style>
<style>
/* 登录/注册弹窗深色化（append-to-body，需全局样式；变量级覆盖，压得住 EP 默认白底） */
html .el-dialog.auth-dialog {
  --el-color-primary: #7c5cff;
  --el-bg-color: #17171c;
  --el-bg-color-overlay: #17171c;
  --el-dialog-bg-color: #17171c;
  --el-text-color-primary: #f0f0f5;
  --el-text-color-regular: #c8c8d0;
  --el-text-color-secondary: #8b8b99;
  --el-border-color: #2f2f38;
  --el-border-color-light: #2a2a32;
  --el-border-color-lighter: #26262e;
  --el-fill-color-blank: #1b1b21;
  --el-input-bg-color: #1b1b21;
  --el-input-border-color: #33333d;
  --el-input-text-color: #f0f0f5;
  --el-input-placeholder-color: #6f6f7d;
  background: #17171c;
  border: 1px solid #2a2a32;
  border-radius: 18px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.65);
}
/* 防拉伸硬约束：无论父容器怎么排，弹窗都按内容高度、居中、不顶格 */
html .el-dialog.auth-dialog {
  height: auto !important;
  max-height: 88vh !important;
  min-height: 0 !important;
  align-self: center !important;
  flex: 0 0 auto !important;
  margin: 0 auto !important;
  top: 0 !important;
}
html .el-overlay-dialog:has(.auth-dialog) {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 24px 0 !important;
}
html .el-dialog.auth-dialog .el-dialog__body { height: auto !important; max-height: none !important; }
html .el-dialog.auth-dialog .el-dialog__header { padding-bottom: 0; }
html .el-dialog.auth-dialog .el-dialog__headerbtn .el-dialog__close { color: #8b8b99; }
html .el-dialog.auth-dialog .el-dialog__headerbtn:hover .el-dialog__close { color: #7c5cff; }
html .el-dialog.auth-dialog .auth-dialog-header { background: transparent; }
html .el-dialog.auth-dialog .el-tabs__item { color: #8b8b99; font-weight: 600; }
html .el-dialog.auth-dialog .el-tabs__item.is-active { color: #a78bfa; }
html .el-dialog.auth-dialog .el-tabs__active-bar { background: #7c5cff; }
html .el-dialog.auth-dialog .el-tabs__nav-wrap::after { background: #26262e; }
html .el-dialog.auth-dialog .el-button--primary {
  background: linear-gradient(135deg, #7c5cff 0%, #6b4bff 100%);
  border: none; height: 44px; border-radius: 10px; font-weight: 700;
  box-shadow: 0 8px 22px rgba(124, 92, 255, 0.35);
}
html .el-dialog.auth-dialog .el-button--primary:hover { filter: brightness(1.12); }
html .el-dialog.auth-dialog .el-checkbox__label { color: #a8a8b6; }
/* 压制 Chrome 自动填充把输入框刷白（粘贴/自动填充账号密码时触发） */
html .el-dialog.auth-dialog input:-webkit-autofill,
html .el-dialog.auth-dialog input:-webkit-autofill:hover,
html .el-dialog.auth-dialog input:-webkit-autofill:focus,
html .el-dialog.auth-dialog textarea:-webkit-autofill,
html .el-dialog.auth-dialog select:-webkit-autofill {
  -webkit-text-fill-color: #f0f0f5 !important;
  -webkit-box-shadow: 0 0 0 1000px #1b1b21 inset !important;
  box-shadow: 0 0 0 1000px #1b1b21 inset !important;
  caret-color: #f0f0f5;
  transition: background-color 9999s ease-in-out 0s;
}
html .el-dialog.auth-dialog .el-input__inner {
  background-color: transparent !important;
  color: #f0f0f5 !important;
}
html .el-dialog.auth-dialog .el-input__wrapper {
  background-color: #1b1b21 !important;
  box-shadow: 0 0 0 1px #33333d inset !important;
}
html .el-dialog.auth-dialog .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px #7c5cff inset !important;
}
html .el-dialog.auth-dialog .el-input__inner::placeholder { color: #6f6f7d !important; }
html .el-dialog.auth-dialog .el-link.el-link--primary { color: #a78bfa; }
</style>
