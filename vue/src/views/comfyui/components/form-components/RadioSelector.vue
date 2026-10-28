<template>
  <div class="radio-selector">
    <el-form-item
      :prop="fieldKey"
      :required="formItem.required"
    >
      <template #label>
        <div class="label-with-tooltip">
          <span>{{ formItem.tips }}</span>
          <el-tooltip v-if="formItem.template" :content="formItem.template" placement="top">
            <el-icon class="tooltip-icon">
              <QuestionFilled />
            </el-icon>
          </el-tooltip>
        </div>
      </template>
      <!-- 两个选项：滑块开关 -->
      <div
        v-if="isTwoWaySlider"
        class="slider-toggle"
        :class="{ on: isSecondSelected }"
        @click="toggleTwoWay"
      >
        <div class="slider-track">
          <div class="slider-thumb"></div>
          <span class="seg" :class="{ active: !isSecondSelected }">{{ twoOptions[0].key }}</span>
          <span class="seg" :class="{ active: isSecondSelected }">{{ twoOptions[1].key }}</span>
        </div>
      </div>

      <!-- 图片网格模式：选项里带 image 时启用 -->
      <div v-else-if="hasImages" class="img-picker">
        <div class="picker-head">
          <span class="picked">
            已选：<b>{{ selectedLabel || '未选（点击下方模板选择）' }}</b>
          </span>
          <el-input
            v-model="keyword"
            placeholder="搜索模板，如：西装 / 马甲 / 蓬松"
            size="small"
            clearable
            class="picker-search"
          />
        </div>

        <div v-if="groups.length > 1" class="picker-tabs">
          <button
            v-for="(g, gi) in groups"
            :key="g.label"
            type="button"
            :class="['tab', { active: gi === activeGroup }]"
            @click="activeGroup = gi"
          >
            {{ g.label }}
          </button>
        </div>

        <div class="picker-grid">
          <div
            v-for="it in filteredItems"
            :key="it.key"
            :class="['cell', { selected: it.value === modelValue }]"
            @click="pick(it)"
          >
            <img :src="it.image" :alt="it.key" loading="lazy" />
            <span class="cell-name">{{ it.key }}</span>
            <span v-if="it.value === modelValue" class="cell-check">✓</span>
          </div>
        </div>
        <div v-if="!filteredItems.length" class="empty-tip">没有匹配的模板</div>
      </div>

      <el-select
        v-else
        :model-value="modelValue"
        @update:model-value="handleChange"
        :placeholder="getPlaceholder()"
        clearable
        class="radio-select"
      >
        <el-option
          v-for="option in flatOptions"
          :key="option.value"
          :label="option.key"
          :value="option.value"
        />
      </el-select>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { ElFormItem, ElSelect, ElOption, ElTooltip, ElIcon, ElInput } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'

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

interface OptionItem {
  key: string
  value: string
  image?: string
}

interface OptionGroup {
  label: string
  items: OptionItem[]
}

function normalizeItem(item: any): OptionItem {
  if (item && typeof item === 'object') {
    const out: OptionItem = {
      key: String(item.key ?? item.label ?? item.text ?? item.value ?? ''),
      value: String(item.value ?? item.key ?? item.label ?? '')
    }
    if (item.image) out.image = String(item.image)
    return out
  }
  return { key: String(item), value: String(item) }
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:model-value': [value: string]
}>()

const keyword = ref('')
const activeGroup = ref(0)

const fieldKey = computed(() => {
  return `${props.formItem.nodeKey}_${props.formItem.inputs}`
})

// 解析选项数据，支持分组（{"groups":[{label,items:[{key,value,image}]}]}）
const groups = computed<OptionGroup[]>(() => {
  if (!props.formItem.options) {
    return []
  }

  try {
    const parsed = JSON.parse(props.formItem.options)
    if (Array.isArray(parsed)) {
      return [{ label: '全部', items: parsed.map(normalizeItem) }]
    }
    if (parsed && typeof parsed === 'object') {
      if (Array.isArray((parsed as any).groups)) {
        return ((parsed as any).groups as any[])
          .map(g => ({
            label: String(g.label ?? ''),
            items: (Array.isArray(g.items) ? g.items : []).map(normalizeItem)
          }))
          .filter(g => g.items.length > 0)
      }
      if (Array.isArray((parsed as any).options)) {
        return [{ label: '全部', items: ((parsed as any).options as any[]).map(normalizeItem) }]
      }
      return [{ label: '全部', items: Object.keys(parsed).map(k => ({ key: k, value: String((parsed as any)[k]) })) }]
    }
  } catch (error) {
    console.error('Failed to parse radio options:', error)
  }

  return []
})

