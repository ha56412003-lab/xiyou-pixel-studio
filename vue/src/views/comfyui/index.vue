<template>
  <div class="xy-home">
    <!-- ===== Hero ===== -->
    <section class="hero">
      <div class="hero-inner">
        <div class="hero-copy">
          <span class="hero-eyebrow">
            <span class="dot"></span>西柚像素工坊 · XIYOU
          </span>
          <h1 class="hero-title">
            XIYOU 的<span class="hl">AI 数码照相馆</span>
          </h1>
          <p class="hero-sub">
            西柚像素工坊 · XIYOU —— 一句话生成图片与视频，不用碰节点。挑一个项目，上传素材，剩下的交给像素工坊。
          </p>
          <div class="hero-actions">
            <button class="btn-primary" type="button" @click="scrollToWorkflows">
              开始拍摄
              <span class="btn-arrow">→</span>
            </button>
            <button class="btn-ghost" type="button" @click="goWorks">我的相册</button>
          </div>
        </div>

        <!-- 产品预览：用站内商品封面叠放 -->
        <div class="hero-preview" aria-hidden="true">
          <div class="preview-stack">
            <div
              v-for="(p, i) in previewItems"
              :key="p.id"
              class="preview-card"
              :class="`pc-${i + 1}`"
            >
              <img
                v-if="p.imageUrl && !coverErrors[p.id]"
                :src="cardImage(p)"
                :alt="p.title"
                loading="lazy"
                @error="onCardImageError(p)"
              />
              <div v-else class="preview-fallback">{{ initialOf(p.title) }}</div>
            </div>
            <div v-if="previewItems.length < 3" class="preview-card pc-empty">
              <span class="preview-empty-text">XIYOU</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 分类模块 / 搜索结果 ===== -->
    <section id="workflows" ref="listSection" class="list-section">
      <div class="list-head">
        <div class="list-title-wrap">
          <h2 class="list-title">{{ isSearching ? '搜索结果' : '摄影项目' }}</h2>
          <p v-if="!isSearching" class="list-sub">换装、换人、动作迁移 —— 上传照片就能取片，无需配置节点</p>
          <p v-else class="list-sub">关键词「{{ searchParams.prompt }}」的匹配结果</p>
        </div>

        <div class="search-box">
          <el-icon :size="16" class="search-icon"><Search /></el-icon>
          <input
            v-model="searchParams.prompt"
            class="search-input"
            type="text"
            placeholder="搜索工作流 / 关键词"
          />
          <button
            v-if="searchParams.prompt"
            class="search-clear"
            type="button"
            aria-label="清空搜索"
            @click="clearSearch"
          >
            ✕
          </button>
        </div>
      </div>

      <!-- 搜索结果 -->
      <template v-if="isSearching">
        <div v-if="searchItems.length > 0" class="product-grid">
          <article
            v-for="item in searchItems"
            :key="item.id"
            class="product-card"
            @click="handleItemClick(item)"
          >
            <div class="card-cover">
              <img
                v-if="item.imageUrl && !coverErrors[item.id]"
                :src="cardImage(item)"
                :alt="item.title"
                loading="lazy"
                @error="onCardImageError(item)"
              />
              <div v-else class="cover-fallback">{{ initialOf(item.title) }}</div>
              <span class="card-cat">{{ item.categoryName || 'AI 工作流' }}</span>
              <div class="card-overlay">
                <h3 class="card-title" :title="item.title">{{ item.title }}</h3>
                <div class="card-meta-row">
                  <span class="card-price">{{ creditsText(item) }}</span>
                  <span class="card-go">立即拍摄 ›</span>
                </div>
              </div>
            </div>
          </article>
        </div>
        <div v-else-if="searchLoading" class="product-grid">
          <div v-for="n in 10" :key="`ssk-${n}`" class="product-card skeleton-card">
            <div class="sk-cover shimmer"></div>
            <div class="card-overlay">
              <div class="sk-line shimmer" style="width: 68%"></div>
              <div class="sk-line sk-line-sm shimmer" style="width: 34%"></div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">
          <div class="empty-badge">
            <el-icon :size="26"><Files /></el-icon>
          </div>
          <p class="empty-title">没有找到匹配的工作流</p>
          <p class="empty-sub">换个关键词试试</p>
          <button class="btn-ghost" type="button" @click="clearSearch">清空搜索</button>
        </div>
        <div v-if="searchItems.length > 0" class="more-row">
          <button
            v-if="searchHasMore"
            class="more-btn"
            type="button"
            :disabled="searchLoading"
            @click="loadMoreSearch"
          >
            {{ searchLoading ? '加载中…' : '展示更多' }}
          </button>
        </div>
      </template>

      <!-- 分类模块 -->
      <template v-else>
        <div v-if="modulesLoading" class="product-grid">
          <div v-for="n in 10" :key="`msk-${n}`" class="product-card skeleton-card">
            <div class="sk-cover shimmer"></div>
            <div class="card-overlay">
              <div class="sk-line shimmer" style="width: 68%"></div>
              <div class="sk-line sk-line-sm shimmer" style="width: 34%"></div>
            </div>
          </div>
        </div>

        <template v-else>
          <section v-for="mod in modules" :key="mod.categoryId" class="module-section">
            <div class="module-head">
              <h2 class="module-title">{{ mod.name }}</h2>
              <span class="module-count">{{ mod.total }} 个项目</span>
            </div>

            <!-- 生成视频占位模块 -->
            <div v-if="mod.isPlaceholder" class="video-grid">
              <div v-for="n in 5" :key="`vph-${n}`" class="video-ph">
                <div class="video-ph-icon">▶</div>
                <div class="video-ph-text">视频项目占位 {{ n }}</div>
              </div>
            </div>

            <!-- 商品模块 -->
            <template v-else>
              <div class="product-grid">
                <article
                  v-for="item in displayedItems(mod)"
                  :key="item.id"
                  class="product-card"
                  @click="handleItemClick(item)"
                >
                  <div class="card-cover">
                    <img
                      v-if="item.imageUrl && !coverErrors[item.id]"
                      :src="cardImage(item)"
                      :alt="item.title"
                      loading="lazy"
                      @error="onCardImageError(item)"
                    />
                    <div v-else class="cover-fallback">{{ initialOf(item.title) }}</div>
                    <span class="card-cat">{{ item.categoryName || 'AI 工作流' }}</span>
                    <div class="card-overlay">
                      <h3 class="card-title" :title="item.title">{{ item.title }}</h3>
                      <div class="card-meta-row">
                        <span class="card-price">{{ creditsText(item) }}</span>
                        <span class="card-go">立即拍摄 ›</span>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
              <div v-if="mod.total > pageSizeOf(mod)" class="more-row">
                <button
                  class="more-btn"
                  type="button"
                  :disabled="mod.loading"
                  @click="toggleModule(mod)"
                >
                  {{ mod.expanded ? '收起' : (mod.loading ? '加载中…' : '展示更多') }}
                </button>
              </div>
            </template>
          </section>
        </template>
      </template>
    </section>

    <!-- 详情面板 -->
    <transition name="slide">
      <RightPanel
        v-if="selectedItem"
        :item="selectedItem"
        @close="selectedItem = null"
      />
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Search, Files } from '@element-plus/icons-vue';
import RightPanel from './components/RightPanel.vue';
import { comfyuiTaskApi } from '@/api/workflow-task/workflow-task';

