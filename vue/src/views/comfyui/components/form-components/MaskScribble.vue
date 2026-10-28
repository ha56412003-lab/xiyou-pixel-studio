<template>
  <div class="mask-scribble">
    <el-form-item :prop="fieldKey" :required="formItem.required">
      <template #label>
        <div class="label-with-tooltip">
          <span>{{ formItem.tips }}</span>
          <el-tooltip v-if="formItem.template" :content="formItem.template" placement="top">
            <el-icon class="tooltip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
      </template>

      <!-- 未上传：上传入口 -->
      <div v-if="!modelValue" class="upload-box" @click="pickFile">
        <el-icon class="upl-icon"><Picture /></el-icon>
        <div class="upl-text">点击上传图片</div>
        <div class="upl-sub">上传后用遮罩画笔涂抹要处理的区域</div>
        <input ref="fileInput" type="file" accept="image/*" class="hidden-input" @change="onFileChange" />
      </div>

      <!-- 已上传：预览 -->
      <div v-else class="done-box">
        <img :src="previewUrl || modelValue" class="done-img" alt="已选图片" />
        <div class="done-actions">
          <el-button size="small" @click="openPainter">重新涂抹遮罩</el-button>
          <el-button size="small" text @click="resetAll">重新上传</el-button>
        </div>
        <div class="done-tip">✅ 遮罩已保存</div>
      </div>
    </el-form-item>

    <!-- 遮罩画笔窗口 -->
    <el-dialog
      v-model="painterVisible"
      title="涂抹要处理的区域"
      width="820px"
      append-to-body
      class="mask-scribble-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <div class="painter">
        <div class="toolbar">
          <div class="tools">
            <div class="tool" :class="{ active: drawMode === 'draw' }" @click="drawMode = 'draw'">
              <el-icon><Edit /></el-icon><span>画笔</span>
            </div>
            <div class="tool" :class="{ active: drawMode === 'erase' }" @click="drawMode = 'erase'">
              <el-icon><Delete /></el-icon><span>橡皮</span>
            </div>
            <div class="tool" :class="{ active: drawMode === 'fill' }" @click="drawMode = 'fill'">
              <el-icon><MagicStick /></el-icon><span>填充</span>
            </div>
          </div>

          <div class="size-ctl">
            <span>笔刷</span>
            <el-slider v-model="brushSize" :min="6" :max="120" :step="2" style="width: 100px" />
            <span class="size-val">{{ brushSize }}px</span>
          </div>

          <div class="color-ctl">
            <span>颜色</span>
            <div
              v-for="c in COLOR_OPTIONS"
              :key="c.key"
              class="swatch"
              :class="{ active: maskColor === c.key }"
              :style="{ background: c.css }"
              :title="c.label"
              @click="maskColor = c.key"
            ></div>
          </div>

          <div class="acts">
            <el-button size="small" :disabled="!history.length" @click="undo">撤销</el-button>
            <el-button size="small" @click="clearMask">清空</el-button>
          </div>
        </div>

        <div class="tip-line">
          <template v-if="drawMode === 'fill'">
            <b>填充</b>：先用「画笔」把目标区域圈一圈，再切到「填充」在圈内点一下，整块自动涂满
          </template>
          <template v-else-if="drawMode === 'erase'">
            <b>橡皮</b>：擦掉涂错的绿色区域（不会伤到原图）
          </template>
          <template v-else>
            <b>画笔</b>：涂抹要处理的区域（绿色部分）
          </template>
        </div>

        <div class="canvas-wrap" ref="wrapRef">
          <canvas
            ref="canvasRef"
            class="paint-canvas"
            :style="{ cursor: cursorStyle }"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointerleave="onPointerUp"
            @pointercancel="onPointerUp"
          ></canvas>
        </div>

        <div class="status-line">
          已涂抹：<b>{{ maskPercent.toFixed(1) }}%</b>
          <span v-if="maskPercent < 0.2" class="warn">（还没涂，涂完再确定）</span>
        </div>
      </div>

      <template #footer>
        <el-button @click="painterVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="maskPercent < 0.2" @click="confirmMask">
          确定（保存遮罩）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch, onBeforeUnmount } from 'vue'
import { ElFormItem, ElDialog, ElButton, ElTooltip, ElIcon, ElSlider, ElNotification } from 'element-plus'
import { Picture, QuestionFilled, Edit, Delete, MagicStick } from '@element-plus/icons-vue'
import { ossApi } from '@/api/oss/oss'

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
const emit = defineEmits<{ 'update:model-value': [value: string] }>()

