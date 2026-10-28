<template>
  <div class="app-detail-overlay">
    <div class="page-wrap">
      <!-- ===== 顶部通栏 header ===== -->
      <div class="detail-topbar">
        <div class="topbar-main">
          <h1 class="app-title">{{ item?.name || '-' }}</h1>
          <div class="tag-row">
            <span v-for="tag in tags" :key="tag" class="app-tag">{{ tag }}</span>
            <span class="run-count">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M8 5v14l11-7L8 5z" fill="currentColor"/>
              </svg>
              已运行 {{ runCount }} 次
            </span>
          </div>
        </div>
        <button class="close-btn" @click="$emit('close')">✕</button>
      </div>

      <!-- ===== 主体双栏 ===== -->
      <div class="detail-body" v-if="item">
        <!-- 左栏：表单 + 运行 -->
        <div class="left-column">
          <div class="form-section">
            <div class="form-title">上传图片</div>
            <ComfyuiFormRenderer
              ref="formRendererRef"
              :workflow-id="item?.workflowId ?? null"
              :hide-submit-button="true"
              @submit="handleFormSubmit"
            />

            <!-- 上次使用过的素材，可一键复用 -->
            <div v-if="lastUsedInputs.length > 0" class="last-used">
              <div class="lu-head">
                <span class="lu-title">上次使用</span>
                <span class="lu-sub">点击「复用」直接填入</span>
              </div>
              <div class="lu-row">
                <div
                  v-for="(material, i) in lastUsedInputs"
                  :key="`${material.nodeKey}_${i}`"
                  class="lu-item"
                >
                  <img :src="material.url" loading="lazy" alt="上次素材" />
                  <button class="lu-reuse" @click="applyMaterial(material)">复用</button>
                </div>
              </div>
            </div>
          </div>

          <div class="submit-card">
            <div class="price-row">
              <span class="price-label">预计消耗</span>
              <span class="price-value">- {{ credits }} 积分</span>
              <span class="price-help">计费说明</span>
            </div>
            <button class="run-btn" :disabled="runBtnLoading || submitting" @click="handleRunClick">
              <span v-if="submitting">提交中…</span>
              <span v-else-if="runBtnLoading">加载中…</span>
              <span v-else>立即拍摄</span>
            </button>
          </div>
        </div>

        <!-- 右栏：详情 / 我的生成 -->
        <div class="right-column">
          <div class="tabs-header">
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'detail' }"
              @click="activeTab = 'detail'"
            >应用详情</button>
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'mine' }"
              @click="switchToMine"
            >我的生成</button>
          </div>

          <!-- 应用详情 tab -->
          <div v-if="activeTab === 'detail'" class="detail-tab">
            <div class="author-bar">
              <div class="author-bar-left">
                <div class="author-avatar">{{ authorName.slice(0, 1) }}</div>
                <div class="author-info">
                  <div class="author-name">{{ authorName }}</div>
                  <div class="author-sub">AI 创作者</div>
                </div>
                <button class="follow-btn" disabled>关注</button>
              </div>
              <div class="author-bar-right">
                <button class="mini-link" disabled>工作流 ›</button>
                <button class="mini-link" disabled>API ›</button>
              </div>
            </div>

            <div class="intro-row" @click="introExpanded = !introExpanded">
              <span class="intro-label">应用介绍</span>
              <span class="intro-text">{{ item.description || '暂无介绍' }}</span>
              <span class="intro-arrow" :class="{ open: introExpanded }">⌄</span>
            </div>
            <p v-if="introExpanded" class="intro-full">{{ item.description || '暂无介绍' }}</p>

            <!-- 主体大图画廊 -->
            <div class="gallery-main">
              <!-- 生成中 / 排队 -->
              <div v-if="genState === 'queued' || genState === 'building'" class="canvas-progress">
                <div class="progress-spinner"></div>
                <div class="progress-title">
                  {{ genState === 'queued' ? '排队中，正在等待算力…' : '正在生成…' }}
                  <span class="progress-elapsed">已用 {{ elapsed }} 秒</span>
                </div>
                <div class="progress-bar-wrap">
                  <div class="progress-bar">
                    <div class="progress-bar-inner" :style="{ width: (genState === 'queued' ? 8 : Math.max(progress || 0, 5)) + '%' }"></div>
                  </div>
                  <div class="progress-num">{{ genState === 'queued' ? '排队' : (progress || 0) + '%' }}</div>
                </div>
                <div class="progress-sub" v-if="queueLocation > 0">当前队列位置：第 {{ queueLocation }} 位</div>
                <div class="progress-sub" v-else-if="genState === 'queued'">已提交，等待算力空闲（通常 1 分钟内）</div>
              </div>

              <!-- 成功：结果大图 -->
              <template v-else-if="genState === 'success'">
                <video v-if="resultType === WorkflowResultModelTypeEnum.VIDEO" :src="resultUrl" class="main-media" controls autoplay loop></video>
                <div v-else-if="resultType === WorkflowResultModelTypeEnum.AUDIO" class="audio-wrap">
                  <audio :src="resultUrl" controls></audio>
                </div>
                <template v-else>
                  <img :src="resultUrl" class="main-media clickable" alt="生成结果" @click="lightboxVisible = true" />
                  <div class="result-actions">
                    <a class="action-btn" :href="downloadHref(resultUrl)" :download="downloadName(resultUrl)">下载原图</a>
                    <button class="action-btn" @click="resetCanvas">返回详情</button>
                  </div>
                </template>

                <!-- 输入素材 → 生成结果 对比条 -->
                <div v-if="currentInputs.length > 0" class="compare-inline">
                  <div class="ci-side">
                    <img :src="currentInputs[0].url" alt="输入素材" loading="lazy" />
                    <span class="ci-tag">原图</span>
                  </div>
                  <span class="ci-arrow">→</span>
                  <button class="ci-side ci-output" @click="openResultCompare">
                    <img
                      v-if="resultType === WorkflowResultModelTypeEnum.IMAGE"
                      :src="resultUrl"
                      alt="生成结果"
                      loading="lazy"
                    />
                    <video
                      v-else-if="resultType === WorkflowResultModelTypeEnum.VIDEO"
                      :src="resultUrl"
                      muted
                    ></video>
                    <div v-else class="ci-file">音频/文件</div>
                    <span class="ci-tag">结果</span>
                  </button>
                  <button class="ci-compare-btn" @click="openResultCompare">对比查看</button>
                </div>
              </template>

              <!-- 失败 -->
              <div v-else-if="genState === 'failed'" class="canvas-error">
                <div class="error-icon">⚠️</div>
                <div class="error-title">生成失败</div>
                <div class="error-msg">{{ errorMsg || '任务执行失败，积分将自动退回，请调整参数后重试。' }}</div>
                <div class="result-actions">
                  <button class="action-btn primary" @click="retryFailed">重新生成</button>
                  <button class="action-btn" @click="resetCanvas">返回详情</button>
                </div>
              </div>

              <!-- 默认：商品封面大图 -->
              <template v-else>
                <img
                  v-if="displayArt"
                  :src="displayArt.url"
                  class="main-media clickable"
                  :alt="item.name"
                  @click="openArtPreview(displayArt)"
                />
                <div v-else class="tab-empty">
                  <div class="empty-text">暂无预览图</div>
                </div>
                <div v-if="mainThumbs.length > 0" class="thumb-row">
                  <button
                    v-for="(thumb, i) in mainThumbs"
                    :key="i"
                    class="thumb-cell"
                    :class="{ active: activeThumb === i }"
                    @click="activeThumb = i"
                  >
                    <img v-if="thumb.type === WorkflowResultModelTypeEnum.IMAGE" :src="thumb.url" loading="lazy" alt="缩略图" />
                    <video v-else-if="thumb.type === WorkflowResultModelTypeEnum.VIDEO" :src="thumb.url" muted></video>
                  </button>
                </div>
              </template>
            </div>
          </div>

          <!-- 我的生成 tab -->
          <div v-else class="mine-tab">
            <div v-if="myResults.length > 0" class="mine-list">
              <div v-for="art in myResults" :key="art.key" class="mine-preview" @click="openArtPreview(art)">
                <img v-if="art.type === WorkflowResultModelTypeEnum.IMAGE" :src="art.url" loading="lazy" alt="生成结果" />
                <video v-else-if="art.type === WorkflowResultModelTypeEnum.VIDEO" :src="art.url" muted></video>
                <div v-else class="result-file-hint">音频/文件</div>
                <button
                  v-if="hasInputs(art)"
                  class="compare-badge"
                  @click.stop="openCardCompare(art)"
                >对比</button>
                <div class="cell-time">{{ art.time }}</div>
              </div>
            </div>
            <div v-else class="tab-empty">
              <div class="empty-icon">🖼️</div>
              <div class="empty-text">暂无生成记录，点击「立即拍摄」开始创作</div>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 底部作品展示区（正常文档流，在双栏下方） ===== -->
      <div class="works-section" v-if="item">
        <div class="works-header">
          <span class="works-title">作品展示 <span class="works-count">({{ worksItems.length }})</span></span>
          <span class="works-tab active">评论</span>
          <button class="publish-btn" disabled>发布作品</button>
        </div>
        <div v-if="worksItems.length > 0" class="works-grid">
          <div
            v-for="art in worksItems"
            :key="art.key"
            class="works-cell"
            :class="{ showcase: isShowcaseMode }"
            @click="openArtPreview(art)"
          >
            <img v-if="art.type === WorkflowResultModelTypeEnum.IMAGE" :src="art.url" loading="lazy" alt="作品" />
            <video v-else-if="art.type === WorkflowResultModelTypeEnum.VIDEO" :src="art.url" muted></video>
            <button
              v-if="!isShowcaseMode && hasInputs(art)"
              class="compare-badge"
              @click.stop="openCardCompare(art)"
            >对比</button>
            <div class="works-author-mask">
              <span class="works-author">@{{ isShowcaseMode ? '西柚像素工坊' : authorName }}</span>
              <a
                v-if="isShowcaseMode"
                class="works-download"
                :href="downloadHref(art.url)"
                :download="downloadName(art.url)"
                @click.stop
              >下载</a>
            </div>
          </div>
        </div>
        <div v-else class="works-empty">暂无作品</div>
      </div>
    </div>

    <!-- 放大预览 -->
    <teleport to="body">
      <transition name="fade">
        <div v-if="lightboxVisible" class="lightbox-mask" @click="closeLightbox">
          <img v-if="lightboxSrc && lightboxType === WorkflowResultModelTypeEnum.IMAGE" :src="lightboxSrc" class="lightbox-img" @click.stop />
          <video v-else-if="lightboxSrc && lightboxType === WorkflowResultModelTypeEnum.VIDEO" :src="lightboxSrc" class="lightbox-img" controls autoplay loop @click.stop></video>
          <audio v-else-if="lightboxSrc" :src="lightboxSrc" controls autoplay @click.stop></audio>
          <a
            v-if="lightboxSrc"
            class="lightbox-download"
            :href="downloadHref(lightboxSrc)"
            :download="downloadName(lightboxSrc)"
            @click.stop
          >下载</a>
          <button class="lightbox-close" @click="closeLightbox">×</button>
        </div>
      </transition>
    </teleport>
    <!-- 前后对比层 -->
    <teleport to="body">
      <transition name="fade">
        <div v-if="compareVisible" class="compare-mask" @click="closeCompare">
          <div class="compare-dialog" @click.stop>
            <div class="compare-head">
              <span class="compare-title">原图 → 结果 对比</span>
              <button class="compare-close" @click="closeCompare">×</button>
            </div>
            <div class="compare-body">
              <div class="compare-pane">
                <div class="compare-media">
                  <img :src="compareInput" alt="输入素材" />
                </div>
                <span class="compare-label">输入素材</span>
              </div>
              <div class="compare-sep">→</div>
              <div class="compare-pane">
                <div class="compare-media">
                  <img
                    v-if="compareOutputType === WorkflowResultModelTypeEnum.IMAGE"
                    :src="compareOutput"
                    alt="生成结果"
                  />
                  <video
                    v-else-if="compareOutputType === WorkflowResultModelTypeEnum.VIDEO"
                    :src="compareOutput"
                    controls
                    autoplay
                    loop
                  ></video>
                  <audio v-else-if="compareOutput" :src="compareOutput" controls autoplay></audio>
                  <div v-else class="ci-file">暂无结果</div>
                </div>
                <span class="compare-label">生成结果</span>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElNotification, ElMessage } from 'element-plus'
