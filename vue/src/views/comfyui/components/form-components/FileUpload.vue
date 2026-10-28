<template>
  <div class="file-upload">
    <el-form-item
      :prop="fieldKey"
      :required="false"
    >
      <template #label>
        <div class="label-with-tooltip">
          <span v-if="formItem.required" class="req-star">*</span>
          <span>{{ formItem.tips }}</span>
          <el-tooltip v-if="formItem.template" :content="formItem.template" placement="top">
            <el-icon class="tooltip-icon">
              <QuestionFilled />
            </el-icon>
          </el-tooltip>
        </div>
      </template>
      <div class="upload-container">
        <!-- 如果没有文件，显示上传区域 -->
        <div v-if="!modelValue" class="upload-wrapper" @click="handleUploadClick">
          <el-upload
            ref="uploadRef"
            :http-request="customUpload"
            :accept="acceptTypes"
            :limit="1"
            :before-upload="beforeUpload"
            :show-file-list="false"
            :disabled="uploading || !canUpload"
            class="file-uploader"
          >
            <div class="upload-trigger">
              <el-icon class="upload-icon">
                <component :is="getUploadIcon()" />
              </el-icon>
              <div class="upload-text">{{ getUploadText() }}</div>
              <div class="upload-hint">{{ getUploadHints() }}</div>
            </div>
          </el-upload>
        </div>
        
        <!-- 上传进度遮罩层 -->
        <div v-if="uploading" class="upload-progress-overlay">
          <div class="progress-text">{{ Math.round(uploadProgress) }}%</div>
        </div>

        <!-- 如果有文件，显示预览 -->
        <div v-if="modelValue && !uploading" class="file-preview">
          <img 
            v-if="isImageType" 
            :src="modelValue" 
            alt="预览图片"
            class="preview-image"
          />
          <div v-else class="file-placeholder">
            <el-icon class="file-icon">
              <component :is="getUploadIcon()" />
            </el-icon>
            <div class="file-name">已上传文件</div>
          </div>

          <!-- 预览图右上角操作按钮：裁剪 / 重新上传 -->
          <div class="preview-actions">
            <button
              v-if="isImageType"
              type="button"
              class="pa-btn"
              title="裁剪图片"
              @click.stop="openCrop"
            >
              <el-icon class="pa-icon"><Crop /></el-icon>
              <span>裁剪</span>
            </button>
            <el-upload
              :http-request="customUpload"
              :accept="acceptTypes"
              :before-upload="beforeUpload"
              :show-file-list="false"
              class="pa-upload"
            >
              <button type="button" class="pa-btn" title="重新上传">
                <el-icon class="pa-icon"><RefreshRight /></el-icon>
                <span>重新上传</span>
              </button>
            </el-upload>
          </div>
        </div>
      </div>
    </el-form-item>

    <!-- 全屏裁剪弹窗 -->
    <ImageCropper
      v-if="isImageType"
      v-model:visible="cropVisible"
      :src="modelValue"
      :busy="cropUploading"
      @confirm="handleCropped"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElFormItem, ElUpload, ElIcon, ElNotification, ElTooltip } from 'element-plus'
import { Picture, VideoCamera, Microphone, QuestionFilled, Crop, RefreshRight } from '@element-plus/icons-vue'
import type {  UploadFiles, UploadRequestOptions } from 'element-plus'
import { ossApi } from '@/api/oss/oss'
import { WorkflowFormTypeEnum } from '@/enums'
import { useAuthStore } from '@/stores'
import emitter, { OPEN_AUTH_DIALOG } from '@/utils/eventBusUtil'
import ImageCropper from './ImageCropper.vue'

/**
 * 上传前压缩图片：长边限制 2048px、JPEG 质量 0.9
 * 目的：手机原图动辄 3-8MB，压缩后约 200-400KB，上传/预览快 20 倍
 */
