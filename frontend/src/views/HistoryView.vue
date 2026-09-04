<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getTask, getTaskTrace, listTasks } from '@/api/tasks'
import type { AgentTraceRecord, TaskListItem, TaskState } from '@/types/task'

type HistoryStatus = 'Success' | 'Human-in-loop' | 'Running' | 'Failed'

interface HistoryRow {
  taskId: number
  id: string
  query: string
  status: HistoryStatus
  time: string
}

const router = useRouter()
const loading = ref(false)
const tableData = ref<HistoryRow[]>([])

function formatTime(timestamp?: number): string {
  if (typeof timestamp !== 'number' || !Number.isFinite(timestamp) || timestamp <= 0) {
    return '尚未开始'
  }

  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return '尚未开始'
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function taskStatus(state: TaskState | null, trace: AgentTraceRecord[]): HistoryStatus {
  if (state?.finalReport) return 'Success'
  if (trace.some((record) => record.status === 'FAILED')) return 'Failed'
  if (state?.hypothesis && !state.evaluation && !state.humanFeedback) return 'Human-in-loop'
  return 'Running'
}

async function buildHistoryRow(item: TaskListItem): Promise<{ row: HistoryRow; partial: boolean }> {
  const [stateResult, traceResult] = await Promise.allSettled([
    getTask(item.taskId),
    getTaskTrace(item.taskId),
  ])
  const state = stateResult.status === 'fulfilled' ? stateResult.value : null
  const trace = traceResult.status === 'fulfilled' ? traceResult.value : []
  const startTime = trace
    .map((record) => record.startTimeMillis)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value) && value > 0)
    .sort((left, right) => left - right)[0]

  return {
    row: {
      taskId: item.taskId,
      id: `TASK-${item.taskId}`,
      query: state?.question?.trim() || item.question?.trim() || '正在初始化科研问题',
      status: taskStatus(state, trace),
      time: formatTime(startTime),
    },
    partial: stateResult.status === 'rejected' || traceResult.status === 'rejected',
  }
}

async function loadTasks() {
  if (loading.value) return
  loading.value = true
  try {
    const tasks = (await listTasks()).sort((left, right) => right.taskId - left.taskId)
    const results = await Promise.all(tasks.map(buildHistoryRow))
    tableData.value = results.map(({ row }) => row)
    const partialCount = results.filter(({ partial }) => partial).length
    if (partialCount > 0) {
      ElMessage.warning(`${partialCount} 个任务的部分状态暂时无法读取，可稍后刷新重试`)
    }
  } catch (error) {
    tableData.value = []
    ElMessage.error(error instanceof Error ? error.message : '读取历史任务失败')
  } finally {
    loading.value = false
  }
}

function openTask(row: HistoryRow) {
  const path = row.status === 'Success' ? '/result' : '/pipeline'
  void router.push({ path, query: { taskId: String(row.taskId) } })
}

function actionText(status: HistoryStatus): string {
  if (status === 'Success') return '查看详细报告'
  if (status === 'Human-in-loop') return '继续人工审阅'
  if (status === 'Failed') return '查看异常详情'
  return '查看运行进度'
}

onMounted(() => void loadTasks())
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <div style="font-weight: bold; font-size: 18px;">历史科研任务记录看板</div>
        <el-button :icon="Refresh" :loading="loading" @click="loadTasks">刷新</el-button>
      </div>
    </template>

    <div v-loading="loading" class="table-shell">
      <el-table
        v-if="tableData.length > 0"
        :data="tableData"
        stripe
        border
        style="width: 100%"
        row-class-name="task-row"
        @row-click="openTask"
      >
        <el-table-column prop="id" label="任务编号" width="180" />
        <el-table-column prop="query" label="初始科研问题 (研究方向)" show-overflow-tooltip />
        <el-table-column prop="time" label="发起时间" width="180" />
        <el-table-column label="当前状态" width="150">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 'Success'" type="success">假设生成完毕</el-tag>
            <el-tag v-else-if="scope.row.status === 'Human-in-loop'" type="warning">等待人类介入</el-tag>
            <el-tag v-else-if="scope.row.status === 'Running'" type="primary">管线运行中</el-tag>
            <el-tag v-else type="danger">管线异常终止</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button size="small" type="primary" plain @click.stop="openTask(scope.row)">
              {{ actionText(scope.row.status) }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else-if="!loading" description="暂无历史科研任务">
        <el-button type="primary" plain @click="loadTasks">重新加载</el-button>
      </el-empty>
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.table-shell {
  min-height: 240px;
}

:deep(.task-row) {
  cursor: pointer;
}
</style>
