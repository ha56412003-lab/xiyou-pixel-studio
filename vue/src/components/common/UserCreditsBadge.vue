<template>
  <div class="user-credits-badge">
    <div class="credits-display">
      <el-icon class="credits-icon" :size="18">
        <Coin />
      </el-icon>
      <span class="credits-text">{{ formatCredits(userCredits?.availableCredits || 0) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElIcon } from 'element-plus'
import { Coin } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/modules/user'

const userStore = useUserStore()

// 获取用户积分信息
const userCredits = computed(() => userStore.userCredits)

// 格式化积分显示：精确到个位（以前 >=10000 会显示成 10000.0k，扣 15 分看不出变化）
const formatCredits = (credits: number) => {
  return Number(credits || 0).toLocaleString('en-US')
}

// 组件挂载时获取积分信息
onMounted(async () => {
  if (!userCredits.value) {
    await userStore.fetchUserCredits()
  }
})
</script>

<style scoped>
.user-credits-badge {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.credits-display {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  background: #c8f542;
  border: 1px solid #d8ff5e;

  transition: all 0.3s ease;
}

.credits-display:hover {
  background: #d8ff5e;
  box-shadow: 0 2px 10px rgba(200, 245, 66, 0.4);
}

.credits-icon {
  color: #0d0d12;
  flex-shrink: 0;
}

.credits-text {
  font-size: 14px;
  font-weight: 700;
  color: #0d0d12;
  min-width: 20px;
  text-align: center;
}



/* 响应式适配 */
@media (max-width: 768px) {
  .credits-display {
    padding: 4px 8px;
    gap: 4px;
  }
  
  .credits-text {
    font-size: 13px;
  }
}

@media (max-width: 480px) {
  .credits-text {
    display: none;
  }
  
  .credits-display {
    padding: 6px;
    min-width: 32px;
    justify-content: center;
  }
}
</style> 