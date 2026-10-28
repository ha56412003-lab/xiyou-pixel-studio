<template>
  <div class="color-picker">
    <div class="cp-label">
      <span v-if="formItem.required" class="req">*</span>
      {{ formItem.tips || '选择颜色' }}
    </div>

    <div class="cp-swatches">
      <div
        v-for="c in colors"
        :key="c.value"
        class="cp-swatch"
        :class="{ active: isSelected(c.value) }"
        :title="c.value"
        @click="pick(c.value)"
      >
        <span class="chip" :style="{ background: c.value }"></span>
        <span class="name">{{ c.key }}</span>
      </div>
    </div>

    <div v-if="allowCustom" class="cp-custom">
      <span class="cp-custom-label">自定义：</span>
      <el-input
        :model-value="modelValue"
        size="small"
        placeholder="#ffffff"
        maxlength="9"
        @update:model-value="onInput"
      />
      <span class="cp-preview" :style="{ background: previewColor }"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElInput } from 'element-plus'

interface ColorItem {
  key: string
  value: string
}

const props = defineProps<{
  formItem: any
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:model-value', value: string): void
}>()

const DEFAULT_COLORS: ColorItem[] = [
  { key: '白底', value: '#ffffff' },
  { key: '蓝底', value: '#438edb' },
  { key: '红底', value: '#d9333f' },
  { key: '灰底', value: '#b9b9b9' },
  { key: '浅蓝', value: '#c7dcf0' },
  { key: '深蓝', value: '#2b4c7e' },
]

const parsed = computed(() => {
  try {
    return JSON.parse(props.formItem?.options || '{}')
  } catch {
    return {}
  }
})

const colors = computed<ColorItem[]>(() => {
  const list = parsed.value?.colors
  return Array.isArray(list) && list.length ? list : DEFAULT_COLORS
})

const allowCustom = computed(() => parsed.value?.allowCustom !== false)
const defaultValue = computed(() => parsed.value?.default || colors.value[0]?.value || '#ffffff')

const isSelected = (v: string) => (props.modelValue || defaultValue.value).toLowerCase() === v.toLowerCase()

const pick = (v: string) => emit('update:model-value', v)

const onInput = (v: string) => emit('update:model-value', v)

const previewColor = computed(() => {
  const v = (props.modelValue || '').trim()
  return /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(v) ? v : 'transparent'
})

onMounted(() => {
  // 没选过时用默认值（默认白底），保证参数一定传下去
  if (!props.modelValue) emit('update:model-value', defaultValue.value)
})
</script>

<style scoped>
.color-picker {
  width: 100%;
}
.cp-label {
  font-size: 13px;
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
}
.cp-label .req {
  color: var(--el-color-danger);
  margin-right: 2px;
}
.cp-swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 8px;
}
.cp-swatch {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
}
.cp-swatch .chip {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.cp-swatch:hover .chip {
  transform: translateY(-1px);
}
.cp-swatch.active .chip {
  box-shadow: 0 0 0 2px #c8f542;
  border-color: #c8f542;
}
.cp-swatch .name {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.cp-swatch.active .name {
  color: #c8f542;
}
.cp-custom {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.cp-custom-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.cp-preview {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  flex: 0 0 auto;
}
</style>