async function compressImage(file: File): Promise<File> {
  if (!file.type.startsWith('image/')) return file
  if (file.type === 'image/gif') return file
  if (file.size < 600 * 1024) return file // 小于 600KB 不处理
  try {
    const bitmap = await createImageBitmap(file)
    const maxSide = 2048
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
    const w = Math.round(bitmap.width * scale)
    const h = Math.round(bitmap.height * scale)
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) return file
    ctx.drawImage(bitmap, 0, 0, w, h)
    const blob: Blob | null = await new Promise((resolve) =>
      canvas.toBlob((b) => resolve(b), 'image/jpeg', 0.9)
    )
    if (!blob) return file
    const name = file.name.replace(/\.[^.]+$/, '') + '.jpg'
    return new File([blob], name, { type: 'image/jpeg' })
  } catch (e) {
    console.warn('图片压缩失败，使用原图', e)
    return file
  }
}


interface Props {
  formItem: {
    inputs: string
    nodeKey: string
    tips: string
    type: string
    required: boolean
    options: string
    template?: string | null
    size?: number
  }
  modelValue: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:model-value': [value: string]
}>()

const uploadRef = ref()
const fileList = ref<UploadFiles>([])

// 裁剪弹窗状态
const cropVisible = ref(false)
const cropUploading = ref(false)

// 是否为图片类型（仅图片支持裁剪）
const isImageType = computed(() => props.formItem.type === WorkflowFormTypeEnum.IMAGE_UPLOAD)

// 上传进度控制
const uploading = ref(false)
const uploadProgress = ref(0)

// 控制是否可以上传（用于权限控制）
const canUpload = ref(true)

const fieldKey = computed(() => {
  return `${props.formItem.nodeKey}_${props.formItem.inputs}`
})

// 自定义上传方法
const customUpload = async (options: UploadRequestOptions) => {
  try {
    const { file, onProgress, onSuccess} = options
    
    // 开始上传，显示进度
    uploading.value = true
    uploadProgress.value = 0
    
    // 模拟进度更新
    const progressInterval = setInterval(() => {
      if (uploadProgress.value < 90) {
        uploadProgress.value += Math.random() * 10
        // 调用 Element Plus 的进度回调
        onProgress?.({ percent: uploadProgress.value } as any)
      }
    }, 200)
    
    // 调用我们的 OSS API
    const compressed = await compressImage(file as File)
    const result = await ossApi.uploadFile({ file: compressed })
    
    // 清除进度定时器
    clearInterval(progressInterval)
    
    // 完成进度
    uploadProgress.value = 100
    onProgress?.({ percent: 100 } as any)
    
    // 短暂延迟显示完成状态
    setTimeout(() => {
      uploading.value = false
      uploadProgress.value = 0
      
      // 上传成功，调用成功回调
      onSuccess?.(result)
      
      // 更新表单值
      emit('update:model-value', result)
      
      ElNotification.success({
        title: '成功',
        message: '文件上传成功'
      })
    }, 500)
    
  } catch (error) {
    console.error('Upload error:', error)
    
    // 重置上传状态
    uploading.value = false
    uploadProgress.value = 0
    
    // 创建符合 UploadAjaxError 接口的错误对象
    const uploadError = {
      name: 'UploadError',
      status: 500,
      method: 'POST',
      url: '/upload/file',
      message: error instanceof Error ? error.message : '上传失败'
    }
    options.onError?.(uploadError)
    ElNotification.error({
      title: '错误',
      message: '文件上传失败，请重试'
    })
  }
}

// 打开裁剪弹窗
const openCrop = () => {
  if (!props.modelValue || cropUploading.value) return
  cropVisible.value = true
}

// 裁剪结果上传并用新 URL 覆盖表单值（复用同一上传接口）
const handleCropped = async (blob: Blob) => {
  if (cropUploading.value) return

  const sizeLimit = getFileSizeLimit()
  if (blob.size / 1024 / 1024 >= sizeLimit) {
    ElNotification.error({
      title: '错误',
      message: `裁剪后的图片超过 ${sizeLimit}MB，请缩小裁剪范围后重试`
    })
    return
  }

  cropUploading.value = true
  try {
    const file = new File([blob], `crop_${Date.now()}.png`, { type: 'image/png' })
    const url = await ossApi.uploadFile({ file })
    emit('update:model-value', url)
    cropVisible.value = false
    ElNotification.success({
      title: '成功',
      message: '裁剪已完成'
    })
  } catch (error) {
    console.error('Crop upload error:', error)
    ElNotification.error({
      title: '错误',
      message: '裁剪后的图片上传失败，请重试'
    })
  } finally {
    cropUploading.value = false
  }
}

