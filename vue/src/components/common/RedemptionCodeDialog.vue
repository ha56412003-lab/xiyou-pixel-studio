<script setup lang="ts">
import { ElDialog, ElForm, ElFormItem, ElInput, ElButton, ElNotification, type FormRules } from 'element-plus'
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import emitter, { OPEN_REDEMPTION_DIALOG } from '@/utils/eventBusUtil'
import { redemptionCodeApi } from '@/api/redemption-code/redemption-code'
import { useUserStore } from '@/stores/modules/user'

const { t } = useI18n()

const visible = ref(false)
const qrVisible = ref(true)
// 运行时路径，避免 Vite 静态解析（图片放 public/wechat-qr.png）
const qrSrc = '/wechat-qr.png'
const loading = ref(false)
const formRef = ref()
const userStore = useUserStore()

const redemptionForm = reactive({
  code: ''
})

const rules = computed<FormRules>(() => ({
  code: [
    { required: true, message: t('redemption.pleaseEnterCode'), trigger: 'blur' },
    { min: 6, message: t('redemption.codeMinLength'), trigger: 'blur' }
  ]
}))

const handleClose = () => {
  visible.value = false
  // 重置表单
  redemptionForm.code = ''
  formRef.value?.resetFields()
}

const openDialog = () => {
  visible.value = true
}

const handleSubmit = () => {
  formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true
      try {
        // 调用兑换码API
        await redemptionCodeApi.reqRedeemCode({ 
          code: redemptionForm.code 
        })
        
        ElNotification.success({
          title: t('redemption.success'),
          message: t('redemption.successMessage')
        })
        
        // 兑换成功后刷新用户积分信息
        await userStore.fetchUserCredits()
        
        handleClose()
      } catch (error: any) {
        console.log(error)
      } finally {
        loading.value = false
      }
    }
  })
}

onMounted(() => {
  emitter.on(OPEN_REDEMPTION_DIALOG, openDialog)
})

