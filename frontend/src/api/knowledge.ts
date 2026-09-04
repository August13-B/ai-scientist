import { requestJson } from './http'

export type KnowledgeBaseName = 'papers' | 'methods' | 'datasets' | 'evidence'

export interface LibraryStats {
  curated: number
  uploaded: number
  total: number
}

export interface KnowledgeStats {
  status: string
  mode: string
  vectorDatabase: string
  embeddingModel: string
  dimensions: number
  total: number
  libraries: Record<KnowledgeBaseName, LibraryStats>
}

export interface KnowledgeSearchResult {
  title: string
  content: string
  authors: string[]
  year: number | null
  doi: string | null
  pmid: string | null
  url: string | null
  sourceId: string
}

export function getKnowledgeStats(): Promise<KnowledgeStats> {
  return requestJson<KnowledgeStats>('/knowledge/stats')
}

export function searchKnowledge(
  knowledgeBase: KnowledgeBaseName,
  query: string,
  topK = 6,
): Promise<KnowledgeSearchResult[]> {
  return requestJson<KnowledgeSearchResult[]>('/knowledge/search', {
    method: 'POST',
    body: JSON.stringify({ knowledgeBase, query, topK }),
  })
}