const acceptTypes = computed(() => {
  switch (props.formItem.type) {
    case WorkflowFormTypeEnum.IMAGE_UPLOAD:
      return 'image/*'
    case WorkflowFormTypeEnum.AUDIO_UPLOAD:
      return 'audio/*'
    case WorkflowFormTypeEnum.VIDEO_UPLOAD:
      return 'video/*'
    default:
      return '*'
  }
})

// 获取上传图标
const getUploadIcon = () => {
  switch (props.formItem.type) {
    case WorkflowFormTypeEnum.IMAGE_UPLOAD:
      return Picture
    case WorkflowFormTypeEnum.AUDIO_UPLOAD:
      return Microphone
    case WorkflowFormTypeEnum.VIDEO_UPLOAD:
      return VideoCamera
    default:
      return Picture
  }
}

// 获取上传文本
const getUploadText = () => {
  switch (props.formItem.type) {
    case WorkflowFormTypeEnum.IMAGE_UPLOAD:
      return '上传图片'
    case WorkflowFormTypeEnum.AUDIO_UPLOAD:
      return '上传音频'
    case WorkflowFormTypeEnum.VIDEO_UPLOAD:
      return '上传视频'
    default:
      return '上传文件'
  }
}

// 获取上传提示
const getUploadHints = () => {
  const sizeLimit = getFileSizeLimit()
  switch (props.formItem.type) {
    case WorkflowFormTypeEnum.IMAGE_UPLOAD:
      return `JPG、PNG、GIF，≤${sizeLimit}MB`
    case WorkflowFormTypeEnum.AUDIO_UPLOAD:
      return `MP3、WAV、AAC，≤${sizeLimit}MB`
    case WorkflowFormTypeEnum.VIDEO_UPLOAD:
      return `MP4、AVI、MOV，≤${sizeLimit}MB`
    default:
      return '请选择合适格式'
  }
}

// 文件大小限制（MB）
const getFileSizeLimit = () => {
  // 如果有 size 字段，优先使用（假设 size 是以 MB 为单位）
  if (props.formItem.size) {
    return props.formItem.size
  }
  
  // 否则使用默认值
  switch (props.formItem.type) {
    case WorkflowFormTypeEnum.IMAGE_UPLOAD:
      return 10
    case WorkflowFormTypeEnum.AUDIO_UPLOAD:
      return 50
    case WorkflowFormTypeEnum.VIDEO_UPLOAD:
      return 100
    default:
      return 10
  }
}

// 上传前检查（文件大小等基础检查）
const beforeUpload = (file: File) => {
  const sizeLimit = getFileSizeLimit()
  const isValidSize = file.size / 1024 / 1024 < sizeLimit
  
  if (!isValidSize) {
    ElNotification.error({
      title: '错误',
      message: `文件大小不能超过 ${sizeLimit}MB`
    })
    return false
  }
  
  return true
}

// 处理上传点击事件，进行权限验证
const handleUploadClick = (event: Event) => {
  if (!canUpload.value) {
    // 未登录，阻止默认行为并触发登录弹窗
    event.preventDefault()
    event.stopPropagation()
    emitter.emit(OPEN_AUTH_DIALOG)
    return false
  }
  
  // 已登录，允许继续上传流程
  return true
}





// 监听登录状态变化，更新上传权限
const authStore = useAuthStore()
watch(
  () => authStore.isLoggedIn,
  (isLoggedIn) => {
    canUpload.value = isLoggedIn
  },
  { immediate: true }
)