interface WorkflowCard {
  id: number;
  imageUrl: string;
  title: string;
  categoryName: string;
  originalData: any;
}

interface CategoryFilter {
  categoryId: number;
  name: string;
  sortOrder?: number;
}

interface CategoryModule {
  categoryId: number;
  name: string;
  total: number;
  items: WorkflowCard[];
  loading: boolean;
  expanded: boolean;
  isPlaceholder: boolean;
}

interface SelectedWorkflow {
  workflowId: number;
  name: string;
  description: string;
  url: string;
  categoryName: string;
}

const DEFAULT_PAGE_SIZE = 10;
// 各模块首页展示数量：图片生成 30，精选项目 5，其余 10
const pageSizeOf = (mod?: { name?: string }) => {
  const name = mod?.name || '';
  // 精选只放 5 个，其它分类（图片/视频/以后新增的）统一放 30 个
  if (name.indexOf('精选') > -1) return 5;
  return 30;
};

const router = useRouter();

const selectedItem = ref<SelectedWorkflow | null>(null);
const listSection = ref<HTMLElement | null>(null);
const modules = ref<CategoryModule[]>([]);
const modulesLoading = ref(true);
const coverErrors = ref<Record<number, boolean>>({});

const searchParams = reactive({ prompt: '' });
const searchItems = ref<WorkflowCard[]>([]);
const searchLoading = ref(false);
const searchPage = ref(1);
const searchTotal = ref(0);

