import { ApiError, requestJson } from './http'
import type {
  AgentTraceRecord,
  CreateTaskResponse,
  HumanFeedback,
  InterveneResponse,
  TaskId,
  TaskListItem,
  TaskReportResponse,
  TaskState,
} from '../types/task'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function requireTaskId(taskId: TaskId): void {
  if (!Number.isSafeInteger(taskId) || taskId <= 0) {
    throw new ApiError('任务编号无效，请重新创建或选择任务。')
  }
}

function requireRecord(value: unknown, message: string): Record<string, unknown> {
  if (!isRecord(value)) throw new ApiError(message, 502, value)
  return value
}

export async function createTask(question: string): Promise<CreateTaskResponse> {
  const normalizedQuestion = question.trim()
  if (!normalizedQuestion) throw new ApiError('请输入需要研究的科学问题。')

  const body = await requestJson<unknown>('/tasks', {
    method: 'POST',
    body: JSON.stringify({ question: normalizedQuestion }),
  })
  const record = requireRecord(body, '后端创建任务的响应格式不正确。')
  const taskId = record.taskId
  if (typeof taskId !== 'number' || !Number.isSafeInteger(taskId) || taskId <= 0) {
    throw new ApiError('后端未返回有效的任务编号。', 502, body)
  }
  return { taskId }
}

export async function listTasks(): Promise<TaskListItem[]> {
  const body = await requestJson<unknown>('/tasks')
  if (!Array.isArray(body)) throw new ApiError('后端任务列表响应格式不正确。', 502, body)

  return body.map((item) => {
    const record = requireRecord(item, '后端任务列表中包含格式错误的数据。')
    const { taskId, runId, question, done } = record
    if (
      typeof taskId !== 'number'
      || !Number.isSafeInteger(taskId)
      || taskId <= 0
      || typeof runId !== 'string'
      || !runId.trim()
    ) {
      throw new ApiError('后端任务列表中缺少 taskId 或 runId。', 502, item)
    }

    return {
      taskId,
      runId,
      ...(typeof question === 'string' ? { question } : {}),
      ...(typeof done === 'boolean' ? { done } : {}),
    }
  })
}

export async function getTask(taskId: TaskId): Promise<TaskState> {
  requireTaskId(taskId)
  const body = await requestJson<unknown>(`/tasks/${taskId}`)
  const record = requireRecord(body, '后端任务详情响应格式不正确。')
  if (typeof record.question !== 'string') {
    throw new ApiError('后端任务详情中缺少科学问题。', 502, body)
  }
  return record as unknown as TaskState
}

export async function getTaskTrace(taskId: TaskId): Promise<AgentTraceRecord[]> {
  requireTaskId(taskId)
  const body = await requestJson<unknown>(`/tasks/${taskId}/trace`)
  if (!Array.isArray(body)) throw new ApiError('后端执行追踪响应格式不正确。', 502, body)

  for (const item of body) {
    const record = requireRecord(item, '后端执行追踪中包含格式错误的数据。')
    if (
      typeof record.stage !== 'string'
      || typeof record.agent !== 'string'
      || typeof record.status !== 'string'
      || typeof record.durationMillis !== 'number'
    ) {
      throw new ApiError('后端执行追踪缺少必要字段。', 502, item)
    }
  }
  return body as AgentTraceRecord[]
}

export async function getTaskReport(taskId: TaskId): Promise<TaskReportResponse> {
  requireTaskId(taskId)
  const body = await requestJson<unknown>(`/tasks/${taskId}/report`)
  const record = requireRecord(body, '后端研究报告响应格式不正确。')
  if (!Object.prototype.hasOwnProperty.call(record, 'report') || (record.report !== null && !isRecord(record.report))) {
    throw new ApiError('后端研究报告响应中缺少 report 字段。', 502, body)
  }
  return { report: record.report as TaskReportResponse['report'] }
}

export async function interveneTask(
  taskId: TaskId,
  feedback: HumanFeedback = {},
): Promise<InterveneResponse> {
  requireTaskId(taskId)
  // AI 服务当前在 resume 事件中要求 comment 非 null；空意见表示直接通过审阅。
  const reviewComment = feedback.reviewComment?.trim() || '通过'
  const body = await requestJson<unknown>(`/tasks/${taskId}/intervene`, {
    method: 'POST',
    body: JSON.stringify({
      reviewComment,
      revisedHypotheses: feedback.revisedHypotheses ?? [],
    }),
  })
  const record = requireRecord(body, '后端人工审阅响应格式不正确。')
  if (typeof record.status !== 'string' || typeof record.runId !== 'string') {
    throw new ApiError('后端人工审阅响应缺少 status 或 runId。', 502, body)
  }
  return { status: record.status, runId: record.runId }
}
