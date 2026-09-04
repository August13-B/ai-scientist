const TASK_ID_KEY = 'ai-scientist.current-task-id'
const QUESTION_KEY = 'ai-scientist.current-question'

export function rememberCurrentTask(taskId: number, question?: string) {
  sessionStorage.setItem(TASK_ID_KEY, String(taskId))
  if (question?.trim()) {
    sessionStorage.setItem(QUESTION_KEY, question.trim())
  }
}

export function getCurrentTaskId(): number | null {
  const value = sessionStorage.getItem(TASK_ID_KEY)
  if (!value) return null

  const taskId = Number(value)
  return Number.isSafeInteger(taskId) && taskId > 0 ? taskId : null
}

export function getCurrentQuestion(): string {
  return sessionStorage.getItem(QUESTION_KEY) ?? ''
}