const isSearching = computed(() => searchParams.prompt.trim().length > 0);
const searchHasMore = computed(() => searchItems.value.length < searchTotal.value);

const previewItems = computed(() => {
  const featured = modules.value.find(m => !m.isPlaceholder);
  return featured ? featured.items.slice(0, 3) : [];
});

const initialOf = (title: string) => (title || 'X').trim().charAt(0).toUpperCase();

const markCoverError = (id: number) => {
  coverErrors.value = { ...coverErrors.value, [id]: true };
};

// 首页卡片优先用 480px 缩略图（THUMB/xxx.jpg），省带宽；缩略图缺失时自动回退原图
const thumbFellBack = ref<Record<number, boolean>>({});

const thumbUrl = (url: string): string => {
  if (!url) return url;
  const idx = url.indexOf('/files/');
  if (idx < 0) return url;
  const rest = url.slice(idx + '/files/'.length).split('?')[0];
  if (rest.startsWith('THUMB/')) return url;
  const name = rest.split('/').pop() || '';
  if (!name) return url;
  const jpg = name.replace(/\.[^.]+$/, '.jpg');
  return `${url.slice(0, idx)}/files/THUMB/${jpg}`;
};

const cardImage = (item: WorkflowCard): string => {
  if (!item?.imageUrl) return '';
  if (thumbFellBack.value[item.id]) return item.imageUrl;
  return thumbUrl(item.imageUrl);
};

const onCardImageError = (item: WorkflowCard) => {
  if (!thumbFellBack.value[item.id]) {
    thumbFellBack.value = { ...thumbFellBack.value, [item.id]: true };
    return;
  }
  markCoverError(item.id);
};

const creditsText = (item: WorkflowCard) => {
  const raw = item?.originalData?.creditsDeducted ?? item?.originalData?.credits;
  const n = Number(raw);
  if (raw !== null && raw !== undefined && Number.isFinite(n) && n >= 0) {
    return `消耗 ${n} 积分`;
  }
  return '积分见详情';
};

const mapItem = (raw: any): WorkflowCard => ({
  id: raw.workflowId,
  imageUrl: raw.url,
  title: raw.name,
  categoryName: raw.categoryName,
  originalData: raw,
});

const displayedItems = (mod: CategoryModule) =>
  mod.expanded ? mod.items : mod.items.slice(0, pageSizeOf(mod));

const fetchCategoryPage = async (categoryId: number, page: number) => {
  const mod = modules.value.find(m => m.categoryId === categoryId);
  const result = await comfyuiTaskApi.reqGetWorkflowResultModelflowsPage({
    page: page.toString(),
    size: pageSizeOf(mod),
    categoryId,
  });
  return result;
};

const buildModules = async (filters: CategoryFilter[]) => {
  modulesLoading.value = true;
  try {
    // sortOrder=1（精选项目）放最前，其余按接口（sortOrder 升序）顺序
    const sorted = [...filters].sort((a, b) => {
      if ((a.sortOrder ?? 99) === 1) return -1;
      if ((b.sortOrder ?? 99) === 1) return 1;
      return (a.sortOrder ?? 99) - (b.sortOrder ?? 99);
    });

    const built: CategoryModule[] = [];
    for (const cat of sorted) {
      try {
        const result = await fetchCategoryPage(cat.categoryId, 1);
        const items = result.items.map(mapItem);
        // 空分类不展示（视频模块暂时没商品→自动隐藏；以后加了视频商品会自动出现）
        if (items.length === 0) continue;
        built.push({
          categoryId: cat.categoryId,
          name: cat.name,
          total: result.total,
          items,
          loading: false,
          expanded: false,
          isPlaceholder: items.length === 0,
        });
      } catch (error) {
        console.error(`Failed to fetch category ${cat.name}:`, error);
      }
    }
    modules.value = built;
  } finally {
    modulesLoading.value = false;
  }
};

const fetchFilters = async () => {
  try {
    const result = await comfyuiTaskApi.reqGetWorkflowResultModelflowFilterList();
    await buildModules(result);
  } catch (error) {
    console.error('Failed to fetch filters:', error);
    modulesLoading.value = false;
  }
};