import ComfyuiFormRenderer from './ComfyuiFormRenderer.vue'
import { comfyuiTaskApi } from '@/api/workflow-task/workflow-task'
import type { SubmitTaskApi, GetTaskProgressPageApi } from '@/api/workflow-task/types'
import { WorkflowFormTypeEnum, WorkflowTaskStatusEnum, WebSocketMessageTypeEnum, WorkflowResultModelTypeEnum } from '@/enums'
import { useTaskWebSocketStore } from '@/stores/modules/taskWebsocket'
import { useUserStore } from '@/stores/modules/user'
import type { WebSocketMessage } from '@/utils/taskWebsocketUtil'
import {
  saveTaskMaterials,
  bindTaskId,
  attachResultUrl,
  getLastInputs,
  findInputs,
  isImageFormType
} from '../utils/materialStore'
import type { MaterialInput } from '../utils/materialStore'


interface WorkflowItem {
  workflowId: number
  name: string
  description: string
  url: string
  categoryName: string
}

interface Props {
  item: WorkflowItem | null
}

const props = defineProps<Props>()
defineEmits<{
  close: []
}>()

const formRendererRef = ref<InstanceType<typeof ComfyuiFormRenderer> | null>(null)
const webSocketStore = useTaskWebSocketStore()
const userStore = useUserStore()

