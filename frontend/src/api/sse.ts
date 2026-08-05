// SSE 流式封装（占位）
// TODO: 建立与后端 /api/tasks/{id}/stream 的 EventSource 连接，
//       处理 agent.start / agent.thinking / agent.result /
//       pipeline.pause / pipeline.resume / pipeline.done / pipeline.error 事件

export interface SseEvent {
  type: string
  data: unknown
}

export function connectTaskStream(taskId: string, onEvent: (e: SseEvent) => void) {
  const es = new EventSource(`/api/tasks/${taskId}/stream`)
  es.onmessage = (msg) => {
    try {
      onEvent(JSON.parse(msg.data) as SseEvent)
    } catch {
      // 忽略非 JSON 事件
    }
  }
  return es
}