const toggleModule = async (mod: CategoryModule) => {
  if (mod.expanded) {
    mod.expanded = false;
    return;
  }
  if (mod.items.length >= mod.total) {
    mod.expanded = true;
    return;
  }
  mod.loading = true;
  try {
    const nextPage = Math.floor(mod.items.length / pageSizeOf(mod)) + 1;
    const result = await fetchCategoryPage(mod.categoryId, nextPage);
    const existing = new Set(mod.items.map(i => i.id));
    mod.items.push(...result.items.map(mapItem).filter(i => !existing.has(i.id)));
    mod.total = result.total;
    mod.expanded = true;
  } catch (error) {
    console.error('Failed to load more:', error);
  } finally {
    mod.loading = false;
  }
};

// ---------- 搜索 ----------
const fetchSearch = async (isNewSearch = false) => {
  const keyword = searchParams.prompt.trim();
  if (!keyword) return;
  if (isNewSearch) {
    searchPage.value = 1;
    searchTotal.value = 0;
    searchItems.value = [];
  }
  searchLoading.value = true;
  try {
    const result = await comfyuiTaskApi.reqGetWorkflowResultModelflowsPage({
      page: searchPage.value.toString(),
      size: DEFAULT_PAGE_SIZE,
      keyword,
    });
    searchTotal.value = result.total;
    const newItems = result.items.map(mapItem);
    if (isNewSearch) {
      searchItems.value = newItems;
    } else {
      const existing = new Set(searchItems.value.map(i => i.id));
      searchItems.value.push(...newItems.filter(i => !existing.has(i.id)));
    }
  } catch (error) {
    console.error('Failed to search workflows:', error);
  } finally {
    searchLoading.value = false;
  }
};

const loadMoreSearch = async () => {
  if (searchLoading.value || !searchHasMore.value) return;
  searchPage.value++;
  await fetchSearch(false);
};

let searchTimer: ReturnType<typeof setTimeout> | null = null;

const clearSearch = () => {
  searchParams.prompt = '';
};

const scrollToWorkflows = () => {
  listSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

const goWorks = () => {
  router.push('/works');
};

watch(() => searchParams.prompt, () => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    if (searchParams.prompt.trim()) {
      fetchSearch(true);
    }
  }, 320);
});

onMounted(() => {
  fetchFilters();
});

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer);
});

const handleItemClick = (item: WorkflowCard) => {
  selectedItem.value = {
    workflowId: item.originalData.workflowId,
    name: item.originalData.name,
    description: item.originalData.description,
    url: item.originalData.url,
    categoryName: item.originalData.categoryName,
  };
};
</script>

<style scoped>
.xy-home {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px 32px 64px;
  background: #111111;
  color: #ececf2;
  box-sizing: border-box;
}

/* ===== Hero ===== */
.hero {
  position: relative;
  border-radius: 24px;
  border: 1px solid #23232b;
  background:
    radial-gradient(120% 140% at 88% 0%, rgba(124, 92, 255, 0.28) 0%, rgba(124, 92, 255, 0) 55%),
    radial-gradient(90% 120% at 0% 100%, rgba(94, 234, 212, 0.12) 0%, rgba(94, 234, 212, 0) 60%),
    linear-gradient(135deg, #16161c 0%, #0e0e13 60%, #141420 100%);
  min-height: 250px;
  display: flex;
  align-items: center;
  margin-top: 56px;
}

.hero::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 26px 26px;
  pointer-events: none;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.9), transparent 75%);
  -webkit-mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.9), transparent 75%);
}

.hero-inner {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
  padding: 40px 44px;
}

.hero-copy {
  max-width: 640px;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid rgba(124, 92, 255, 0.35);
  background: rgba(124, 92, 255, 0.1);
  color: #b8a6ff;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.hero-eyebrow .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #5eead4;
  box-shadow: 0 0 8px rgba(94, 234, 212, 0.8);
}

.hero-title {
  margin: 18px 0 12px;
  font-size: 44px;
  line-height: 1.15;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: #f6f6f9;
}