// ===== 静态信息 =====
const authorName = '浩哥'
const submitting = ref(false)
const runBtnLoading = ref(false)
const credits = ref(0)
const introExpanded = ref(false)
const activeThumb = ref(-1)

// 「立即拍摄」→ 触发表单渲染器提交（内部先校验，必填未填会提示且不提交）
const handleRunClick = () => {
  formRendererRef.value?.submitForm()
}

const tags = computed(() => {
  const list: string[] = []
  if (props.item?.categoryName) list.push(props.item.categoryName)
  return list
})

// ===== 运行次数 & 历史 =====
interface ArtItem {
  key: string | number
  url: string
  type: WorkflowResultModelTypeEnum
  time: string
  taskId?: string
}

const runCount = ref(0)
const myResults = ref<ArtItem[]>([])
const activeTab = ref<'detail' | 'mine'>('detail')

// ===== 输入素材缓存（对比 / 复用） =====
const materialsVersion = ref(0)
const lastRecordId = ref('')
const bumpMaterials = () => {
  materialsVersion.value++
}

const lastUsedInputs = computed<MaterialInput[]>(() => {
  materialsVersion.value
  return getLastInputs(props.item?.workflowId)
})

const currentInputs = computed<MaterialInput[]>(() => {
  materialsVersion.value
  if (genState.value !== 'success') return []
  return findInputs(props.item?.workflowId, {
    taskId: activeTaskId.value,
    resultUrl: resultUrl.value
  })
})

const inputsOfArt = (art: ArtItem): MaterialInput[] => {
  materialsVersion.value
  return findInputs(props.item?.workflowId, { taskId: art.taskId, resultUrl: art.url })
}

const hasInputs = (art: ArtItem) => inputsOfArt(art).length > 0

const applyMaterial = (material: MaterialInput) => {
  const matched = formRendererRef.value?.applyUploadValue(material.nodeKey, material.url)
  if (matched) {
    ElMessage.success('已填入上次素材，可直接重新选择覆盖')
  } else {
    ElMessage.warning('当前表单未找到对应素材字段')
  }
}

// ===== 对比层 =====
const compareVisible = ref(false)
const compareInput = ref('')
const compareOutput = ref('')
const compareOutputType = ref<WorkflowResultModelTypeEnum | ''>('')

const openCompare = (
  inputUrl: string,
  outputUrl: string,
  outputType: WorkflowResultModelTypeEnum | ''
) => {
  if (!inputUrl) return
  compareInput.value = inputUrl
  compareOutput.value = outputUrl
  compareOutputType.value = outputType
  compareVisible.value = true
}

const openResultCompare = () => {
  const input = currentInputs.value[0]
  if (!input) return
  openCompare(input.url, resultUrl.value, resultType.value)
}

const openCardCompare = (art: ArtItem) => {
  const input = inputsOfArt(art)[0]
  if (!input) return
  openCompare(input.url, art.url, art.type)
}

const closeCompare = () => {
  compareVisible.value = false
}

// 结果地址到达后写回素材记录，历史卡片即可按 URL 匹配
const rememberResult = (url: string) => {
  if (!url) return
  attachResultUrl(props.item?.workflowId, url, lastRecordId.value, activeTaskId.value)
  bumpMaterials()
}

const coverArt = computed<ArtItem | null>(() => {
  if (!props.item?.url) return null
  return {
    key: 'cover',
    url: props.item.url,
    type: WorkflowResultModelTypeEnum.IMAGE,
    time: ''
  }
})

// 详情画廊缩略图：仅本商品历史结果，无则整行隐藏
const mainThumbs = computed<ArtItem[]>(() => myResults.value.slice(0, 8))

// 当前主图：默认商品封面，点缩略图后切换
const displayArt = computed<ArtItem | null>(() => {
  if (activeThumb.value >= 0 && mainThumbs.value[activeThumb.value]) {
    return mainThumbs.value[activeThumb.value]
  }
  return coverArt.value
})

