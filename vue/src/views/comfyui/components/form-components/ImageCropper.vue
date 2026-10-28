<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="ic-modal"
      role="dialog"
      aria-modal="true"
      aria-label="裁剪图片"
    >
      <!-- 顶部标题 -->
      <div class="ic-header">
        <div class="ic-title">
          裁剪图片
          <span class="ic-sub">拖动框选区域，调整裁剪范围</span>
        </div>
      </div>

      <!-- 图片舞台 -->
      <div ref="stageRef" class="ic-stage" @pointerdown="onStageDown">
        <div
          v-if="ready"
          ref="canvasRef"
          class="ic-canvas"
          :style="canvasStyle"
          @pointerdown="onCanvasDown"
        >
          <img
            class="ic-img"
            :src="displaySrc"
            alt="待裁剪图片"
            draggable="false"
            @dragstart.prevent
          />
          <div
            v-if="sel"
            class="ic-sel"
            :style="selStyle"
            @pointerdown.stop="onSelDown"
          >
            <div class="ic-thirds">
              <i class="v1" /><i class="v2" /><i class="h1" /><i class="h2" />
            </div>
            <span
              v-for="dir in HANDLES"
              :key="dir"
              class="ic-handle"
              :class="`ic-handle-${dir}`"
              @pointerdown.stop="onHandleDown($event, dir)"
            />
          </div>
        </div>
        <div v-else class="ic-loading">
          <span class="ic-spinner" />
          <span>图片加载中…</span>
        </div>
      </div>

      <!-- 底部工具栏 -->
      <div class="ic-toolbar">
        <div class="ic-size">
          <span class="ic-size-value">{{ sizeLabel }}</span>
          <span class="ic-size-unit">px</span>
        </div>
        <div class="ic-actions">
          <button type="button" class="ic-btn" :disabled="busy" @click="resetSelection">重置</button>
          <button type="button" class="ic-btn" :disabled="busy" @click="close">取消</button>
          <button
            type="button"
            class="ic-btn ic-btn-primary"
            :disabled="!canConfirm"
            @click="confirmCrop"
          >
            {{ busy ? '上传中…' : '确认裁剪' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

interface Props {
  visible: boolean
  src: string
  /** 裁剪结果上传中，禁止重复操作 */
  busy?: boolean
}

const props = withDefaults(defineProps<Props>(), { busy: false })

const emit = defineEmits<{
  'update:visible': [value: boolean]
  confirm: [blob: Blob]
}>()

/** 8 个拖拽点（4 角 + 4 边） */
const HANDLES = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const
type HandleDir = (typeof HANDLES)[number]

/** 原图像素尺寸 */
const natW = ref(0)
const natH = ref(0)
/** 舞台可视区尺寸 */
const stageW = ref(0)
const stageH = ref(0)
/** 当前选区（原图像素坐标） */
const sel = ref<{ x: number; y: number; w: number; h: number } | null>(null)
const ready = ref(false)
const displaySrc = ref('')

const stageRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLElement | null>(null)

let imgEl: HTMLImageElement | null = null
let objectUrl: string | null = null
let resizeObserver: ResizeObserver | null = null
let loadToken = 0

const MIN_SIZE = 16

/* ---------------- 尺寸换算 ---------------- */

const scale = computed(() => {
  if (!natW.value || !natH.value || !stageW.value || !stageH.value) return 1
  return Math.min(stageW.value / natW.value, stageH.value / natH.value)
})

const canvasStyle = computed(() => {
  if (!natW.value) return {}
  return {
    width: `${Math.max(1, Math.round(natW.value * scale.value))}px`,
    height: `${Math.max(1, Math.round(natH.value * scale.value))}px`
  }
})

const selStyle = computed(() => {
  const s = sel.value
  if (!s) return {}
  return {
    left: `${s.x * scale.value}px`,
    top: `${s.y * scale.value}px`,
    width: `${s.w * scale.value}px`,
    height: `${s.h * scale.value}px`
  }
})

const sizeLabel = computed(() => {
  const s = sel.value
  if (!s) return '— × —'
  return `${Math.round(s.w)} × ${Math.round(s.h)}`
})

const canConfirm = computed(() => !!sel.value && sel.value.w >= 2 && sel.value.h >= 2 && ready.value)

/* ---------------- 选区约束 ---------------- */

const clamp = (v: number, min: number, max: number) => Math.min(Math.max(v, min), max)

/** 把选区限制在原图范围内，并保证最小尺寸 */
function normalize(x: number, y: number, w: number, h: number) {
  const W = natW.value
  const H = natH.value
  let cw = clamp(w, 1, W)
  let ch = clamp(h, 1, H)
  if (cw < MIN_SIZE) cw = Math.min(MIN_SIZE, W)
  if (ch < MIN_SIZE) ch = Math.min(MIN_SIZE, H)
  const cx = clamp(x, 0, W - cw)
  const cy = clamp(y, 0, H - ch)
  return { x: Math.round(cx), y: Math.round(cy), w: Math.round(cw), h: Math.round(ch) }
}

function resetSelection() {
  if (!natW.value || !natH.value) return
  const w = natW.value * 0.8
  const h = natH.value * 0.8
  sel.value = normalize((natW.value - w) / 2, (natH.value - h) / 2, w, h)
}

/* ---------------- 图片加载 ---------------- */

function loadFromUrl(url: string, cors: boolean) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image()
    if (cors) img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('image load failed'))
    img.src = url
  })
}

