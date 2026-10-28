<template>
  <div class="comfyui-form-renderer">
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>
    
    <div v-else-if="error" class="error-container">
      <el-alert
        :title="t('comfyui.formRenderer.loadFailed')"
        :description="error"
        type="error"
        show-icon
        :closable="false"
      />
    </div>
    
    <div v-else-if="formData" class="form-container">
      <div class="form-content">
        <div>
          <el-form
          ref="formRef"
          :model="formValues"
          :rules="formRules"
          label-position="top"
          class="comfyui-form"
          :class="{ 'has-grid-cells': hasGridCells }"
          :validate-on-rule-change="false"
        >
          <div
            v-for="(formItem, index) in formData.formContainer"
            :key="index"
            class="form-item-wrapper"
            :class="{ 'grid-cell': isGridCell(formItem) }"
          >
            <!-- 文本输入框 -->
            <TextPromptInput
              v-if="formItem.type === WorkflowFormTypeEnum.TEXT_PROMPT"
              :form-item="formItem"
              :model-value="formValues[getFieldKey(formItem)]"
              @update:model-value="updateFormValue(formItem, $event)"
            />

            <!-- 色块选择（如证件照背景色） -->
            <ColorPicker
              v-else-if="formItem.type === WorkflowFormTypeEnum.COLOR_PICKER"
              :form-item="formItem"
              :model-value="formValues[getFieldKey(formItem)]"
              @update:model-value="updateFormValue(formItem, $event)"
            />

            <!-- 图片涂抹组件 -->
            <MaskScribble
              v-else-if="formItem.type === WorkflowFormTypeEnum.IMAGE_SCRIBBLE"
              :form-item="formItem"
              :model-value="formValues[getFieldKey(formItem)]"
              @update:model-value="updateFormValue(formItem, $event)"
            />

            <!-- 文件上传 -->
            <FileUpload
              v-else-if="[WorkflowFormTypeEnum.IMAGE_UPLOAD, WorkflowFormTypeEnum.AUDIO_UPLOAD, WorkflowFormTypeEnum.VIDEO_UPLOAD].includes(formItem.type)"
              :form-item="formItem"
              :model-value="formValues[getFieldKey(formItem)]"
              @update:model-value="updateFormValue(formItem, $event)"
            />

            <!-- 单选下拉框 -->
            <RadioSelector
              v-else-if="formItem.type === WorkflowFormTypeEnum.RADIO_SELECTOR"
              :form-item="formItem"
              :model-value="formValues[getFieldKey(formItem)]"
              @update:model-value="updateFormValue(formItem, $event)"
            />

            <!-- 多选组件 -->
            <CheckboxSelector
              v-else-if="formItem.type === WorkflowFormTypeEnum.CHECKBOX_SELECTOR"
              :form-item="formItem"
              :model-value="formValues[getFieldKey(formItem)]"
              @update:model-value="updateFormValue(formItem, $event)"
            />
          </div>
        </el-form>
        </div>
      </div>

      <div v-if="!props.hideSubmitButton" class="form-actions">
        <el-button
          type="primary"
          size="large"
          :loading="submitting"
          v-auth="handleSubmit"
          class="submit-btn"
        >
          <span v-if="submitting">{{ t('comfyui.formRenderer.generating') }}</span>
          <span v-else>
            {{ t('comfyui.formRenderer.startGenerate') }} 
            <span class="credits-text">{{ t('comfyui.formRenderer.creditsDeduct', { credits: formData?.creditsDeducted || 0 }) }}</span>
          </span>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, nextTick } from 'vue'
import { ElForm, ElSkeleton, ElAlert, ElButton } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { comfyuiTaskApi } from '@/api/workflow-task/workflow-task'
import type { GetWorkflowInterfaceApi } from '@/api/workflow-task/types'
import { WorkflowFormTypeEnum } from '@/enums'
import TextPromptInput from './form-components/TextPromptInput.vue'
import FileUpload from './form-components/FileUpload.vue'
import MaskScribble from './form-components/MaskScribble.vue'
import RadioSelector from './form-components/RadioSelector.vue'
import ColorPicker from './form-components/ColorPicker.vue'
import CheckboxSelector from './form-components/CheckboxSelector.vue'