.hero-title .hl {
  margin-left: 10px;
  background: linear-gradient(92deg, #a78bfa 0%, #7c5cff 55%, #5eead4 120%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-sub {
  margin: 0;
  font-size: 14.5px;
  line-height: 1.75;
  color: #a2a2b0;
  max-width: 560px;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 26px;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 24px;
  border: none;
  border-radius: 12px;
  background: #7c5cff;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 8px 24px rgba(124, 92, 255, 0.28);
}

.btn-primary:hover {
  background: #6a49f2;
  box-shadow: 0 10px 28px rgba(124, 92, 255, 0.42);
  transform: translateY(-1px);
}

.btn-arrow {
  font-size: 15px;
}

.btn-ghost {
  display: inline-flex;
  align-items: center;
  height: 44px;
  padding: 0 22px;
  border-radius: 12px;
  background: #1b1b21;
  border: 1px solid #2f2f39;
  color: #d7d7e0;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-ghost:hover {
  border-color: #7c5cff;
  color: #ffffff;
  background: #20202a;
}

/* Hero 预览叠卡 */
.hero-preview {
  flex-shrink: 0;
  width: 380px;
  height: 420px;
  margin-top: -110px;
  z-index: 2;
}

.preview-stack {
  position: relative;
  width: 100%;
  height: 100%;
}

.preview-card {
  position: absolute;
  width: 200px;
  height: 330px;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid #2c2c36;
  background: #191920;
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.55);
  transition: transform 0.35s ease;
}

.preview-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.preview-fallback,
.preview-card.pc-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2a2140 0%, #16161c 60%, #12312c 100%);
  color: #8f79ff;
  font-size: 28px;
  font-weight: 800;
}

.preview-empty-text {
  font-size: 13px;
  letter-spacing: 0.24em;
  color: #5eead4;
}

.preview-card.pc-1 {
  top: 16px;
  left: 8px;
  transform: rotate(-7deg);
  opacity: 0.72;
}

.preview-card.pc-2 {
  top: 58px;
  left: 78px;
  transform: rotate(2deg);
  opacity: 0.9;
}

.preview-card.pc-3 {
  top: 104px;
  left: 132px;
  transform: rotate(6deg);
  border-color: rgba(124, 92, 255, 0.5);
}

.hero-preview:hover .preview-card.pc-1 {
  transform: rotate(-9deg) translate(-6px, -6px);
}

.hero-preview:hover .preview-card.pc-3 {
  transform: rotate(8deg) translate(6px, 4px);
}

/* ===== 列表 ===== */
.list-section {
  margin-top: 34px;
  scroll-margin-top: 24px;
}

.list-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.list-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 800;
  color: #f2f2f7;
}

.list-sub {
  margin: 0;
  font-size: 13px;
  color: #83838f;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  width: 300px;
  padding: 0 14px;
  border-radius: 12px;
  background: #1b1b1f;
  border: 1px solid #2a2a30;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.search-box:focus-within {
  border-color: #7c5cff;
  box-shadow: 0 0 0 3px rgba(124, 92, 255, 0.16);
}

.search-icon {
  color: #6f6f7d;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: #ececf2;
  font-size: 13.5px;
  font-family: inherit;
}

.search-input::placeholder {
  color: #6a6a78;
}

.search-clear {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: #2b2b34;
  color: #9a9aa6;
  font-size: 11px;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.search-clear:hover {
  background: #3a3a46;
  color: #ffffff;
}

/* 搜索 */


/* 分类模块 */
.module-section {
  margin-top: 34px;
}

.module-section:first-of-type {
  margin-top: 10px;
}

.module-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.module-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: #f0f0f5;
}

.module-count {
  font-size: 12.5px;
  color: #8b8b98;
}