/** 优先通过 fetch 取回二进制，避免 canvas 被跨域图片污染导致导出失败 */
async function prepareImage(src: string) {
  const token = ++loadToken
  ready.value = false
  sel.value = null

  const apply = (img: HTMLImageElement, url: string, revoke: (() => void) | null) => {
    if (token !== loadToken) {
      if (revoke) revoke()
      return
    }
    if (objectUrl) URL.revokeObjectURL(objectUrl)
    objectUrl = revoke ? url : null
    imgEl = img
    natW.value = img.naturalWidth
    natH.value = img.naturalHeight
    displaySrc.value = url
    ready.value = true
    nextTick(() => {
      measureStage()
      resetSelection()
    })
  }

  try {
    const res = await fetch(src, { mode: 'cors', credentials: 'omit' })
    if (!res.ok) throw new Error(`status ${res.status}`)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const img = await loadFromUrl(url, false)
    apply(img, url, () => URL.revokeObjectURL(url))
    return
  } catch {
    // fetch 失败（无 CORS 头 / 网络问题）时退回原始地址
  }

  try {
    const img = await loadFromUrl(src, true)
    apply(img, src, null)
  } catch {
    try {
      const img = await loadFromUrl(src, false)
      apply(img, src, null)
    } catch {
      ElMessage.error('图片加载失败，请重新上传后再裁剪')
      close()
    }
  }
}

/* ---------------- 舞台测量 & 响应式 ---------------- */

function measureStage() {
  const el = stageRef.value
  if (!el) return
  stageW.value = el.clientWidth
  stageH.value = el.clientHeight
}

function ensureObserver() {
  if (resizeObserver || !stageRef.value) return
  resizeObserver = new ResizeObserver(() => {
    measureStage()
    // 窗口变化后保证选区仍在图内
    const s = sel.value
    if (s) sel.value = normalize(s.x, s.y, s.w, s.h)
  })
  resizeObserver.observe(stageRef.value)
}

/* ---------------- 指针交互 ---------------- */

type DragMode = 'new' | 'move' | 'resize'

interface DragState {
  mode: DragMode
  dir?: HandleDir
  startClientX: number
  startClientY: number
  anchor: { x: number; y: number }
  origin: { x: number; y: number; w: number; h: number }
}

let drag: DragState | null = null

/** 客户端坐标 → 原图像素坐标 */
function toNatural(ev: PointerEvent | MouseEvent) {
  const box = canvasRef.value?.getBoundingClientRect()
  const k = scale.value || 1
  if (!box) return { x: 0, y: 0 }
  return {
    x: clamp((ev.clientX - box.left) / k, 0, natW.value),
    y: clamp((ev.clientY - box.top) / k, 0, natH.value)
  }
}

function onStageDown(ev: PointerEvent) {
  // 点击舞台空白（遮罩）不关闭弹窗，仅阻止冒泡
  ev.stopPropagation()
}

function onCanvasDown(ev: PointerEvent) {
  if (!ready.value || props.busy) return
  const p = toNatural(ev)
  drag = {
    mode: 'new',
    startClientX: ev.clientX,
    startClientY: ev.clientY,
    anchor: p,
    origin: { x: p.x, y: p.y, w: 0, h: 0 }
  }
  sel.value = normalize(p.x, p.y, MIN_SIZE, MIN_SIZE)
  bindWindow()
}

function onSelDown(ev: PointerEvent) {
  const s = sel.value
  if (!s || props.busy) return
  drag = {
    mode: 'move',
    startClientX: ev.clientX,
    startClientY: ev.clientY,
    anchor: toNatural(ev),
    origin: { ...s }
  }
  bindWindow()
}

function onHandleDown(ev: PointerEvent, dir: HandleDir) {
  const s = sel.value
  if (!s || props.busy) return
  drag = {
    mode: 'resize',
    dir,
    startClientX: ev.clientX,
    startClientY: ev.clientY,
    anchor: toNatural(ev),
    origin: { ...s }
  }
  bindWindow()
}

