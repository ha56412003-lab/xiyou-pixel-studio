/**
 * 任务输入素材本地缓存
 * - 按 workflowId 分组，每个工作流保留最近 20 条
 * - 每条记录与 taskId / 结果 URL 关联，便于「输入 → 结果」对比
 * - 所有读写均 try/catch，序列化只保留 { nodeKey, url, type }
 */
import { WorkflowFormTypeEnum } from '@/enums'

export interface MaterialInput {
  nodeKey: string
  url: string
  type: string
}

export interface MaterialRecord {
  id: string
  taskId: string
  resultUrl: string
  inputs: MaterialInput[]
  createTime: number
}

const STORAGE_KEY = 'comfyui:task-materials:v1'
const MAX_PER_WORKFLOW = 20

type MaterialStore = Record<string, MaterialRecord[]>

const IMAGE_FORM_TYPES: string[] = [
  WorkflowFormTypeEnum.IMAGE_UPLOAD,
  WorkflowFormTypeEnum.IMAGE_SCRIBBLE
]

/** 表单字段是否为图片类上传字段（可作为「输入素材」） */
export const isImageFormType = (type: string) => IMAGE_FORM_TYPES.includes(type)

const toKey = (workflowId: number | string | null | undefined): string => {
  if (workflowId === null || workflowId === undefined) return ''
  const key = String(workflowId).trim()
  return key && key !== 'null' && key !== 'undefined' ? key : ''
}

const normalizeInput = (raw: any): MaterialInput | null => {
  if (!raw || typeof raw !== 'object') return null
  if (typeof raw.nodeKey !== 'string' || typeof raw.url !== 'string') return null
  if (!raw.url) return null
  return {
    nodeKey: raw.nodeKey,
    url: raw.url,
    type: typeof raw.type === 'string' && raw.type ? raw.type : 'IMAGE'
  }
}

const readStore = (): MaterialStore => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}

    const store: MaterialStore = {}
    Object.keys(parsed as Record<string, unknown>).forEach(key => {
      const list = (parsed as any)[key]
      if (!Array.isArray(list)) return
      const records: MaterialRecord[] = []
      list.forEach((item: any) => {
        if (!item || !Array.isArray(item.inputs)) return
        const inputs: MaterialInput[] = []
        item.inputs.forEach((rawInput: any) => {
          const input = normalizeInput(rawInput)
          if (input) inputs.push(input)
        })
        if (inputs.length === 0) return
        records.push({
          id: typeof item.id === 'string' ? item.id : '',
          taskId: typeof item.taskId === 'string' ? item.taskId : '',
          resultUrl: typeof item.resultUrl === 'string' ? item.resultUrl : '',
          inputs,
          createTime: typeof item.createTime === 'number' ? item.createTime : 0
        })
      })
      if (records.length > 0) store[key] = records.slice(0, MAX_PER_WORKFLOW)
    })
    return store
  } catch (error) {
    console.warn('[materialStore] 读取本地素材缓存失败', error)
    return {}
  }
}

const writeStore = (store: MaterialStore): boolean => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
    return true
  } catch (error) {
    console.warn('[materialStore] 写入本地素材缓存失败', error)
    return false
  }
}

/** 记录一次提交的输入素材，返回记录 id（用于后续绑定 taskId / 结果 URL） */
export const saveTaskMaterials = (
  workflowId: number | string | null | undefined,
  inputs: MaterialInput[]
): string => {
  try {
    const key = toKey(workflowId)
    if (!key || !Array.isArray(inputs)) return ''

    const cleaned: MaterialInput[] = []
    inputs.forEach(raw => {
      const input = normalizeInput(raw)
      if (input) cleaned.push(input)
    })
    if (cleaned.length === 0) return ''

    const id = `m_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const store = readStore()
    const list = store[key] || []
    list.unshift({
      id,
      taskId: '',
      resultUrl: '',
      inputs: cleaned,
      createTime: Date.now()
    })
    store[key] = list.slice(0, MAX_PER_WORKFLOW)
    return writeStore(store) ? id : ''
  } catch (error) {
    console.warn('[materialStore] 保存素材失败', error)
    return ''
  }
}

/** 任务 id 到达后回填到记录上 */
export const bindTaskId = (
  workflowId: number | string | null | undefined,
  recordId: string,
  taskId: string
) => {
  try {
    const key = toKey(workflowId)
    if (!key || !recordId || !taskId) return
    const store = readStore()
    const list = store[key]
    if (!list) return
    const record = list.find(item => item.id === recordId)
    if (!record) return
    record.taskId = taskId
    writeStore(store)
  } catch (error) {
    console.warn('[materialStore] 绑定 taskId 失败', error)
  }
}

/** 生成成功后把结果 URL 回填到记录上，历史卡片即可按 URL 匹配输入素材 */
export const attachResultUrl = (
  workflowId: number | string | null | undefined,
  resultUrl: string,
  recordId = '',
  taskId = ''
) => {
  try {
    const key = toKey(workflowId)
    if (!key || !resultUrl) return
    const store = readStore()
    const list = store[key]
    if (!list || list.length === 0) return

    let record = recordId ? list.find(item => item.id === recordId) : undefined
    if (!record && taskId) record = list.find(item => item.taskId === taskId)
    if (!record) return

    record.resultUrl = resultUrl
    if (taskId && !record.taskId) record.taskId = taskId
    writeStore(store)
  } catch (error) {
    console.warn('[materialStore] 关联结果 URL 失败', error)
  }
}

/** 「上次使用」：取该工作流最近一条素材记录 */
export const getLastInputs = (workflowId: number | string | null | undefined): MaterialInput[] => {
  const key = toKey(workflowId)
  if (!key) return []
  const store = readStore()
  const list = store[key] || []
  return list.length > 0 ? list[0].inputs.slice() : []
}

/** 按 taskId 优先、结果 URL 兜底匹配输入素材 */
export const findInputs = (
  workflowId: number | string | null | undefined,
  query: { taskId?: string; resultUrl?: string } = {}
): MaterialInput[] => {
  try {
    const key = toKey(workflowId)
    if (!key) return []
    const store = readStore()
    const list = store[key] || []
    if (list.length === 0) return []

    if (query.taskId) {
      const hit = list.find(item => item.taskId && item.taskId === query.taskId)
      if (hit) return hit.inputs.slice()
    }
    if (query.resultUrl) {
      const hit = list.find(item => item.resultUrl && item.resultUrl === query.resultUrl)
      if (hit) return hit.inputs.slice()
    }
    return []
  } catch (error) {
    console.warn('[materialStore] 查询素材失败', error)
    return []
  }
}