/** 遮罩叠加层：颜色可选（默认绿），透明度固定 0.4 */
const MASK_ALPHA = 0.4
type MaskColorKey = 'green' | 'white' | 'black'
const COLOR_OPTIONS: { key: MaskColorKey; label: string; css: string; rgb: string; stroke: string }[] = [
  { key: 'green', label: '绿色（默认）', css: '#22c55e', rgb: '34, 197, 94', stroke: '%2322c55e' },
  { key: 'white', label: '白色', css: '#ffffff', rgb: '255, 255, 255', stroke: '%23ffffff' },
  { key: 'black', label: '黑色', css: '#111111', rgb: '17, 17, 17', stroke: '%23111111' }
]
const maskColor = ref<MaskColorKey>('green')
const activeColor = computed(() => COLOR_OPTIONS.find(c => c.key === maskColor.value) || COLOR_OPTIONS[0])

const fileInput = ref<HTMLInputElement>()
const wrapRef = ref<HTMLElement>()
const canvasRef = ref<HTMLCanvasElement>()

const previewUrl = ref('')
const srcUrl = ref('')
const painterVisible = ref(false)
const saving = ref(false)
const drawMode = ref<'draw' | 'erase' | 'fill'>('draw')
const brushSize = ref(40)
const maskPercent = ref(0)
const history = ref<ImageData[]>([])
const MAX_HISTORY = 15

let baseImg: HTMLImageElement | null = null
let W = 0
let H = 0
let maskCanvas: HTMLCanvasElement | null = null
let drawing = false
let lastX = 0
let lastY = 0

const fieldKey = computed(() => `${props.formItem.nodeKey}_${props.formItem.inputs}`)