function bindWindow() {
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
  window.addEventListener('pointercancel', onPointerUp)
}

function unbindWindow() {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)
}

function onPointerMove(ev: PointerEvent) {
  const d = drag
  if (!d) return
  const k = scale.value || 1
  const dx = (ev.clientX - d.startClientX) / k
  const dy = (ev.clientY - d.startClientY) / k
  const W = natW.value
  const H = natH.value

  if (d.mode === 'move') {
    const nx = clamp(d.origin.x + dx, 0, W - d.origin.w)
    const ny = clamp(d.origin.y + dy, 0, H - d.origin.h)
    sel.value = { x: Math.round(nx), y: Math.round(ny), w: d.origin.w, h: d.origin.h }
    return
  }

  if (d.mode === 'new') {
    const cx = clamp(d.anchor.x + dx, 0, W)
    const cy = clamp(d.anchor.y + dy, 0, H)
    let x1 = Math.min(d.anchor.x, cx)
    let x2 = Math.max(d.anchor.x, cx)
    let y1 = Math.min(d.anchor.y, cy)
    let y2 = Math.max(d.anchor.y, cy)
    if (x2 - x1 < MIN_SIZE) {
      if (cx >= d.anchor.x) x2 = Math.min(W, x1 + MIN_SIZE)
      else x1 = Math.max(0, x2 - MIN_SIZE)
    }
    if (y2 - y1 < MIN_SIZE) {
      if (cy >= d.anchor.y) y2 = Math.min(H, y1 + MIN_SIZE)
      else y1 = Math.max(0, y2 - MIN_SIZE)
    }
    sel.value = normalize(x1, y1, x2 - x1, y2 - y1)
    return
  }

  // resize
  const dir = d.dir as HandleDir
  let left = d.origin.x
  let top = d.origin.y
  let right = d.origin.x + d.origin.w
  let bottom = d.origin.y + d.origin.h

  if (dir.includes('w')) left += dx
  if (dir.includes('e')) right += dx
  if (dir.includes('n')) top += dy
  if (dir.includes('s')) bottom += dy

  left = clamp(left, 0, W)
  right = clamp(right, 0, W)
  top = clamp(top, 0, H)
  bottom = clamp(bottom, 0, H)

  if (right - left < MIN_SIZE) {
    if (dir.includes('w')) left = Math.max(0, right - MIN_SIZE)
    else right = Math.min(W, left + MIN_SIZE)
  }
  if (bottom - top < MIN_SIZE) {
    if (dir.includes('n')) top = Math.max(0, bottom - MIN_SIZE)
    else bottom = Math.min(H, top + MIN_SIZE)
  }

  sel.value = {
    x: Math.round(left),
    y: Math.round(top),
    w: Math.round(right - left),
    h: Math.round(bottom - top)
  }
}

function onPointerUp() {
  drag = null
  unbindWindow()
}

/* ---------------- 导出 ---------------- */

function confirmCrop() {
  const s = sel.value
  const img = imgEl
  if (!s || !img || props.busy) return

  const sx = Math.round(clamp(s.x, 0, natW.value - 1))
  const sy = Math.round(clamp(s.y, 0, natH.value - 1))
  const sw = Math.round(clamp(s.w, 1, natW.value - sx))
  const sh = Math.round(clamp(s.h, 1, natH.value - sy))

  const canvas = document.createElement('canvas')
  canvas.width = sw
  canvas.height = sh
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    ElMessage.error('当前浏览器不支持画布裁剪')
    return
  }
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  try {
    ctx.drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh)
  } catch {
    ElMessage.error('图片读取失败（跨域限制），请重新上传本地图片后再裁剪')
    return
  }

  try {
    canvas.toBlob((blob) => {
      if (!blob) {
        ElMessage.error('裁剪失败，请重试')
        return
      }
      emit('confirm', blob)
    }, 'image/png')
  } catch {
    ElMessage.error('图片读取失败（跨域限制），请重新上传本地图片后再裁剪')
  }
}

/* ---------------- 生命周期 ---------------- */

function close() {
  if (props.busy) return
  emit('update:visible', false)
}

function onKeydown(ev: KeyboardEvent) {
  if (ev.key === 'Escape') {
    ev.stopPropagation()
    close()
  }
}

function teardown() {
  loadToken++
  unbindWindow()
  drag = null
  window.removeEventListener('keydown', onKeydown, true)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = null
  }
  imgEl = null
  ready.value = false
  sel.value = null
  natW.value = 0
  natH.value = 0
  displaySrc.value = ''
}

watch(
  () => props.visible,
  async (open) => {
    if (open) {
      window.addEventListener('keydown', onKeydown, true)
      await nextTick()
      measureStage()
      ensureObserver()
      if (props.src) void prepareImage(props.src)
    } else {
      teardown()
    }
  },
  { immediate: true }
)