const { t } = useI18n()

interface Props {
  workflowId: number | null
  hideSubmitButton?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  submit: [formData: Record<string, any>]
}>()

const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const formRef = ref<InstanceType<typeof ElForm>>()
const formData = ref<GetWorkflowInterfaceApi.Result | null>(null)
const formValues = reactive<Record<string, any>>({})
const formRules = reactive<Record<string, any>>({})
// 必填校验规则默认不挂载：页面一打开就一片红非常吓人，点提交时才启用
const showRequiredRules = ref(false)



// 获取表单字段的唯一键
const getFieldKey = (formItem: any) => {
  return `${formItem.nodeKey}_${formItem.inputs}`
}

/** 选项里带 {"ui":{"layout":"grid33"}} 的表单项按 3×2 网格排列 */
const isGridCell = (formItem: any): boolean => {
  const raw = formItem?.options
  if (!raw) return false
  try {
    const parsed = JSON.parse(raw)
    return parsed?.ui?.layout === 'grid33'
  } catch {
    return false
  }
}

const hasGridCells = computed(() => {
  const list: any[] = formData?.value?.formContainer || []
  return list.some((it: any) => isGridCell(it))
})

// 更新表单值
const updateFormValue = (formItem: any, value: any) => {
  const key = getFieldKey(formItem)
  formValues[key] = value
  // 自定义控件不会自动触发 el-form 校验，这里手动同步：
  // 有值 → 重新校验把红字清掉；空值 → 清掉错误（不要一进页面就一片红，提交时仍会整体校验拦下）
  if (formRef.value) {
    nextTick(() => {
      const has = value !== '' && value !== null && value !== undefined
      if (has) {
        formRef.value?.validateField(key).catch(() => {})
      } else {
        formRef.value?.clearValidate(key)
      }
    })
  }
}

// 加载工作流接口配置
const loadWorkflowInterface = async (workflowId: number) => {
  if (!workflowId) return
  
  loading.value = true
  error.value = ''
  
  try {
    const result = await comfyuiTaskApi.reqGetWorkflowResultModelflowsInterface({
      workflowId
    })
    
    formData.value = result
    
    // 初始化表单值和验证规则
    initFormValues()
    initFormRules()

    // 刚进页面不要一片红：子组件挂载/初始化有先有后，3 秒内多次清扫校验状态
    const sweep = [0, 80, 200, 400, 700, 1000, 1500, 2000, 2500, 3000]
    sweep.forEach(ms => setTimeout(() => formRef.value?.clearValidate(), ms))
    
  } catch (err: any) {
    error.value = err.message || t('comfyui.formRenderer.loadWorkflowFailed')
    console.error('Failed to load workflow interface:', err)
  } finally {
    loading.value = false
  }
}

// 初始化表单值
const initFormValues = () => {
  if (!formData.value) return
  
  // 清空之前的值
  Object.keys(formValues).forEach(key => {
    delete formValues[key]
  })
  
  // 设置默认值
  formData.value.formContainer.forEach(formItem => {
    const key = getFieldKey(formItem)
    if (formItem.type === WorkflowFormTypeEnum.CHECKBOX_SELECTOR) {
      formValues[key] = []
    } else {
      formValues[key] = ''
    }
  })
}

