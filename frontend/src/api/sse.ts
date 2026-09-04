import { ApiError, createSseUrl } from './http'
import type { TaskEventType, TaskId, TaskStreamEvent } from '../types/task'

export interface TaskStreamHandlers {
  onOpen?: () => void
  onEvent?: (event: TaskStreamEvent) => void
  onError?: (error: ApiError) => void
}

export interface TaskStreamSubscription {
  source: EventSource
  close: () => void
}

const NAMED_EVENTS: Exclude<TaskEventType, 'message'>[] = [
  'agent.start',
  'agent.thinking',
  'agent.result',
  'pipeline.pause',
  'pipeline.resume',
  'pipeline.done',
  'pipeline.error',
]

function parseEventData(rawData: string): unknown {
  if (!rawData) return null
  try {
    return JSON.parse(rawData) as unknown
  } catch {
    return rawData
  }
}

function toTaskStreamEvent(type: TaskEventType, event: MessageEvent<string>): TaskStreamEvent {
  return {
    type,
    data: parseEventData(event.data),
    rawData: event.data,
    lastEventId: event.lastEventId,
  }
}

/**
 * 订阅后端转发的任务事件。EventSource 会自动重连；组件卸载时必须调用 close()。
 */
export function subscribeTaskEvents(
  taskId: TaskId,
  handlers: TaskStreamHandlers = {},
): TaskStreamSubscription {
  if (!Number.isSafeInteger(taskId) || taskId <= 0) {
    throw new ApiError('任务编号无效，无法建立实时连接。')
  }

  const source = new EventSource(createSseUrl(taskId))
  source.onopen = () => handlers.onOpen?.()
  source.onerror = () => {
    handlers.onError?.(new ApiError('任务实时连接中断，系统正在尝试重新连接。'))
  }
  source.onmessage = (event: MessageEvent<string>) => {
    handlers.onEvent?.(toTaskStreamEvent('message', event))
  }

  for (const eventType of NAMED_EVENTS) {
    source.addEventListener(eventType, (event) => {
      handlers.onEvent?.(toTaskStreamEvent(eventType, event as MessageEvent<string>))
    })
  }

  return {
    source,
    close: () => source.close(),
  }
}
