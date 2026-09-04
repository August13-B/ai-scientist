<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getKnowledgeStats,
  searchKnowledge,
  type KnowledgeBaseName,
  type KnowledgeSearchResult,
  type KnowledgeStats,
} from '@/api/knowledge'

const libraryDefinitions: Array<{
  name: KnowledgeBaseName
  label: string
  shortLabel: string
  description: string
}> = [
  { name: 'papers', label: '论文库', shortLabel: '论文', description: '论文全文、摘要与研究结论，为文献检索和知识发现提供依据。' },
  { name: 'methods', label: '方法库', shortLabel: '方法', description: '机器学习、深度学习及实验范式，为假设和实验设计提供方法支撑。' },
  { name: 'datasets', label: '数据集库', shortLabel: '数据集', description: '公开数据集及数据特征信息；实验设计仍只采用经过核验的精选数据源。' },
  { name: 'evidence', label: '证据库', shortLabel: '证据', description: '论文事实、实验结果和证据片段，用于推理链支撑与引用核验。' },
]

const activeName = ref<KnowledgeBaseName>('papers')
const stats = ref<KnowledgeStats | null>(null)
const query = ref('SSD 故障预测 SMART 时间序列 NAND 闪存磨损')
const results = ref<KnowledgeSearchResult[]>([])
const loadingStats = ref(false)
const searching = ref(false)

const activeDefinition = computed(() =>
  libraryDefinitions.find(item => item.name === activeName.value) ?? libraryDefinitions[0],
)
const activeStats = computed(() => stats.value?.libraries[activeName.value])

async function loadStats() {
  loadingStats.value = true
  try {
    stats.value = await getKnowledgeStats()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库状态读取失败')
  } finally {
    loadingStats.value = false
  }
}

async function runSearch() {
  const text = query.value.trim()
  if (!text || searching.value) return
  searching.value = true
  results.value = []
  try {
    results.value = await searchKnowledge(activeName.value, text, 6)
    if (results.value.length === 0) ElMessage.warning('没有检索到相关内容')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '向量检索失败')
  } finally {
    searching.value = false
  }
}

function switchLibrary(name: KnowledgeBaseName) {
  activeName.value = name
  results.value = []
}

function sourceType(sourceId: string) {
  if (sourceId.startsWith('url:localdoc://')) return '上传原文'
  if (sourceId.startsWith('doi:')) return 'DOI'
  if (sourceId.startsWith('pmid:')) return 'PMID'
  return '公开网页'
}

function sourceTagType(sourceId: string): 'primary' | 'success' | 'info' {
  if (sourceId.startsWith('url:localdoc://')) return 'primary'
  if (sourceId.startsWith('doi:') || sourceId.startsWith('pmid:')) return 'success'
  return 'info'
}

onMounted(loadStats)
</script>