// 监听 modelValue 变化，更新文件列表
watch(
  () => props.modelValue,
  (newValue) => {
    if (newValue && fileList.value.length === 0) {
      fileList.value = [{
        name: '已上传文件',
        url: newValue,
        uid: Date.now(),
        status: 'success'
      }]
    } else if (!newValue) {
      fileList.value = []
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.file-upload {
  width: 100%;
}

.upload-container {
  width: 148px;
  height: 148px;
  position: relative;
}

.upload-wrapper {
  width: 148px;
  height: 148px;
  cursor: pointer;
}

.upload-wrapper * {
  cursor: pointer !important;
}

/* 覆盖 Element Plus 禁用状态的鼠标指针样式 */
.file-uploader :deep(.el-upload.is-disabled) {
  cursor: pointer !important;
}

.file-uploader :deep(.el-upload.is-disabled *) {
  cursor: pointer !important;
}

.file-uploader {
  width: 148px;
  height: 148px;
}

.file-uploader :deep(.el-upload) {
  display: block;
  width: 148px;
  height: 148px;
  border: 2px dashed var(--el-border-color);
  border-radius: 12px;
  transition: all 0.3s ease;
  background: transparent;
  position: relative;
  overflow: hidden;
  cursor: pointer !important;
}

.file-uploader :deep(.el-upload:hover) {
  border-color: var(--el-color-primary);
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.upload-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  width: 100%;
  padding: 12px;
  box-sizing: border-box;
  cursor: pointer !important;
}

.upload-icon {
  font-size: 32px;
  color: var(--el-color-primary);
  margin-bottom: 8px;
  transition: transform 0.3s ease;
}

.file-uploader :deep(.el-upload:hover) .upload-icon {
  transform: scale(1.1);
}

.upload-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: center;
  line-height: 1.3;
  font-weight: 500;
}

.upload-hint {
  font-size: 10px;
  color: var(--el-text-color-placeholder);
  text-align: center;
  margin-top: 4px;
  line-height: 1.2;
}

/* 文件预览样式 */
.file-preview {
  width: 148px;
  height: 148px;
  border-radius: 12px;
  background: transparent;
  position: relative;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid var(--el-border-color);
}

.file-preview:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.15);
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 12px;
}

.file-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-primary);
  background: transparent;
}

.file-icon {
  font-size: 32px;
  margin-bottom: 8px;
  opacity: 0.8;
}

.file-name {
  font-size: 12px;
  opacity: 0.9;
  text-align: center;
}




.preview-actions {
  position: absolute;
  top: 6px;
  right: 6px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  z-index: 10;
  opacity: 0.88;
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.file-preview:hover .preview-actions {
  opacity: 1;
  transform: translateY(0);
}

.pa-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 22px;
  padding: 0 7px;
  border: 1px solid rgba(124, 92, 255, 0.55);
  border-radius: 6px;
  background: rgba(17, 17, 19, 0.82);
  color: #7c5cff;
  font-size: 11px;
  line-height: 1;
  font-weight: 500;
  cursor: pointer;
  backdrop-filter: blur(4px);
  transition: all 0.16s ease;
}

.pa-btn:hover {
  background: #7c5cff;
  border-color: #7c5cff;
  color: #101014;
}

.pa-icon {
  font-size: 11px;
}

.pa-upload {
  display: inline-flex;
  line-height: 1;
}

.pa-upload :deep(.el-upload) {
  display: inline-flex;
  line-height: 1;
}

.el-form-item {
  margin-bottom: 0;
}

.el-form-item :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}

.label-with-tooltip {
  display: flex;
  align-items: center;
  gap: 4px;
}

.label-with-tooltip .req-star {
  color: var(--el-color-danger);
  margin-right: 2px;
}

.tooltip-icon {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  cursor: help;
  transition: color 0.3s ease;
}

.tooltip-icon:hover {
  color: var(--el-color-primary);
}

/* 上传进度遮罩层样式 */
.upload-progress-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 148px;
  height: 148px;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  z-index: 20;
  opacity: 0;
  animation: fadeIn 0.3s ease-in-out forwards;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.progress-text {
  font-size: 24px;
  font-weight: 600;
  color: #ffffff;
}
</style>