const flatOptions = computed<OptionItem[]>(() => groups.value.flatMap(g => g.items))

const hasImages = computed(() => flatOptions.value.some(i => !!i.image))

const selectedLabel = computed(() => {
  const hit = flatOptions.value.find(i => i.value === props.modelValue)
  return hit ? hit.key : ''
})

// 刚好两个选项、且没有图片 → 渲染成滑块开关
const twoOptions = computed<OptionItem[]>(() => {
  if (groups.value.length === 1 && groups.value[0].items.length === 2
      && !groups.value[0].items.some(i => !!i.image)) {
    return groups.value[0].items
  }
  return []
})
const isTwoWaySlider = computed(() => twoOptions.value.length === 2)
const isSecondSelected = computed(() => {
  if (!isTwoWaySlider.value) return false
  if (props.modelValue === twoOptions.value[1].value) return true
  if (props.modelValue === twoOptions.value[0].value) return false
  return false
})
const toggleTwoWay = () => {
  const next = isSecondSelected.value ? twoOptions.value[0] : twoOptions.value[1]
  emit('update:model-value', next.value)
}

onMounted(() => {
  // 滑块型必填项给个默认值（第一个选项），避免“没动过就报红”
  if (!props.modelValue && props.formItem?.required && twoOptions.value.length === 2) {
    emit('update:model-value', twoOptions.value[0].value)
  }
})

const filteredItems = computed<OptionItem[]>(() => {
  const kw = keyword.value.trim()
  if (kw) {
    return flatOptions.value.filter(i => i.key.toLowerCase().includes(kw.toLowerCase()))
  }
  const g = groups.value[activeGroup.value]
  return g ? g.items : []
})

const pick = (item: OptionItem) => {
  emit('update:model-value', item.value)
}

const handleChange = (value: string) => {
  emit('update:model-value', value)
}

const getPlaceholder = () => {
  if (props.formItem.tips) {
    return `${props.formItem.tips}`
  }
  return '请选择选项'
}
</script>

<style scoped>
.radio-selector {
  width: 100%;
}

.radio-select {
  width: 100%;
}

.radio-select :deep(.el-select) {
  width: 100%;
}

.radio-select :deep(.el-select__wrapper) {
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  transition: all 0.3s ease;
  box-shadow: none;
}

.radio-select :deep(.el-select__wrapper:hover) {
  border-color: var(--el-border-color-hover);
}

.radio-select :deep(.el-select__wrapper.is-focused) {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.radio-select :deep(.el-select__placeholder) {
  color: var(--el-text-color-placeholder);
  font-style: italic;
}

.radio-select :deep(.el-select__selected-item) {
  color: var(--el-text-color-primary);
}

.radio-select :deep(.el-select__caret) {
  color: var(--el-text-color-regular);
}



/* ===== 两选项滑块开关 ===== */
.slider-toggle {
  width: 100%;
  max-width: 100%;
  user-select: none;
}
.slider-track {
  position: relative;
  display: flex;
  align-items: center;
  background: var(--el-fill-color);
  border: 1px solid var(--el-border-color);
  border-radius: 999px;
  height: 40px;
  padding: 4px;
  cursor: pointer;
  transition: background 0.2s ease;
}
.slider-thumb {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  border-radius: 999px;
  background: #c8f542;
  box-shadow: 0 2px 8px rgba(200, 245, 66, 0.35);
  transition: transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}
.slider-toggle.on .slider-thumb {
  transform: translateX(100%);
}
.slider-track .seg {
  position: relative;
  z-index: 1;
  flex: 1;
  text-align: center;
  font-size: 13px;
  line-height: 32px;
  color: var(--el-text-color-regular);
  transition: color 0.2s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 6px;
}
.slider-track .seg.active {
  color: #0d0d12;
  font-weight: 600;
}

/* ===== 图片选择器 ===== */
.img-picker {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  padding: 12px;
  background: var(--el-bg-color);
}

.picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.picked {
  font-size: 13px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  flex: 1;
}

.picked b {
  color: #7c5cff;
}

.picker-search {
  max-width: 240px;
  flex: 0 0 auto;
}

.picker-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.picker-tabs .tab {
  cursor: pointer;
  border: 1px solid var(--el-border-color);
  background: transparent;
  color: var(--el-text-color-regular);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  line-height: 20px;
  transition: all 0.2s ease;
}

.picker-tabs .tab:hover {
  border-color: #7c5cff;
  color: #7c5cff;
}

.picker-tabs .tab.active {
  background: #c8f542;
  border-color: #d8ff5e;
  color: #0d0d12;
  font-weight: 600;
}

.picker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
  gap: 10px;
  max-height: 320px;
  overflow-y: auto;
  padding-right: 4px;
}

