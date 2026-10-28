import { get, post } from '@/utils/requestUtil'

export interface SystemResultItem {
  id: number
  taskId: string
  workflowId: number | null
  workflowName: string
  userId: number
  userAccount: string
  type: string
  url: string
  createTime: string
}

export interface SystemResultPage {
  total: number
  items: SystemResultItem[]
}

export const systemResultApi = {
  /** 全站作品分页 */
  page: (params: {
    page?: number
    size?: number
    userId?: number
    workflowId?: number
    keyword?: string
  }) => get<SystemResultPage>('/system/result/page', params),

  /** 批量删除（记录 + 磁盘文件） */
  batchDelete: (ids: number[]) => post<string>('/system/result/batch-delete', { ids }),
}