// 初始化表单验证规则
const initFormRules = () => {
  if (!formData.value) return
  
  // 清空之前的规则
  Object.keys(formRules).forEach(key => {
    delete formRules[key]
  })
  
  // 设置验证规则
  formData.value.formContainer.forEach(formItem => {
    const key = getFieldKey(formItem)
    const rules: any[] = []
    
    // 必填验证
    if (formItem.required && showRequiredRules.value) {
      rules.push({
          required: true,
          message: t('comfyui.formRenderer.validation.pleaseEnter', { field: formItem.tips || t('comfyui.formRenderer.validation.thisField') }),
          trigger: ['blur', 'change']
      })
    }
    
    // 长度验证（仅针对输入类型组件）
    if (formItem.size && [WorkflowFormTypeEnum.TEXT_PROMPT].includes(formItem.type)) {
      rules.push({
        max: formItem.size,
        message: t('comfyui.formRenderer.validation.maxLength', { max: formItem.size }),
        trigger: ['blur', 'change']
      })
    }
    
    // 如果有验证规则则设置
    if (rules.length > 0) {
      formRules[key] = rules
    }
  })
}

// 处理表单提交
const handleSubmit = async () => {

  if (!formRef.value) return
  
  try {
    // 第一次提交时再启用必填校验规则
    if (!showRequiredRules.value) {
      showRequiredRules.value = true
      initFormRules()
      await nextTick()
    }

    await formRef.value.validate()
    
    submitting.value = true
    
    // 构建提交数据
    const submitData = {
      workflowId: formData.value?.workflowId,
      formData: { ...formValues }
    }
    
    emit('submit', submitData)
    
  } catch (error) {
    console.error('Form validation failed:', error)
    // 只有在验证失败时才重置submitting状态
    submitting.value = false
  }
}

// 单独的校验方法
const validateForm = async () => {
  if (!formRef.value) throw new Error('Form not ready')
  
  try {
    if (!showRequiredRules.value) {
      showRequiredRules.value = true
      initFormRules()
      await nextTick()
    }
    await formRef.value.validate()
  } catch (error) {
    const validationError = new Error('Form validation failed')
    validationError.name = 'ValidationError'
    throw validationError
  }
}

// 不包含校验的提交方法（校验已在外部完成）
const submitFormWithoutValidation = async () => {
  if(loading.value) return
  submitting.value = true
  
  try {
    // 构建提交数据
    const submitData = {
      workflowId: formData.value?.workflowId,
      formData: { ...formValues }
    }
    
    emit('submit', submitData)
    
  } catch (error) {
    console.error('Form submission failed:', error)
    throw error
  } finally {
    submitting.value = false
  }
}

// 重置表单方法
const resetForm = () => {
  if (formRef.value) {
    // 使用 Element Plus 的 resetFields 方法重置表单
    formRef.value.resetFields()
  }
  // 手动设置默认值（因为 resetFields 可能无法处理所有字段）
  if (formData.value) {
    formData.value.formContainer.forEach(formItem => {
      const key = getFieldKey(formItem)
      if (formItem.type === WorkflowFormTypeEnum.CHECKBOX_SELECTOR) {
        formValues[key] = []
      } else {
        formValues[key] = ''
      }
    })
  }
}

/**
 * 外部回填上传字段（复用上次素材）
 * 直接把 URL 写入表单值，FileUpload / ImageScribble 会以缩略图形式展示已选文件
 * 返回是否命中字段，未命中返回 false
 */
const applyUploadValue = (nodeKey: string, url: string): boolean => {
  if (!nodeKey || !url || !formData.value) return false
  const uploadTypes: string[] = [
    WorkflowFormTypeEnum.IMAGE_UPLOAD,
    WorkflowFormTypeEnum.IMAGE_SCRIBBLE,
    WorkflowFormTypeEnum.AUDIO_UPLOAD,
    WorkflowFormTypeEnum.VIDEO_UPLOAD
  ]
  let matched = false
  formData.value.formContainer.forEach(formItem => {
    if (formItem.nodeKey !== nodeKey) return
    if (!uploadTypes.includes(formItem.type)) return
    const key = getFieldKey(formItem)
    formValues[key] = url
    if (formRef.value) formRef.value.clearValidate(key)
    matched = true
  })
  return matched
}