const fetchHistory = async () => {
  const name = props.item?.name
  if (!name) return
  // 未登录时不请求私有历史（避免 401 → 自动登出 → 刷新页面）
  if (!localStorage.getItem('token')) return
  try {
    const results: ArtItem[] = []
    const maxPages = 5
    let matched = 0
    for (let page = 1; page <= maxPages; page++) {
      const result: GetTaskProgressPageApi.Result = await comfyuiTaskApi.reqGetComfyuiTaskProgressPage({ page })
      const items = result.items || []
      for (const task of items) {
        if (task.workflowName !== name) continue
        matched++
        const model = task.workflowResultModel
        if (model?.url) {
          results.push({
            key: model.workflowResultId ?? model.url,
            url: model.url,
            type: model.type || WorkflowResultModelTypeEnum.IMAGE,
            time: task.createTime || '',
            taskId: task.taskId
          })
        }
      }
      if (items.length === 0 || items.length < 10) break
    }
    runCount.value = matched
    myResults.value = results
  } catch (error) {
    console.error('Failed to fetch task history:', error)
  }
}

// ===== 作品展示柜：优先真实用户生成记录（自动），其次手动橱窗图，最后访问者自己的 =====
const autoShowcase = ref<ArtItem[]>([])
const loadAutoShowcase = async () => {
  if (!props.item?.workflowId) { autoShowcase.value = []; return }
  try {
    const list = await comfyuiTaskApi.reqGetWorkflowShowcase({
      workflowId: props.item.workflowId,
      limit: 6
    })
    autoShowcase.value = (list || []).map((r, i) => ({
      key: `auto_${i}_${r.url}`,
      url: r.url,
      type: (r.type as WorkflowResultModelTypeEnum) || WorkflowResultModelTypeEnum.IMAGE,
      time: (r.createTime || '').split('/').join('-')
    }))
  } catch {
    autoShowcase.value = []
  }
}

// 手动橱窗图（管理员上传，生成记录不足时补位）
const showcaseUrls = ref<string[]>([])
const showcaseArtList = computed<ArtItem[]>(() =>
  showcaseUrls.value.map((url, i) => ({
    key: `showcase_${i}_${url}`,
    url,
    type: WorkflowResultModelTypeEnum.IMAGE,
    time: ''
  }))
)
const worksItems = computed<ArtItem[]>(() => {
  if (autoShowcase.value.length > 0) return autoShowcase.value
  if (showcaseArtList.value.length > 0) return showcaseArtList.value
  return myResults.value
})
const isShowcaseMode = computed(() => worksItems.value === showcaseArtList.value || autoShowcase.value.length > 0)

const switchToMine = () => {
  activeTab.value = 'mine'
  fetchHistory()
}

// ===== 积分价格（复用工作流接口配置） =====
const loadCredits = async () => {
  if (!props.item?.workflowId) return
  runBtnLoading.value = true
  try {
    const result = await comfyuiTaskApi.reqGetWorkflowResultModelflowsInterface({
      workflowId: props.item.workflowId
    })
    credits.value = result.creditsDeducted ?? 0
    showcaseUrls.value = result.showcaseUrls || []
    loadAutoShowcase()
  } catch (error) {
    console.error('Failed to load credits:', error)
  } finally {
    runBtnLoading.value = false
  }
}

// ===== 画布状态（保留 WebSocket 实时逻辑） =====
type GenState = 'idle' | 'queued' | 'building' | 'success' | 'failed'
const genState = ref<GenState>('idle')
const progress = ref(0)
const queueLocation = ref(0)
const activeTaskId = ref<string>('')
const resultUrl = ref('')
/** 让图片/视频真正“下载到本地”：走后端 ?download=1 强制附件下载（跨端口时 <a download> 会失效） */
const downloadHref = (url: string) => {
  if (!url) return url
  return url + (url.includes('?') ? '&' : '?') + 'download=1'
}
const downloadName = (url: string) => {
  try {
    const clean = url.split('?')[0]
    return clean.substring(clean.lastIndexOf('/') + 1) || 'aistudio'
  } catch {
    return 'aistudio'
  }
}
const resultType = ref<WorkflowResultModelTypeEnum | ''>('')
const errorMsg = ref('')
const lightboxVisible = ref(false)
const lightboxArt = ref<ArtItem | null>(null)

const lightboxSrc = computed(() => {
  if (genState.value === 'success' && resultUrl.value && resultType.value !== WorkflowResultModelTypeEnum.AUDIO) {
    return resultUrl.value
  }
  return lightboxArt.value?.url || ''
})

const lightboxType = computed<WorkflowResultModelTypeEnum | ''>(() => {
  if (genState.value === 'success' && resultUrl.value && resultType.value !== WorkflowResultModelTypeEnum.AUDIO) {
    return resultType.value
  }
  return lightboxArt.value?.type || ''
})

const resetCanvas = () => {
  genState.value = 'idle'
  progress.value = 0
  queueLocation.value = 0
  activeTaskId.value = ''
  resultUrl.value = ''
  resultType.value = ''
  errorMsg.value = ''
  activeThumb.value = -1
}

const retryFailed = () => {
  resetCanvas()
}

// 任务 id 到达后绑定到本次素材记录
watch(activeTaskId, id => {
  if (!id || !lastRecordId.value) return
  bindTaskId(props.item?.workflowId, lastRecordId.value, id)
  bumpMaterials()
})

const openArtPreview = (art: ArtItem | null) => {
  if (!art) return
  lightboxArt.value = art
  lightboxVisible.value = true
}

const closeLightbox = () => {
  lightboxVisible.value = false
  lightboxArt.value = null
}

// 切换商品时重置并重新加载
watch(
  () => props.item?.workflowId,
  () => {
    resetCanvas()
    activeTab.value = 'detail'
    lastRecordId.value = ''
    compareVisible.value = false
    showcaseUrls.value = []
    bumpMaterials()
    loadCredits()
    fetchHistory()
  },
  { immediate: true }
)

// ===== WebSocket 监听 =====
const isRelevantMessage = (message: WebSocketMessage) => {
  const data: any = message.parsedData
  if (!data || typeof data !== 'object') return false
  if (activeTaskId.value) {
    return data.taskId === activeTaskId.value
  }
  return !!data.workflowName && data.workflowName === props.item?.name
}