onUnmounted(() => {
  emitter.off(OPEN_REDEMPTION_DIALOG, openDialog)
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('redemption.title')"
    width="400px"
    :before-close="handleClose"
    align-center
    class="redemption-dialog"
    :append-to-body="true"
    :lock-scroll="true"
    :close-on-click-modal="false"
  >
    <div class="redemption-content">
      <el-form
        ref="formRef"
        :model="redemptionForm"
        :rules="rules"
        @submit.prevent
        label-position="top"
      >
        <el-form-item :label="t('redemption.enterCode')" prop="code">
          <el-input
            v-model="redemptionForm.code"
            :placeholder="t('redemption.enterCode')"
            size="large"
            maxlength="20"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleSubmit"
            class="submit-button"
          >
            {{ loading ? t('redemption.redeeming') : t('redemption.redeem') }}
          </el-button>
        </el-form-item>
      </el-form>
      
      <div class="tips">
        <p class="tip-text">
          <span class="tip-icon">💡</span>
          {{ t('redemption.tips') }}
        </p>
        <ul class="tip-list">
          <li>{{ t('redemption.tip1') }}</li>
          <li>{{ t('redemption.tip2') }}</li>
          <li>{{ t('redemption.tip3') }}</li>
        </ul>
      </div>

      <!-- 怎么拿到兑换码：加作者微信 -->
      <div class="get-code">
        <p class="get-code-title">怎么拿到兑换码？</p>
        <ol class="get-code-list">
          <li>扫码添加作者微信（或长按识别）</li>
          <li>告诉我们想充多少积分，微信付款后立刻发码</li>
          <li>回到这里输入兑换码，积分马上到账</li>
        </ol>
        <div class="qr-wrap">
          <img
            v-if="qrVisible"
            :src="qrSrc"
            alt="作者微信二维码"
            class="qr-img"
            @error="qrVisible = false"
          />
          <div v-else class="qr-placeholder">二维码待上传<br />先加作者微信</div>
          <span class="qr-caption">微信扫码 · 添加作者</span>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.redemption-dialog {
  :deep(.el-dialog__body) {
    padding: 20px;
  }
}

.redemption-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.submit-button {
  width: 100%;
  border-radius: 8px;
  font-weight: 600;
}

.tips {
  background-color: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 16px;
  border-left: 4px solid var(--el-color-primary);
}

.tip-text {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px 0;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.tip-icon {
  font-size: 16px;
}

.tip-list {
  margin: 0;
  padding-left: 20px;
  color: var(--el-text-color-regular);
}

.tip-list li {
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 1.5;
}

.tip-list li:last-child {
  margin-bottom: 0;
}

/* 输入框主题样式 */
:deep(.el-input) {
  --el-input-bg-color: var(--el-bg-color);
}

:deep(.el-input__wrapper) {
  background-color: var(--el-bg-color);
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

:deep(.el-input__wrapper.is-focus) {
  background-color: var(--el-bg-color);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}


</style>

<style>
/* ===== 兑换码弹窗深色化（append-to-body，teleport 到 body，需非 scoped） ===== */
.el-dialog.redemption-dialog {
  background-color: #1b1b1f;
  border: 1px solid #2a2a30;
  color: #f0f0f5;
}
.el-dialog.redemption-dialog .el-dialog__title {
  color: #f0f0f5;
}
.el-dialog.redemption-dialog .el-dialog__headerbtn .el-dialog__close {
  color: #a2a2b0;
}
.el-dialog.redemption-dialog .el-dialog__headerbtn:hover .el-dialog__close {
  color: #7c5cff;
}
.el-dialog.redemption-dialog .el-form-item__label {
  color: #d5d5de;
}
.el-dialog.redemption-dialog .el-input__wrapper {
  background-color: #111111;
  box-shadow: 0 0 0 1px #2a2a30 inset;
}
.el-dialog.redemption-dialog .el-input__inner {
  color: #f0f0f5;
}
.el-dialog.redemption-dialog .el-input__inner::placeholder {
  color: #6d6d7a;
}
.el-dialog.redemption-dialog .el-input__wrapper.is-focus {
  background-color: #111111;
  box-shadow: 0 0 0 1px #7c5cff inset;
}
.el-dialog.redemption-dialog .submit-button {
  background-color: #7c5cff;
  border-color: #7c5cff;
  color: #ffffff;
}
.el-dialog.redemption-dialog .submit-button:hover,
.el-dialog.redemption-dialog .submit-button:focus {
  background-color: #8f76ff;
  border-color: #8f76ff;
  color: #ffffff;
}
.el-dialog.redemption-dialog .tips {
  background-color: rgba(35, 35, 41, 0.85);
  border-left-color: #7c5cff;
}
.el-dialog.redemption-dialog .tip-text {
  color: #f0f0f5;
}
.el-dialog.redemption-dialog .tip-list {
  color: #a2a2b0;
}

/* 怎么拿到兑换码 */
.get-code {
  margin-top: 14px;
  padding: 16px;
  border-radius: 8px;
  background-color: rgba(35, 35, 41, 0.85);
  border-left: 4px solid #c8f542;
}
.get-code-title {
  margin: 0 0 10px 0;
  font-weight: 600;
  color: #f0f0f5;
  font-size: 14px;
}
.get-code-list {
  margin: 0;
  padding-left: 20px;
  color: #a2a2b0;
  font-size: 13px;
  line-height: 1.7;
}
.qr-wrap {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.qr-img {
  width: 116px;
  height: 116px;
  object-fit: contain;
  border-radius: 8px;
  background: #fff;
  padding: 4px;
}
.qr-placeholder {
  width: 116px;
  height: 116px;
  border-radius: 8px;
  border: 1px dashed rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 11px;
  color: #7a7a88;
  line-height: 1.4;
}
.qr-caption {
  font-size: 12px;
  color: #a2a2b0;
}
</style>