<template>
  <div class="loading-state">
    <div class="loading-cards">
      <div v-for="i in 6" :key="i" class="skeleton-card">
        <el-skeleton animated>
          <template #template>
            <div class="skeleton-image"></div>
            <div class="skeleton-content">
              <el-skeleton-item variant="h3" style="width: 80%" />
              <el-skeleton-item variant="text" />
              <el-skeleton-item variant="text" style="width: 60%" />
            </div>
          </template>
        </el-skeleton>
      </div>
    </div>
  </div>
</template>


<style scoped>
.loading-state {
  padding: 24px 0;
}

.loading-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  width: 100%;
}

.skeleton-card {
  background: #1b1b1f;
  border-radius: 0;
  overflow: hidden;
  padding: 0;
  border: 1px solid #2a2a30;
}

.skeleton-image {
  width: 100%;
  aspect-ratio: 9 / 16;
  background: #15151a;
  border-radius: 0;
  margin-bottom: 0;
}

.skeleton-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

/* 响应式设计 */
@media (max-width: 1279px) {
  .loading-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px;
  }
}

@media (max-width: 899px) {
  .loading-cards {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
}
</style> 