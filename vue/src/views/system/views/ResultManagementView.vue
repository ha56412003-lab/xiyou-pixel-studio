<template>
  <div class="result-management">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="商品名 / 任务ID"
        clearable
        style="width: 200px"
        @keyup.enter="reload"
      />
      <el-input
        v-model="filters.userId"
        placeholder="用户ID"
        clearable
        style="width: 120px"
        @keyup.enter="reload"
      />
      <el-input
        v-model="filters.workflowId"
        placeholder="商品ID"
        clearable
        style="width: 120px"
        @keyup.enter="reload"
      />
      <el-button type="primary" :loading="loading" @click="reload">查询</el-button>
      <el-button :disabled="loading" @click="resetFilters">重置</el-button>

      <div class="spacer"></div>

      <el-button
        type="danger"
        :disabled="selected.length === 0"
        :loading="deleting"
        @click="handleBatchDelete"
      >
        删除选中（{{ selected.length }}）
      </el-button>
    </div>

    <!-- 列表 -->
    <el-table
      v-loading="loading"
      :data="items"
      height="calc(100% - 118px)"
      border
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="46" />
      <el-table-column label="预览" width="90">
        <template #default="{ row }">
          <el-image
            v-if="row.type === 'IMAGE'"
            :src="row.url"
            :preview-src-list="[row.url]"
            fit="cover"
            style="width: 56px; height: 56px; border-radius: 6px"
            preview-teleported
          />
          <a v-else :href="row.url" target="_blank" class="file-link">{{ row.type }}</a>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="workflowName" label="商品" min-width="180" show-overflow-tooltip />
      <el-table-column prop="userAccount" label="用户" width="180" show-overflow-tooltip />
      <el-table-column prop="taskId" label="任务ID" width="300" show-overflow-tooltip />
      <el-table-column prop="createTime" label="生成时间" width="180" />
    </el-table>

    <!-- 分页 -->
    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[20, 50, 100]"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemResultApi } from '@/api/system-result/system-result'
import type { SystemResultItem } from '@/api/system-result/system-result'

const loading = ref(false)
const deleting = ref(false)
const items = ref<SystemResultItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selected = ref<SystemResultItem[]>([])

const filters = reactive({
  keyword: '',
  userId: '',
  workflowId: '',
})

const load = async () => {
  loading.value = true
  try {
    const data = await systemResultApi.page({
      page: page.value,
      size: size.value,
      keyword: filters.keyword || undefined,
      userId: filters.userId ? Number(filters.userId) : undefined,
      workflowId: filters.workflowId ? Number(filters.workflowId) : undefined,
    })
    items.value = data?.items || []
    total.value = data?.total || 0
  } catch (e) {
    console.error('加载作品失败', e)
  } finally {
    loading.value = false
  }
}

const reload = () => {
  page.value = 1
  load()
}

const resetFilters = () => {
  filters.keyword = ''
  filters.userId = ''
  filters.workflowId = ''
  reload()
}

const onSizeChange = () => {
  page.value = 1
  load()
}

const onSelectionChange = (rows: SystemResultItem[]) => {
  selected.value = rows
}

const handleBatchDelete = async () => {
  if (!selected.value.length) return
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${selected.value.length} 条作品？图片文件会一起删除，不可恢复。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    const ids = selected.value.map(i => i.id)
    await systemResultApi.batchDelete(ids)
    ElMessage.success(`已删除 ${ids.length} 条`)
    selected.value = []
    load()
  } catch (e) {
    console.error('删除失败', e)
  } finally {
    deleting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.result-management {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.spacer {
  flex: 1;
}
.pager {
  display: flex;
  justify-content: flex-end;
}
.file-link {
  color: var(--el-color-primary);
  text-decoration: underline;
}
</style>