.picker-grid .cell {
  position: relative;
  cursor: pointer;
  border: 2px solid var(--el-border-color);
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  transition: all 0.2s ease;
}

.picker-grid .cell:hover {
  border-color: #7c5cff;
  transform: translateY(-2px);
}

.picker-grid .cell.selected {
  border-color: #c8f542;
  box-shadow: 0 0 0 2px rgba(200, 245, 66, 0.25);
}

.picker-grid .cell img {
  display: block;
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
}

.picker-grid .cell-name {
  display: block;
  font-size: 11px;
  text-align: center;
  padding: 4px 2px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.picker-grid .cell-check {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #c8f542;
  color: #0d0d12;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-tip {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  padding: 16px 0;
  text-align: center;
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
  display: inline-flex;
  align-items: center;
  gap: 6px;
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

/* 暗色模式适配 */
@media (prefers-color-scheme: dark) {
  .radio-select :deep(.el-select__wrapper) {
    background-color: var(--el-bg-color-page);
    border-color: var(--el-border-color-dark);
  }

  .radio-select :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 2px var(--el-color-primary-dark-2);
  }
}

/* Element Plus 暗色主题适配 */
.dark .radio-select :deep(.el-select__wrapper) {
  background-color: var(--el-bg-color-page);
  border-color: var(--el-border-color-dark);
  color: var(--el-text-color-primary);
}

.dark .radio-select :deep(.el-select__wrapper.is-focused) {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-dark-2);
}

.dark .radio-select :deep(.el-select__selected-item) {
  color: var(--el-text-color-primary);
}

.dark .radio-select :deep(.el-select__placeholder) {
  color: var(--el-text-color-placeholder);
}

/* 下拉选项样式 - 使用全局样式 */
:global(.el-select-dropdown) {
  background-color: var(--el-bg-color) !important;
  border: 1px solid var(--el-border-color) !important;
  border-radius: 8px !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1) !important;
}

:global(.el-select-dropdown__item) {
  color: var(--el-text-color-primary) !important;
  background-color: transparent !important;
  transition: all 0.2s ease !important;
}

:global(.el-select-dropdown__item:hover) {
  background-color: var(--el-fill-color-light) !important;
}

:global(.el-select-dropdown__item.is-selected) {
  background-color: var(--el-color-primary-light-9) !important;
  color: var(--el-color-primary) !important;
  font-weight: 500 !important;
}

/* 暗色模式下拉选项 */
@media (prefers-color-scheme: dark) {
  :global(.el-select-dropdown) {
    background-color: var(--el-bg-color-page) !important;
    border-color: var(--el-border-color-dark) !important;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3) !important;
  }

  :global(.el-select-dropdown__item:hover) {
    background-color: var(--el-fill-color-dark) !important;
  }

  :global(.el-select-dropdown__item.is-selected) {
    background-color: var(--el-color-primary-dark-2) !important;
  }
}

/* Element Plus 暗色主题下拉选项 */
.dark :global(.el-select-dropdown) {
  background-color: var(--el-bg-color-page) !important;
  border-color: var(--el-border-color-dark) !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3) !important;
}

.dark :global(.el-select-dropdown__item) {
  color: var(--el-text-color-primary) !important;
}

.dark :global(.el-select-dropdown__item:hover) {
  background-color: var(--el-fill-color-dark) !important;
}

.dark :global(.el-select-dropdown__item.is-selected) {
  background-color: var(--el-color-primary-dark-2) !important;
  color: var(--el-color-primary-light-3) !important;
}
</style>