onBeforeUnmount(teardown)
</script>

<style scoped>
.ic-modal {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  background: rgba(9, 9, 11, 0.94);
  backdrop-filter: blur(6px);
  color: #f4f4f5;
  animation: icFade 0.18s ease-out;
}

@keyframes icFade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.ic-header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  padding: 18px 24px 10px;
}

.ic-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.ic-sub {
  font-size: 12px;
  font-weight: 400;
  color: #8b8b93;
}

.ic-stage {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px 24px;
  overflow: hidden;
}

.ic-canvas {
  position: relative;
  overflow: hidden;
  border-radius: 10px;
  background: #1b1b1f;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.55);
  touch-action: none;
  user-select: none;
  cursor: crosshair;
}

.ic-img {
  display: block;
  width: 100%;
  height: 100%;
  user-select: none;
  -webkit-user-drag: none;
  pointer-events: none;
}

.ic-sel {
  position: absolute;
  box-sizing: border-box;
  border: 1px solid #7c5cff;
  cursor: move;
  touch-action: none;
  /* 选区外整体压暗 */
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.62);
}

.ic-thirds {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.ic-thirds i {
  position: absolute;
  background: rgba(255, 255, 255, 0.22);
}

.ic-thirds .v1,
.ic-thirds .v2 {
  top: 0;
  bottom: 0;
  width: 1px;
}

.ic-thirds .v1 {
  left: 33.333%;
}

.ic-thirds .v2 {
  left: 66.666%;
}

.ic-thirds .h1,
.ic-thirds .h2 {
  left: 0;
  right: 0;
  height: 1px;
}

.ic-thirds .h1 {
  top: 33.333%;
}

.ic-thirds .h2 {
  top: 66.666%;
}

.ic-handle {
  position: absolute;
  width: 12px;
  height: 12px;
  background: #7c5cff;
  border: 1px solid #0f0f11;
  border-radius: 3px;
  box-sizing: border-box;
  touch-action: none;
  transition: transform 0.12s ease;
}

.ic-handle:hover {
  transform: scale(1.18);
}

.ic-handle-nw {
  left: -6px;
  top: -6px;
  cursor: nwse-resize;
}

.ic-handle-n {
  left: 50%;
  top: -6px;
  margin-left: -6px;
  cursor: ns-resize;
}

.ic-handle-ne {
  right: -6px;
  top: -6px;
  cursor: nesw-resize;
}

.ic-handle-e {
  right: -6px;
  top: 50%;
  margin-top: -6px;
  cursor: ew-resize;
}

.ic-handle-se {
  right: -6px;
  bottom: -6px;
  cursor: nwse-resize;
}

.ic-handle-s {
  left: 50%;
  bottom: -6px;
  margin-left: -6px;
  cursor: ns-resize;
}

.ic-handle-sw {
  left: -6px;
  bottom: -6px;
  cursor: nesw-resize;
}

.ic-handle-w {
  left: -6px;
  top: 50%;
  margin-top: -6px;
  cursor: ew-resize;
}

.ic-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #8b8b93;
  font-size: 13px;
}

.ic-spinner {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid rgba(124, 92, 255, 0.25);
  border-top-color: #7c5cff;
  animation: icSpin 0.8s linear infinite;
}

@keyframes icSpin {
  to {
    transform: rotate(360deg);
  }
}

.ic-toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px 20px;
  border-top: 1px solid #232329;
  background: #111113;
}

.ic-size {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #1b1b1f;
  border: 1px solid #2a2a31;
  font-variant-numeric: tabular-nums;
}

.ic-size-value {
  font-size: 14px;
  font-weight: 600;
  color: #7c5cff;
}

.ic-size-unit {
  font-size: 11px;
  color: #75757d;
}

.ic-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ic-btn {
  height: 36px;
  padding: 0 18px;
  border-radius: 10px;
  border: 1px solid #2c2c33;
  background: #1b1b1f;
  color: #e4e4e7;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.16s ease;
}

.ic-btn:hover:not(:disabled) {
  border-color: #3a3a44;
  background: #232329;
}

.ic-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ic-btn-primary {
  border-color: transparent;
  background: #7c5cff;
  color: #101014;
  font-weight: 600;
}

.ic-btn-primary:hover:not(:disabled) {
  background: #d5ff5c;
  box-shadow: 0 6px 20px rgba(124, 92, 255, 0.25);
}

@media (max-width: 640px) {
  .ic-header {
    padding: 14px 14px 6px;
  }

  .ic-stage {
    padding: 10px 12px;
  }

  .ic-toolbar {
    padding: 10px 12px 14px;
    flex-wrap: wrap;
  }

  .ic-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