const syncCanvasFromMessages = () => {
  const messages = webSocketStore.taskMessages
  for (const message of messages) {
    if (!isRelevantMessage(message)) continue
    const data: any = message.parsedData
    if (data.taskId && !activeTaskId.value) {
      activeTaskId.value = data.taskId
    }

    if (message.type === WebSocketMessageTypeEnum.TASK_PROGRESS_UPDATE) {
      if (genState.value === 'queued' || genState.value === 'building') {
        genState.value = 'building'
        progress.value = data.progress ?? progress.value
      }
      return
    }

    if (message.type === WebSocketMessageTypeEnum.QUEUE_POSITION_UPDATE) {
      if (genState.value === 'queued') {
        queueLocation.value = data.location ?? data.position ?? queueLocation.value
      }
      return
    }

    if (message.type === WebSocketMessageTypeEnum.TASK_STATUS_CHANGE) {
      switch (data.status) {
        case WorkflowTaskStatusEnum.WAIT:
          genState.value = 'queued'
          if (typeof data.location === 'number') queueLocation.value = data.location
          break
        case WorkflowTaskStatusEnum.BUILD:
          genState.value = 'building'
          progress.value = data.progress || progress.value
          break
        case WorkflowTaskStatusEnum.SUCCEED:
          if (data.workflowResultModel?.url) {
            resultUrl.value = data.workflowResultModel.url
            resultType.value = data.workflowResultModel.type || WorkflowResultModelTypeEnum.IMAGE
            rememberResult(resultUrl.value)
            genState.value = 'success'
            fetchHistory()
          }
          break
        case WorkflowTaskStatusEnum.FAILED:
          genState.value = 'failed'
          errorMsg.value = data.message || ''
          break
        case WorkflowTaskStatusEnum.CANCELED:
          resetCanvas()
          break
      }
      return
    }
  }
}

watch(
  () => webSocketStore.taskMessages,
  () => syncCanvasFromMessages(),
  { deep: true, immediate: true }
)

// 兜底：任务列表刷新后同步一次状态
watch(
  () => webSocketStore.tasks,
  (tasks) => {
    if (genState.value !== 'queued' && genState.value !== 'building') return
    const task = tasks.find(t => activeTaskId.value ? t.taskId === activeTaskId.value : t.workflowName === props.item?.name)
    if (!task) return
    if (!activeTaskId.value && task.taskId) activeTaskId.value = task.taskId
    if (task.status === WorkflowTaskStatusEnum.WAIT) {
      genState.value = 'queued'
      if (typeof task.location === 'number') queueLocation.value = task.location
    } else if (task.status === WorkflowTaskStatusEnum.BUILD) {
      genState.value = 'building'
      progress.value = task.progress || progress.value
    } else if (task.status === WorkflowTaskStatusEnum.SUCCEED && task.workflowResultModel?.url) {
      resultUrl.value = task.workflowResultModel.url
      resultType.value = task.workflowResultModel.type || WorkflowResultModelTypeEnum.IMAGE
      rememberResult(resultUrl.value)
      genState.value = 'success'
      fetchHistory()
    } else if (task.status === WorkflowTaskStatusEnum.FAILED) {
      genState.value = 'failed'
    }
  },
  { deep: true }
)

onUnmounted(() => {
  lightboxVisible.value = false
  stopTaskWatch()
})

// ===== 任务进度轮询 + 计时（不依赖 WebSocket，保证界面一定更新）=====
const elapsed = ref(0)
let pollTimer: number | undefined
let elapsedTimer: number | undefined

const stopTaskWatch = () => {
  if (pollTimer) { window.clearInterval(pollTimer); pollTimer = undefined }
  if (elapsedTimer) { window.clearInterval(elapsedTimer); elapsedTimer = undefined }
}

const applyTask = (task: any) => {
  if (!task) return false
  if (task.status === WorkflowTaskStatusEnum.WAIT) {
    genState.value = 'queued'
    if (typeof task.location === 'number') queueLocation.value = task.location
  } else if (task.status === WorkflowTaskStatusEnum.BUILD) {
    genState.value = 'building'
    if (typeof task.progress === 'number') progress.value = task.progress
  } else if (task.status === WorkflowTaskStatusEnum.SUCCEED && task.workflowResultModel?.url) {
    resultUrl.value = task.workflowResultModel.url
    resultType.value = task.workflowResultModel.type || WorkflowResultModelTypeEnum.IMAGE
    rememberResult(resultUrl.value)
    genState.value = 'success'
    progress.value = 100
    fetchHistory()
    stopTaskWatch()
    return true
  } else if (task.status === WorkflowTaskStatusEnum.FAILED) {
    genState.value = 'failed'
    errorMsg.value = task.errorMessage || ''
    stopTaskWatch()
    return true
  }
  return false
}

const pollTaskStatus = async () => {
  try {
    const res: any = await comfyuiTaskApi.reqGetComfyuiTaskProgressPage({ page: 1 })
    const items: any[] = res?.items || []
    if (!items.length) return
    let task = activeTaskId.value ? items.find(t => t.taskId === activeTaskId.value) : undefined
    if (!task) {
      // 没拿到 taskId 时，取本商品最新的一条未完成任务
      task = items.find(t => (!props.item?.name || t.workflowName === props.item.name)
        && (t.status === WorkflowTaskStatusEnum.WAIT || t.status === WorkflowTaskStatusEnum.BUILD))
      if (task?.taskId) activeTaskId.value = task.taskId
    }
    if (task) applyTask(task)
  } catch (e) {
    // 忽略单次轮询失败
  }
}

const startTaskWatch = () => {
  stopTaskWatch()
  elapsed.value = 0
  elapsedTimer = window.setInterval(() => { elapsed.value += 1 }, 1000)
  // 立即查一次，然后每 2.5 秒轮询
  pollTaskStatus()
  pollTimer = window.setInterval(() => { pollTaskStatus() }, 2500)
}