// 暴露方法和状态给父组件调用
defineExpose({
  applyUploadValue,
  submitForm: handleSubmit,
  validateForm: validateForm,
  submitFormWithoutValidation: submitFormWithoutValidation,
  resetForm: resetForm,
  setSubmitting: (value: boolean) => {
    submitting.value = value
  }
})

// 监听 workflowId 变化
watch(
  () => props.workflowId,
  (newId) => {
    if (newId) {
      loadWorkflowInterface(newId)
    } else {
      formData.value = null
      error.value = ''
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.comfyui-form-renderer {
  height: 100%;
  display: flex;
  flex-direction: column;

}

.loading-container,
.error-container {
  padding: 20px;
}

.form-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.form-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;

}
.form-content >div{
  padding-bottom: 6px;
}

.comfyui-form {
  height: 100%;
}

.form-item-wrapper {
  margin-bottom: 24px;

}

.form-actions {
  flex-shrink: 0;
  padding-top: 10px;
  padding-left: 10px;
  padding-right: 10px;
  padding-bottom: 10px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
}

.submit-btn {
  width: 100%;
  height: 40px;
  font-size: 15px;
  font-weight: 500;
}

.credits-text {
  font-size: 12px;
  opacity: 0.8;
}

/* 使用全局滚动条样式 */

/* ===== 3×2 图片网格（人头像等多图输入） ===== */
.comfyui-form.has-grid-cells {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  column-gap: 12px;
  row-gap: 16px;
}

.comfyui-form.has-grid-cells .form-item-wrapper {
  flex: 0 0 100%;
  width: 100%;
  margin-right: 0;
}

/* 主图上传框与头像网格同宽，避免"上窄下宽" */
.comfyui-form.has-grid-cells .form-item-wrapper:not(.grid-cell) :deep(.upload-container),
.comfyui-form.has-grid-cells .form-item-wrapper:not(.grid-cell) :deep(.upload-wrapper) {
  width: 100% !important;
  max-width: 100% !important;
  height: 150px !important;
  min-height: 150px !important;
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell {
  /* 两列等分：容器宽度减去 1 个 12px 间隙后二等分，不依赖 nth-child */
  flex: 0 0 calc((100% - 12px) / 2);
  width: calc((100% - 12px) / 2);
  margin-right: 0;
  padding-bottom: 0;
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.el-form-item__label) {
  font-size: 12px;
  margin-bottom: 4px !important;
  line-height: 1.3;
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-container),
.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.image-uploader) {
  min-height: 90px;
  width: 100% !important;
}

/* 网格内：去掉虚线框，只留浅色块，视觉更干净 */
.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-container),
.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-wrapper) {
  width: 100% !important;
  height: 120px !important;
  min-height: 120px !important;
  max-height: 120px !important;
  border: none !important;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.055) !important;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-wrapper:hover),
.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-container:hover) {
  background: rgba(124, 92, 255, 0.14) !important;
  box-shadow: inset 0 0 0 1px rgba(124, 92, 255, 0.45);
}

/* 网格内标签留出呼吸空间 */
.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.el-form-item__label) {
  padding-left: 2px;
  margin-bottom: 6px !important;
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-wrapper) {
  padding: 6px 2px !important;
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-hint) {
  display: none !important;
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.el-form-item__error) {
  display: none;
}

/* 网格内上传框做紧凑版：方形小块 + 只留图标 */
.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-wrapper),
.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-trigger) {
  padding: 8px 4px !important;
  min-height: 92px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-hint) {
  display: none;
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-text) {
  font-size: 12px;
  margin-top: 4px;
}

.comfyui-form.has-grid-cells .form-item-wrapper.grid-cell :deep(.upload-icon) {
  font-size: 20px;
}

@media (max-width: 640px) {
  .comfyui-form.has-grid-cells .form-item-wrapper.grid-cell {
    width: calc(50% - 6px);
  }
}
</style>