<template>
  <div class="knowledge-page" v-loading="loadingStats">
    <el-card shadow="never" class="overview-card">
      <div class="overview-header">
        <div>
          <div class="title-row">
            <h2>RAG 知识库引擎</h2>
            <el-tag v-if="stats" type="success" effect="plain">生产数据已连接</el-tag>
          </div>
          <p>四库向量统一接入智能体管线，检索结果保留公开来源或原文页码级溯源。</p>
        </div>
        <el-button plain :loading="loadingStats" @click="loadStats">刷新状态</el-button>
      </div>

      <div class="metrics" v-if="stats">
        <div class="metric-item">
          <span>向量总数</span>
          <strong>{{ stats.total.toLocaleString() }}</strong>
        </div>
        <div class="metric-item">
          <span>向量数据库</span>
          <strong>{{ stats.vectorDatabase }}</strong>
        </div>
        <div class="metric-item">
          <span>Embedding 模型</span>
          <strong class="model-name">{{ stats.embeddingModel }}</strong>
        </div>
        <div class="metric-item">
          <span>向量维度</span>
          <strong>{{ stats.dimensions }}</strong>
        </div>
      </div>
    </el-card>

    <div class="library-grid">
      <button
        v-for="item in libraryDefinitions"
        :key="item.name"
        class="library-card"
        :class="{ active: activeName === item.name }"
        type="button"
        @click="switchLibrary(item.name)"
      >
        <span class="library-name">{{ item.label }}</span>
        <strong>{{ stats?.libraries[item.name]?.total.toLocaleString() ?? '—' }}</strong>
        <span class="library-detail">
          精选 {{ stats?.libraries[item.name]?.curated ?? '—' }} · 原文向量 {{ stats?.libraries[item.name]?.uploaded.toLocaleString() ?? '—' }}
        </span>
      </button>
    </div>

    <el-card shadow="never" class="search-card">
      <template #header>
        <div class="search-header">
          <div>
            <strong>{{ activeDefinition.label }}语义检索</strong>
            <span>{{ activeDefinition.description }}</span>
          </div>
          <el-tag type="info" effect="plain">当前共 {{ activeStats?.total.toLocaleString() ?? '—' }} 条</el-tag>
        </div>
      </template>

      <div class="search-bar">
        <el-input
          v-model="query"
          clearable
          :placeholder="`输入要在${activeDefinition.label}中检索的科研问题或关键词`"
          @keyup.enter="runSearch"
        />
        <el-button type="primary" :loading="searching" :disabled="!query.trim()" @click="runSearch">
          混合向量检索
        </el-button>
      </div>

      <el-alert
        title="每次检索同时召回带 DOI/PMID/公开 URL 的精选条目与上传论文原文分块；数据集库进入实验设计时仍使用已核验白名单。"
        type="info"
        :closable="false"
        show-icon
      />

      <div v-if="results.length" class="result-list">
        <article v-for="(item, index) in results" :key="`${item.sourceId}-${index}`" class="result-item">
          <div class="result-heading">
            <span class="result-index">{{ index + 1 }}</span>
            <div>
              <h3>{{ item.title }}</h3>
              <div class="result-meta">
                <el-tag size="small" :type="sourceTagType(item.sourceId)">{{ sourceType(item.sourceId) }}</el-tag>
                <span v-if="item.year">{{ item.year }}</span>
                <span v-if="item.authors?.length">{{ item.authors.slice(0, 3).join('、') }}</span>
              </div>
            </div>
          </div>
          <p>{{ item.content }}</p>
          <code>{{ item.sourceId }}</code>
        </article>
      </div>
      <el-empty v-else :description="searching ? '正在调用百炼生成查询向量…' : `输入问题，检索${activeDefinition.shortLabel}库的真实向量数据`" />
    </el-card>
  </div>
</template>

<style scoped>
.knowledge-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.overview-card,
.search-card {
  border-radius: 10px;
}
.overview-header,
.search-header,
.title-row,
.search-bar,
.result-heading,
.result-meta {
  display: flex;
  align-items: center;
}
.overview-header,
.search-header {
  justify-content: space-between;
  gap: 24px;
}
.title-row {
  gap: 12px;
}
h2 {
  margin: 0;
  color: #303133;
  font-size: 21px;
}
.overview-header p,
.search-header span {
  margin: 7px 0 0;
  color: #909399;
  font-size: 13px;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 22px;
  border-top: 1px solid #ebeef5;
  padding-top: 18px;
}
.metric-item {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 0 20px;
  border-right: 1px solid #ebeef5;
}
.metric-item:first-child { padding-left: 0; }
.metric-item:last-child { border-right: 0; }
.metric-item span { color: #909399; font-size: 13px; }
.metric-item strong { color: #303133; font-size: 24px; }
.metric-item .model-name { font-size: 17px; }
.library-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.library-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 18px;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: all .2s ease;
}
.library-card:hover,
.library-card.active {
  border-color: #409eff;
  box-shadow: 0 4px 14px rgba(64, 158, 255, .12);
}
.library-card.active { background: #ecf5ff; }
.library-name { color: #606266; font-size: 14px; }
.library-card strong { color: #303133; font-size: 28px; line-height: 1; }
.library-detail { color: #909399; font-size: 12px; }
.search-header > div {
  display: flex;
  flex-direction: column;
}
.search-bar {
  gap: 12px;
  margin-bottom: 14px;
}
.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}
.result-item {
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}
.result-heading { align-items: flex-start; gap: 11px; }
.result-index {
  flex: 0 0 24px;
  height: 24px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  line-height: 24px;
  text-align: center;
}
.result-item h3 { margin: 1px 0 7px; color: #303133; font-size: 15px; }
.result-meta { gap: 10px; color: #909399; font-size: 12px; }
.result-item p {
  margin: 12px 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.result-item code {
  display: block;
  overflow: hidden;
  color: #409eff;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 1000px) {
  .metrics,
  .library-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .search-bar { align-items: stretch; flex-direction: column; }
}
</style>