// ===== 提交 =====
const handleFormSubmit = async (submitData: Record<string, any>) => {
  submitting.value = true
  try {
    const workflowInterface = await comfyuiTaskApi.reqGetWorkflowResultModelflowsInterface({
      workflowId: submitData.workflowId
    })

    const pickedInputs: MaterialInput[] = []
    const nodeContainer: SubmitTaskApi.Params['nodeContainer'] =
      workflowInterface.formContainer.map(formItem => {
        const fieldKey = `${formItem.nodeKey}_${formItem.inputs}`
        const nodeValue = submitData.formData[fieldKey] || ''

        const isUpload = [
          WorkflowFormTypeEnum.IMAGE_UPLOAD,
          WorkflowFormTypeEnum.IMAGE_SCRIBBLE,
          WorkflowFormTypeEnum.AUDIO_UPLOAD,
          WorkflowFormTypeEnum.VIDEO_UPLOAD
        ].includes(formItem.type)

        // 收集本次提交的图片类输入素材（data/blob 临时地址不缓存，避免撑爆 localStorage）
        if (isImageFormType(formItem.type) && typeof nodeValue === 'string' && nodeValue) {
          if (!nodeValue.startsWith('data:') && !nodeValue.startsWith('blob:')) {
            pickedInputs.push({ nodeKey: formItem.nodeKey, url: nodeValue, type: 'IMAGE' })
          }
        }

        return {
          nodeKey: formItem.nodeKey,
          inputs: formItem.inputs,
          nodeValue: String(nodeValue),
          isUpload
        }
      })

    const submitParams: SubmitTaskApi.Params = {
      workflowId: submitData.workflowId,
      nodeContainer: nodeContainer
    }

    await comfyuiTaskApi.reqSubmitComfyuiTask(submitParams)

    // 记录本次输入素材，供「对比」与「复用上次素材」使用
    lastRecordId.value = saveTaskMaterials(submitData.workflowId, pickedInputs)
    bumpMaterials()

    if (formRendererRef.value) {
      formRendererRef.value.resetForm()
    }

    ElNotification.success({
      title: '任务已提交',
      message: '生成完成后结果会实时显示在页面中',
      duration: 5000,
      showClose: true
    })

    userStore.refreshUserCredits()

    resetCanvas()
    genState.value = 'queued'
    activeTaskId.value = ''
    startTaskWatch()

    setTimeout(async () => {
      await webSocketStore.refreshTasks()
      pollTaskStatus()
    }, 800)
  } catch (error) {
    console.error('Submit task failed:', error)
    genState.value = 'failed'
    errorMsg.value = '任务提交失败，请稍后重试'
  } finally {
    submitting.value = false
    if (formRendererRef.value) {
      formRendererRef.value.setSubmitting(false)
    }
  }
}

onMounted(() => {
  loadCredits()
  fetchHistory()
})
</script>

<style scoped>
.app-detail-overlay {
  position: fixed;
  inset: 0;
  top: 64px;
  z-index: 999;
  background: #111111;
  overflow-y: auto;
  overflow-x: hidden;
}

.page-wrap {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 32px 48px;
}

/* ===== 顶部通栏 header ===== */
.detail-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 0 16px;
  border-bottom: 1px solid #232329;
}

.app-title {
  margin: 0 0 10px;
  font-size: 26px;
  font-weight: 700;
  color: #f0f0f2;
  line-height: 1.3;
}

.tag-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.app-tag {
  padding: 3px 12px;
  border-radius: 999px;
  background: #1b1b1f;
  border: 1px solid #2e2e36;
  color: #b8b8c0;
  font-size: 12px;
  line-height: 1.6;
}

.run-count {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 6px;
  font-size: 13px;
  color: #9a9aa2;
}

.run-count svg {
  color: #7c5cff;
}

.close-btn {
  background: transparent;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: #9a9aa2;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.close-btn:hover {
  background: #26262c;
  color: #f0f0f2;
}

/* ===== 主体双栏 ===== */
.detail-body {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  padding: 20px 0 0;
}

/* 左栏：固定宽度表单卡 */
.left-column {
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  background: #1b1b1f;
  border: 1px solid #2a2a30;
  overflow: hidden;
}

.form-section {
  padding: 16px;
  flex: 1;
}

.form-title {
  font-size: 15px;
  font-weight: 600;
  color: #f0f0f2;
  margin-bottom: 12px;
}

.form-section :deep(.comfyui-form-renderer) {
  height: auto;
}

.form-section :deep(.form-content) {
  overflow: visible;
  padding: 0;
}

.form-section :deep(.el-form-item) {
  margin-bottom: 12px;
}

.form-section :deep(.el-form-item__label) {
  color: #c8c8d0;
  padding-bottom: 4px;
}

.form-section :deep(.el-input__wrapper),
.form-section :deep(.el-textarea__inner),
.form-section :deep(.el-select__wrapper) {
  background-color: #232329;
  box-shadow: 0 0 0 1px #33333a inset;
}

.form-section :deep(.el-input__inner),
.form-section :deep(.el-textarea__inner) {
  color: #e8e8ee;
}

.form-section :deep(.el-textarea__inner) {
  min-height: 56px !important;
}

/* 底部价格 + 运行按钮 */
.submit-card {
  flex-shrink: 0;
  padding: 14px 16px 16px;
  background: #1b1b1f;
  border-top: 1px solid #26262c;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #2e2e36;
  border-radius: 10px;
  background: #17171b;
}

.price-label {
  font-size: 13px;
  color: #7c7c85;
}

.price-value {
  font-size: 17px;
  font-weight: 700;
  color: #7c5cff;
}

.price-help {
  margin-left: auto;
  font-size: 12px;
  color: #7c7c85;
  cursor: not-allowed;
}

.run-btn {
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 12px;
  background: #7c5cff;
  color: #111111;
  font-size: 17px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.run-btn:hover:not(:disabled) {
  background: #d8ff5c;
}

.run-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 右栏 */
.right-column {
  flex: 1;
  min-width: 0;
  border-radius: 16px;
  background: #1b1b1f;
  border: 1px solid #2a2a30;
  display: flex;
  flex-direction: column;
}

.tabs-header {
  display: flex;
  gap: 6px;
  padding: 8px 16px 0;
  border-bottom: 1px solid #26262c;
  flex-shrink: 0;
}

.tab-btn {
  padding: 10px 18px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: #9a9aa2;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-btn:hover {
  color: #e8e8ee;
}

.tab-btn.active {
  color: #7c5cff;
  font-weight: 600;
  border-bottom-color: #7c5cff;
}

.detail-tab {
  padding: 16px 20px 24px;
}

/* 作者条 */
.author-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
}