/** 圆形笔刷光标 */
const cursorStyle = computed(() => {
  if (drawMode.value === 'fill') return 'crosshair'
  const d = Math.max(6, brushSize.value)
  const r = d / 2
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='${d}' height='${d}'><circle cx='${r}' cy='${r}' r='${Math.max(1, r - 1)}' fill='none' stroke='${activeColor.value.stroke}' stroke-width='2'/></svg>`
  return `url("data:image/svg+xml;utf8,${svg}") ${r} ${r}, crosshair`
})

function pickFile() {
  fileInput.value?.click()
}

function resetAll() {
  emit('update:model-value', '')
  previewUrl.value = ''
  srcUrl.value = ''
  maskPercent.value = 0
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) return
  if (f.size > 15 * 1024 * 1024) {
    ElNotification.error({ title: '图片太大', message: '请上传 15MB 以内的图片' })
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    srcUrl.value = String(reader.result)
    nextTick(() => openPainter())
  }
  reader.readAsDataURL(f)
  input.value = ''
}

function openPainter() {
  if (!srcUrl.value) {
    if (props.modelValue) srcUrl.value = props.modelValue
    else return
  }
  painterVisible.value = true
  nextTick(() => initCanvas())
}

function initCanvas() {
  const cv = canvasRef.value
  if (!cv) return
  const im = new Image()
  im.crossOrigin = 'anonymous'
  im.onload = () => {
    baseImg = im
    const maxW = Math.min(720, (wrapRef.value?.clientWidth || 720) - 8)
    const maxH = Math.round(window.innerHeight * 0.64)
    const scale = Math.min(1, maxW / im.naturalWidth, maxH / im.naturalHeight)
    W = Math.round(im.naturalWidth * scale)
    H = Math.round(im.naturalHeight * scale)
    cv.width = W
    cv.height = H
    cv.style.width = `${W}px`
    cv.style.height = `${H}px`

    maskCanvas = document.createElement('canvas')
    maskCanvas.width = W
    maskCanvas.height = H
    history.value = []
    maskPercent.value = 0
    redraw()
  }
  im.src = srcUrl.value
}

function maskCtx(): CanvasRenderingContext2D | null {
  return maskCanvas ? maskCanvas.getContext('2d', { willReadFrequently: true }) : null
}

// 换颜色后立即重绘叠加层
watch(maskColor, () => {
  if (maskCanvas) redraw()
})

/** 重绘：照片层 + 绿色半透明遮罩层（两者完全分离） */
function redraw() {
  const cv = canvasRef.value
  if (!cv || !baseImg || !maskCanvas) return
  const ctx = cv.getContext('2d')!
  ctx.clearRect(0, 0, W, H)
  ctx.drawImage(baseImg, 0, 0, W, H)

  const tmp = document.createElement('canvas')
  tmp.width = W
  tmp.height = H
  const tctx = tmp.getContext('2d')!
  tctx.drawImage(maskCanvas, 0, 0)
  tctx.globalCompositeOperation = 'source-in'
  tctx.fillStyle = `rgba(${activeColor.value.rgb}, ${MASK_ALPHA})`
  tctx.fillRect(0, 0, W, H)
  ctx.drawImage(tmp, 0, 0)
  updatePercent()
}

function updatePercent() {
  const ctx = maskCtx()
  if (!ctx) return
  const d = ctx.getImageData(0, 0, W, H).data
  let c = 0
  for (let i = 3; i < d.length; i += 4) if (d[i] > 10) c++
  maskPercent.value = (c / (W * H)) * 100
}

function pushHistory() {
  const ctx = maskCtx()
  if (!ctx) return
  history.value.push(ctx.getImageData(0, 0, W, H))
  if (history.value.length > MAX_HISTORY) history.value.shift()
}

function undo() {
  const snap = history.value.pop()
  const ctx = maskCtx()
  if (!snap || !ctx) return
  ctx.putImageData(snap, 0, 0)
  redraw()
}

function clearMask() {
  const ctx = maskCtx()
  if (!ctx) return
  pushHistory()
  ctx.clearRect(0, 0, W, H)
  redraw()
}

function toLocal(e: PointerEvent) {
  const cv = canvasRef.value!
  const r = cv.getBoundingClientRect()
  return {
    x: ((e.clientX - r.left) / r.width) * W,
    y: ((e.clientY - r.top) / r.height) * H
  }
}

function onPointerDown(e: PointerEvent) {
  if (!maskCanvas) return
  const p = toLocal(e)

  if (drawMode.value === 'fill') {
    pushHistory()
    floodFill(Math.round(p.x), Math.round(p.y))
    redraw()
    return
  }

  pushHistory()
  drawing = true
  lastX = p.x
  lastY = p.y
  stroke(p.x, p.y, p.x, p.y)
  redraw()
  canvasRef.value?.setPointerCapture?.(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!drawing) return
  const p = toLocal(e)
  stroke(lastX, lastY, p.x, p.y)
  lastX = p.x
  lastY = p.y
  redraw()
}

function onPointerUp() {
  if (drawing) {
    drawing = false
    updatePercent()
  }
}

/** 只操作遮罩层：画笔=白色涂上，橡皮=擦掉（不碰照片层） */
function stroke(x0: number, y0: number, x1: number, y1: number) {
  const ctx = maskCtx()
  if (!ctx) return
  ctx.save()
  ctx.globalCompositeOperation = drawMode.value === 'erase' ? 'destination-out' : 'source-over'
  ctx.strokeStyle = '#ffffff'
  ctx.fillStyle = '#ffffff'
  ctx.lineWidth = brushSize.value
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.beginPath()
  ctx.moveTo(x0, y0)
  ctx.lineTo(x1, y1)
  ctx.stroke()
  if (x0 === x1 && y0 === y1) {
    ctx.beginPath()
    ctx.arc(x1, y1, brushSize.value / 2, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.restore()
}

/** 填充：把点击点所在连通的空白一次填满，遇已涂像素即停（硬边、无羽化） */
function floodFill(sx: number, sy: number) {
  const ctx = maskCtx()
  if (!maskCanvas || !ctx) return
  if (sx < 0 || sy < 0 || sx >= W || sy >= H) return
  const imgData = ctx.getImageData(0, 0, W, H)
  const d = imgData.data
  const at = (x: number, y: number) => (y * W + x) * 4
  if (d[at(sx, sy) + 3] > 10) return

  const visited = new Uint8Array(W * H)
  const stack = [sy * W + sx]
  visited[sy * W + sx] = 1
  let guard = 0
  const limit = W * H

  while (stack.length && guard < limit) {
    const p = stack.pop()!
    guard++
    const x = p % W
    const y = (p - x) / W
    const i = at(x, y)
    if (d[i + 3] > 10) continue
    d[i] = 255; d[i + 1] = 255; d[i + 2] = 255; d[i + 3] = 255
    if (x + 1 < W) { const k = y * W + x + 1; if (!visited[k]) { visited[k] = 1; stack.push(k) } }
    if (x - 1 >= 0) { const k = y * W + x - 1; if (!visited[k]) { visited[k] = 1; stack.push(k) } }
    if (y + 1 < H) { const k = (y + 1) * W + x; if (!visited[k]) { visited[k] = 1; stack.push(k) } }
    if (y - 1 >= 0) { const k = (y - 1) * W + x; if (!visited[k]) { visited[k] = 1; stack.push(k) } }
  }
  ctx.putImageData(imgData, 0, 0)
}

/** 导出：原图 + 遮罩区透明（alpha=0）→ PNG 不压缩上传 */
async function confirmMask() {
  if (!baseImg || !maskCanvas) return
  saving.value = true
  try {
    const ow = baseImg.naturalWidth
    const oh = baseImg.naturalHeight
    const out = document.createElement('canvas')
    out.width = ow
    out.height = oh
    const octx = out.getContext('2d')!
    octx.drawImage(baseImg, 0, 0, ow, oh)

    const scaledMask = document.createElement('canvas')
    scaledMask.width = ow
    scaledMask.height = oh
    const sctx = scaledMask.getContext('2d')!
    sctx.imageSmoothingEnabled = false
    sctx.drawImage(maskCanvas, 0, 0, ow, oh)

    octx.globalCompositeOperation = 'destination-out'
    octx.drawImage(scaledMask, 0, 0)
    octx.globalCompositeOperation = 'source-over'

    const blob: Blob = await new Promise((res, rej) => {
      out.toBlob(b => (b ? res(b) : rej(new Error('导出失败'))), 'image/png')
    })
    const file = new File([blob], `masked_${Date.now()}.png`, { type: 'image/png' })
    const url = await ossApi.uploadFile({ file })

    previewUrl.value = URL.createObjectURL(blob)
    emit('update:model-value', url)
    painterVisible.value = false
    ElNotification.success({ title: '遮罩已保存', message: '现在可以提交生成了' })
  } catch (err: any) {
    console.error(err)
    ElNotification.error({ title: '保存失败', message: err?.message || '请重试' })
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  baseImg = null
  maskCanvas = null
  history.value = []
})
</script>

<style scoped>
.mask-scribble { width: 100%; }

.upload-box {
  width: 100%;
  border: 1.5px dashed var(--el-border-color);
  border-radius: 12px;
  padding: 22px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
}
.upload-box:hover { border-color: #7c5cff; }
.upl-icon { font-size: 26px; color: #7c5cff; }
.upl-text { margin-top: 6px; font-size: 14px; color: var(--el-text-color-primary); }
.upl-sub { margin-top: 4px; font-size: 12px; color: var(--el-text-color-secondary); }
.hidden-input { display: none; }

.done-box { display: flex; flex-direction: column; gap: 8px; }
.done-img { max-height: 220px; max-width: 100%; border-radius: 10px; object-fit: contain; background: var(--el-fill-color-light); }
.done-actions { display: flex; gap: 8px; align-items: center; }
.done-tip { font-size: 12px; color: #38c172; }

.painter { display: flex; flex-direction: column; gap: 10px; }
.toolbar { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.tools { display: flex; gap: 8px; }
.tool {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 12px; border-radius: 8px; cursor: pointer;
  border: 1px solid var(--el-border-color);
  font-size: 13px; color: var(--el-text-color-regular);
  transition: all 0.2s ease;
}
.tool:hover { border-color: #7c5cff; color: #7c5cff; }
.tool.active { background: #c8f542; border-color: #d8ff5e; color: #0d0d12; font-weight: 600; }
.size-ctl { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--el-text-color-regular); }
.size-val { width: 44px; }
.color-ctl { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--el-text-color-regular); }
.swatch {
  width: 18px; height: 18px; border-radius: 50%;
  border: 2px solid var(--el-border-color);
  cursor: pointer; transition: all 0.15s ease;
}
.swatch:hover { transform: scale(1.12); }
.swatch.active { border-color: #7c5cff; box-shadow: 0 0 0 2px rgba(124, 92, 255, 0.25); }
.acts { margin-left: auto; display: flex; gap: 8px; }
.tip-line { font-size: 12px; color: var(--el-text-color-secondary); }
.canvas-wrap { display: flex; justify-content: center; background: var(--el-fill-color-light); border-radius: 10px; padding: 6px; overflow: auto; max-height: 66vh; }
.paint-canvas { touch-action: none; border-radius: 6px; background: #fff; display: block; }
.status-line { font-size: 13px; color: var(--el-text-color-regular); }
.status-line .warn { color: #e6a23c; }
.label-with-tooltip { display: inline-flex; align-items: center; gap: 6px; }
.tooltip-icon { font-size: 14px; color: var(--el-text-color-secondary); cursor: help; }
</style>
