const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

/**
 * 开发环境默认走 Vite 的 /api 代理；部署时可用 VITE_API_BASE_URL 覆盖。
 */
export const API_BASE_URL = (configuredBaseUrl || '/api').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number
  readonly details: unknown

  constructor(message: string, status = 0, details?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

function buildUrl(path: string): string {
  return `${API_BASE_URL}/${path.replace(/^\//, '')}`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function extractErrorMessage(body: unknown, fallback: string): string {
  if (typeof body === 'string' && body.trim()) return body.trim()
  if (!isRecord(body)) return fallback

  for (const key of ['message', 'detail', 'error', 'title'] as const) {
    const value = body[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return fallback
}

async function readResponseBody(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) return null

  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

export async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')

  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json; charset=UTF-8')
  }

  let response: Response
  try {
    response = await fetch(buildUrl(path), { ...init, headers })
  } catch (error) {
    throw new ApiError(
      '无法连接后端服务，请确认后端已在 8080 端口启动。',
      0,
      error,
    )
  }

  const body = await readResponseBody(response)
  if (!response.ok) {
    const fallback = `请求失败（HTTP ${response.status}${response.statusText ? ` ${response.statusText}` : ''}）`
    throw new ApiError(extractErrorMessage(body, fallback), response.status, body)
  }

  return body as T
}

export function createSseUrl(taskId: number): string {
  return buildUrl(`/tasks/${encodeURIComponent(String(taskId))}/stream`)
}