.author-bar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-bar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.author-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2e2e36, #232329);
  border: 1px solid #3a3a42;
  color: #7c5cff;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.author-info {
  min-width: 0;
}

.author-name {
  font-size: 14px;
  font-weight: 600;
  color: #f0f0f2;
}

.author-sub {
  font-size: 12px;
  color: #7c7c85;
  margin-top: 2px;
}

.follow-btn {
  padding: 6px 18px;
  border-radius: 999px;
  border: 1px solid #3a3a42;
  background: #232329;
  color: #7c7c85;
  font-size: 13px;
  cursor: not-allowed;
  opacity: 0.7;
}

.mini-link {
  background: transparent;
  border: none;
  padding: 0;
  font-size: 13px;
  color: #9a9aa2;
  cursor: not-allowed;
  opacity: 0.8;
}

/* 应用介绍行 */
.intro-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0 6px;
  cursor: pointer;
}

.intro-label {
  font-size: 13px;
  color: #7c7c85;
  flex-shrink: 0;
}

.intro-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #b8b8c0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.intro-arrow {
  color: #7c7c85;
  font-size: 14px;
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.intro-arrow.open {
  transform: rotate(180deg);
}

.intro-full {
  margin: 0 0 8px;
  font-size: 13px;
  color: #b8b8c0;
  line-height: 1.7;
  white-space: pre-wrap;
}

/* 大图画廊 */
.gallery-main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  min-height: 480px;
}

.main-media {
  display: block;
  max-width: min(560px, 92%);
  max-height: 560px;
  object-fit: contain;
  border-radius: 12px;
  border: 1px solid #2a2a30;
  background: #17171b;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
}

.main-media.clickable {
  cursor: zoom-in;
}

.thumb-row {
  display: flex;
  gap: 10px;
  max-width: 100%;
  overflow-x: auto;
  padding-bottom: 6px;
}

.thumb-row::-webkit-scrollbar {
  height: 6px;
}

.thumb-row::-webkit-scrollbar-thumb {
  background: #33333a;
  border-radius: 3px;
}

.thumb-cell {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  padding: 0;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid transparent;
  background: #17171b;
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.thumb-cell.active {
  border-color: #7c5cff;
}

.thumb-cell img,
.thumb-cell video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 生成进度 / 失败 */
.canvas-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 80px 32px;
  width: 100%;
  min-height: 480px;
  background: radial-gradient(ellipse at 50% 30%, #1c1c22 0%, #17171b 65%);
  border-radius: 12px;
  border: 1px solid #2a2a30;
}

.progress-spinner {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 4px solid #2a2a30;
  border-top-color: #7c5cff;
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.progress-title {
  font-size: 18px;
  font-weight: 600;
  color: #f0f0f2;
}

.progress-bar-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(360px, 70%);
}

.progress-bar {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: #2a2a30;
  overflow: hidden;
}

.progress-bar-inner {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #5b3fd6, #7c5cff);
  transition: width 0.4s ease;
}

.progress-num {
  font-size: 13px;
  color: #7c5cff;
  min-width: 36px;
  text-align: right;
}

.progress-sub {
  font-size: 13px;
  color: #7c7c85;
}

.audio-wrap audio {
  width: min(420px, 80%);
}

.result-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  padding: 8px 20px;
  border-radius: 8px;
  border: 1px solid #3a3a42;
  background: #232329;
  color: #e8e8ee;
  font-size: 14px;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.2s ease;
}

.action-btn:hover {
  border-color: #7c5cff;
  color: #7c5cff;
}

.action-btn.primary {
  background: #7c5cff;
  border-color: #7c5cff;
  color: #111111;
  font-weight: 600;
}

.action-btn.primary:hover {
  background: #d8ff5c;
  color: #111111;
}

.canvas-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 32px;
  width: 100%;
  min-height: 480px;
  text-align: center;
  border: 1px solid #2a2a30;
  border-radius: 12px;
  background: #17171b;
}

.error-icon {
  font-size: 44px;
}

.error-title {
  font-size: 18px;
  font-weight: 600;
  color: #ff6b6b;
}

.error-msg {
  font-size: 14px;
  color: #9a9aa2;
  line-height: 1.6;
  max-width: 480px;
}

/* 我的生成 tab */
.mine-tab {
  padding: 20px;
}

.mine-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

.mine-preview {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  background: #17171b;
  border: 1px solid #2a2a30;
  cursor: zoom-in;
  transition: border-color 0.2s ease;
}

.mine-preview:hover {
  border-color: #7c5cff;
}

.mine-preview img,
.mine-preview video {
  display: block;
  width: auto;
  max-width: 100%;
  height: auto;
  max-height: 560px;
  margin: 0 auto;
  object-fit: contain;
}

.cell-time {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 14px 10px 6px;
  font-size: 11px;
  color: #c8c8d0;
  background: linear-gradient(180deg, rgba(17, 17, 17, 0) 0%, rgba(17, 17, 17, 0.85) 100%);
}

.result-file-hint {
  width: 220px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #7c7c85;
  font-size: 13px;
}

.tab-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 60px 20px;
}

.empty-icon {
  font-size: 40px;
  opacity: 0.7;
}

.empty-text {
  font-size: 14px;
  color: #7c7c85;
}

/* ===== 底部作品展示区（正常文档流） ===== */
.works-section {
  margin-top: 32px;
  width: 100%;
}

.works-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 16px;
}

.works-title {
  font-size: 17px;
  font-weight: 600;
  color: #f0f0f2;
}

.works-count {
  color: #7c5cff;
}

.works-tab {
  font-size: 14px;
  color: #7c7c85;
  cursor: not-allowed;
}

.works-tab.active {
  color: #e8e8ee;
  font-weight: 600;
}

.publish-btn {
  margin-left: auto;
  padding: 7px 18px;
  border-radius: 8px;
  border: none;
  background: #7c5cff;
  color: #111111;
  font-size: 13px;
  font-weight: 600;
  cursor: not-allowed;
  opacity: 0.75;
}