.more-row {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.more-btn {
  height: 38px;
  padding: 0 30px;
  border-radius: 999px;
  border: 1px solid #2f2f39;
  background: #1b1b21;
  color: #d7d7e0;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s ease;
}

.more-btn:hover:not(:disabled) {
  border-color: #7c5cff;
  color: #ffffff;
  background: #20202a;
}

.more-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

/* 生成视频占位（与图片模块同宽同列数） */
.video-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.video-ph {
  aspect-ratio: 9 / 16;
  border: 1px dashed #33333d;
  background: #15151a;
  border-radius: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #5a5a66;
}

.video-ph-icon {
  font-size: 26px;
  color: #7c5cff;
  opacity: 0.7;
}

.video-ph-text {
  font-size: 12px;
}

@media (max-width: 1279px) {
  .video-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 1023px) {
  .video-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 899px) {
  .video-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 商品网格 */
.product-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.product-card {
  display: flex;
  flex-direction: column;
  background: #1b1b1f;
  border: 1px solid #2a2a30;
  border-radius: 0;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.product-card:hover {
  transform: translateY(-2px);
  border-color: #7c5cff;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.45);
}

.card-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 9 / 16;
  background: #15151a;
  border-radius: 0;
  overflow: hidden;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.4s ease;
}

.product-card:hover .card-cover img {
  transform: scale(1.04);
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2a2140 0%, #191920 55%, #12312c 100%);
  color: #a78bfa;
  font-size: 34px;
  font-weight: 800;
}

.card-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 26px 10px 8px;
  background: linear-gradient(180deg, rgba(0,0,0,0) 0%, rgba(0,0,0,.45) 35%, rgba(0,0,0,.82) 100%);
  pointer-events: none;
}
.card-overlay .card-title,
.card-overlay .card-meta-row,
.card-overlay .card-price,
.card-overlay .card-go {
  pointer-events: auto;
}

.card-title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 700;
  color: #f0f0f5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.card-cat {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  background: rgba(0,0,0,.55);
  border: 1px solid rgba(255,255,255,.18);
  backdrop-filter: blur(4px);
  color: #e6e6ef;
  font-size: 11px;
  line-height: 1.6;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 2px;
}

.card-price {
  font-size: 12px;
  color: #c9c9d6;
  white-space: nowrap;
}

.card-go {
  font-size: 12.5px;
  font-weight: 700;
  color: #7c5cff;
  white-space: nowrap;
  transition: color 0.18s ease;
}

.product-card:hover .card-go {
  color: #a78bfa;
}

/* 骨架 */
.skeleton-card {
  cursor: default;
  pointer-events: none;
}

.skeleton-card:hover {
  transform: none;
  border-color: #2a2a30;
  box-shadow: none;
}

.sk-cover {
  width: 100%;
  aspect-ratio: 9 / 16;
}

.sk-line {
  height: 12px;
  border-radius: 6px;
  margin-bottom: 10px;
}

.sk-line-sm {
  height: 10px;
  margin-bottom: 0;
}

.shimmer {
  background: linear-gradient(90deg, #202027 25%, #2a2a33 50%, #202027 75%);
  background-size: 200% 100%;
  animation: xy-shimmer 1.4s infinite linear;
}

@keyframes xy-shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 72px 20px;
  border-radius: 16px;
  border: 1px dashed #2a2a30;
  background: #15151a;
}

.empty-badge {
  width: 54px;
  height: 54px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(124, 92, 255, 0.12);
  border: 1px solid rgba(124, 92, 255, 0.3);
  color: #a78bfa;
}

.empty-title {
  margin: 6px 0 0;
  font-size: 15px;
  font-weight: 700;
  color: #e6e6ee;
}

.empty-sub {
  margin: 0;
  font-size: 13px;
  color: #7f7f8c;
}

/* 详情滑入 */
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

.slide-enter-to,
.slide-leave-from {
  transform: translateX(0);
  opacity: 1;
}

/* 响应式 */
@media (max-width: 1279px) {
  .product-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 16px;
  }
  .hero-title {
    font-size: 38px;
  }
  .hero-preview {
    width: 330px;
    height: 370px;
    transform: scale(0.9);
  }
}

@media (max-width: 1023px) {
  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
  }
}

@media (max-width: 899px) {
  .xy-home {
    padding: 16px 16px 48px;
  }
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    justify-items: stretch;
  }
  .product-card {
    width: 100%;
    max-width: none;
  }
  .hero-inner {
    flex-direction: column;
    align-items: flex-start;
    padding: 28px 22px;
    gap: 26px;
  }
  .hero-title {
    font-size: 30px;
  }
  .hero-preview {
    width: 100%;
    max-width: 320px;
    height: 220px;
    transform: none;
  }
  .preview-card {
    width: 210px;
    height: 132px;
  }
  .preview-card.pc-1 {
    left: 0;
  }
  .preview-card.pc-2 {
    left: 56px;
  }
  .preview-card.pc-3 {
    left: 104px;
  }
  .search-box {
    width: 100%;
  }
  .list-head {
    align-items: stretch;
  }
}
</style>