.works-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.works-cell {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  background: #17171b;
  border: 1px solid #2a2a30;
  aspect-ratio: 3 / 4;
  cursor: zoom-in;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.works-cell:hover {
  border-color: #7c5cff;
  transform: translateY(-2px);
}

.works-cell img,
.works-cell video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.works-author-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 22px 10px 8px;
  background: linear-gradient(180deg, rgba(17, 17, 17, 0) 0%, rgba(17, 17, 17, 0.85) 100%);
}

.works-author {
  font-size: 12px;
  color: #e8e8ee;
}

/* 公共展示柜卡片：9:16 方角，hover 放大 */
.works-cell.showcase {
  aspect-ratio: 9 / 16;
  border-radius: 0;
}

.works-cell.showcase img {
  transition: transform 0.3s ease;
}

.works-cell.showcase:hover img {
  transform: scale(1.06);
}

.works-author-mask {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.works-download {
  flex-shrink: 0;
  font-size: 12px;
  color: #7c5cff;
  text-decoration: none;
}

.works-download:hover {
  color: #d8ff5c;
}

.works-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 14px;
  color: #7c7c85;
  border: 1px dashed #2e2e36;
  border-radius: 16px;
  background: #1b1b1f;
}

/* ===== 放大预览 ===== */
.lightbox-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(0, 0, 0, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
}

.lightbox-img {
  max-width: 94vw;
  max-height: 90vh;
  object-fit: contain;
  border-radius: 8px;
}

.lightbox-download {
  position: absolute;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 28px;
  border-radius: 999px;
  background: #7c5cff;
  color: #111111;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.lightbox-download:hover {
  background: #d8ff5c;
}

.lightbox-close {
  position: absolute;
  top: 20px;
  right: 28px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 24px;
  cursor: pointer;
}

.lightbox-close:hover {
  background: rgba(255, 255, 255, 0.22);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ===== 上次使用素材 ===== */
.last-used {
  margin-top: 4px;
  padding: 12px 12px 10px;
  border: 1px dashed #33333a;
  border-radius: 12px;
  background: #17171b;
}

.lu-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
}

.lu-title {
  font-size: 13px;
  font-weight: 600;
  color: #e8e8ee;
}

.lu-sub {
  font-size: 11px;
  color: #7c7c85;
}

.lu-row {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.lu-row::-webkit-scrollbar {
  height: 6px;
}

.lu-row::-webkit-scrollbar-thumb {
  background: #33333a;
  border-radius: 3px;
}

.lu-item {
  flex-shrink: 0;
  width: 72px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lu-item img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #2e2e36;
  background: #111111;
  display: block;
}

.lu-reuse {
  width: 100%;
  padding: 4px 0;
  border-radius: 6px;
  border: 1px solid #3a3a42;
  background: #232329;
  color: #7c5cff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.lu-reuse:hover {
  border-color: #7c5cff;
  background: #2b2b32;
}

/* ===== 结果页 输入 → 结果 对比条 ===== */
.compare-inline {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
  padding: 12px;
  border: 1px solid #2a2a30;
  border-radius: 12px;
  background: #17171b;
}

.ci-side {
  position: relative;
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  padding: 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #2e2e36;
  background: #111111;
}

button.ci-side {
  cursor: zoom-in;
}

.ci-side img,
.ci-side video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.ci-tag {
  position: absolute;
  left: 6px;
  bottom: 6px;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(17, 17, 17, 0.75);
  color: #e8e8ee;
  font-size: 11px;
  line-height: 1.6;
}

.ci-arrow {
  color: #7c5cff;
  font-size: 18px;
  font-weight: 700;
}

.ci-compare-btn {
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid #3a3a42;
  background: #232329;
  color: #e8e8ee;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ci-compare-btn:hover {
  border-color: #7c5cff;
  color: #7c5cff;
}

.ci-file {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #7c7c85;
}

/* 卡片「对比」角标 */
.compare-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 5;
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid rgba(124, 92, 255, 0.55);
  background: rgba(17, 17, 17, 0.72);
  color: #7c5cff;
  font-size: 12px;
  cursor: pointer;
  backdrop-filter: blur(4px);
  transition: all 0.2s ease;
}

.compare-badge:hover {
  background: #7c5cff;
  color: #111111;
}

/* ===== 对比层 ===== */
.compare-mask {
  position: fixed;
  inset: 0;
  z-index: 3200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.9);
}

.compare-dialog {
  width: 100%;
  max-width: 1100px;
  max-height: 92vh;
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  border: 1px solid #2a2a30;
  background: #17171b;
  overflow: hidden;
}

.compare-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 1px solid #26262c;
  flex-shrink: 0;
}

.compare-title {
  font-size: 15px;
  font-weight: 600;
  color: #f0f0f2;
}

.compare-close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: #232329;
  color: #e8e8ee;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}

.compare-close:hover {
  background: #2e2e36;
}

.compare-body {
  display: flex;
  align-items: stretch;
  gap: 16px;
  padding: 18px;
  overflow: auto;
  min-height: 0;
}

.compare-pane {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.compare-media {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  border: 1px solid #2a2a30;
  background: #111111;
  overflow: hidden;
}

.compare-media img,
.compare-media video {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.compare-media audio {
  width: min(360px, 90%);
  margin: 40px auto;
}

.compare-label {
  text-align: center;
  font-size: 13px;
  color: #9a9aa2;
  flex-shrink: 0;
}

.compare-sep {
  align-self: center;
  color: #7c5cff;
  font-size: 22px;
  font-weight: 700;
  flex-shrink: 0;
}

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .page-wrap {
    padding: 0 16px 32px;
  }

  .detail-body {
    flex-direction: column;
  }

  .left-column {
    width: 100%;
  }

  .gallery-main {
    min-height: 320px;
  }
}

@media (max-width: 720px) {
  .compare-mask {
    padding: 12px;
  }

  .compare-body {
    flex-direction: column;
    align-items: stretch;
  }

  .compare-sep {
    transform: rotate(90deg);
  }

  .compare-media img,
  .compare-media video {
    max-height: 42vh;
  }

  .compare-inline {
    justify-content: flex-start;
  }
}
.progress-elapsed {
  margin-left: 8px;
  font-size: 12px;
  color: #8b8b99;
  font-weight: 400;
}
</style>