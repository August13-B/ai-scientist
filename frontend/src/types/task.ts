export type TaskId = number

export type TaskStage =
  | 'UNDERSTANDING'
  | 'LITERATURE'
  | 'KNOWLEDGE'
  | 'HYPOTHESIS'
  | 'EVALUATION'
  | 'EXPERIMENT'
  | 'DEBATE'
  | 'REPORT'

export interface QuestionQuery {
  originalQuestion: string
  domain: string | null
  subQueries: string[]
  keyConcepts: string[]
  knownConditions: string[]
  targetVariables: string[]
}

export interface PaperEvidence {
  title: string
  content: string
  authors: string[]
  year: number | null
  doi: string | null
  pmid: string | null
  url: string | null
  sourceId: string
}

export interface KeyFinding {
  finding: string
  evidenceIds: string[]
}

export interface CitationChain {
  chain: string
  evidenceIds: string[]
}

export interface LiteratureResult {
  papers: PaperEvidence[]
  keyFindings: KeyFinding[]
  citationChains: CitationChain[]
}

export interface ResearchGap {
  gap: string
  evidenceIds: string[]
  confidence: number
  rankingReason: string
}

export interface KnowledgeDiscoveryResult {
  knownFindings: string[]
  limitations: string[]
  conflicts: string[]
  transferOpportunities: string[]
  researchGaps: ResearchGap[]
  selectedProblem: string
  paperTitle: string
  paperAbstract: string
  references: string[]
}

export interface Hypothesis {
  summary: string
  rationale: string
  technicalDetails: string[]
  methods: string[]
  reasoningChain: string[]
  evidenceIds: string[]
}

export interface HypothesisResult {
  hypotheses: Hypothesis[]
}

export interface ScoredHypothesis {
  summary: string
  innovation: number
  feasibility: number
  citationReliability: number
  dataAvailability: number
  overall: number
}

export interface HallucinationCheck {
  citation: string
  verified: boolean
  note: string | null
}

export interface EvaluationResult {
  rankings: ScoredHypothesis[]
  hallucinationReport: HallucinationCheck[]
  references: string[]
}

export interface ExperimentResult {
  baselines: string[]
  metrics: string[]
  datasets: string[]
  expectedResults: string | null
}

export interface DebateResult {
  debateLog: string[]
  refinedComments: string | null
}

export interface DatasetPlan {
  source: string[]
  target: string[]
}

export interface ExperimentPlan {
  baselines: string[]
  metrics: string[]
}

export interface ResearchPlan {
  problemStatement: string
  rationale: string | null
  technicalDetails: string[]
  datasets: DatasetPlan | null
  paperTitle: string | null
  paperAbstract: string | null
  methods: string[]
  experiments: ExperimentPlan | null
  results: string | null
  references: string[]
}

export interface HumanFeedback {
  reviewComment?: string
  revisedHypotheses?: Hypothesis[]
}

export interface AppliedHumanFeedback {
  reviewComment: string | null
  revisedHypotheses: Hypothesis[]
}

export interface TaskState {
  question: string
  questionQuery: QuestionQuery | null
  literature: LiteratureResult | null
  knowledgeDiscovery: KnowledgeDiscoveryResult | null
  hypothesis: HypothesisResult | null
  evaluation: EvaluationResult | null
  experiment: ExperimentResult | null
  debate: DebateResult | null
  humanFeedback: AppliedHumanFeedback | null
  finalReport: ResearchPlan | null
}

export interface TaskListItem {
  taskId: TaskId
  runId: string
  /** 网关当前未返回这两个字段，保留可选字段便于后端扩展。 */
  question?: string
  done?: boolean
}

export interface CreateTaskResponse {
  taskId: TaskId
}

export interface TaskReportResponse {
  report: ResearchPlan | null
}

export type TraceStatus = 'SUCCESS' | 'FAILED'

export interface AgentTraceRecord {
  stage: TaskStage
  agent: string
  startTimeMillis: number
  durationMillis: number
  status: TraceStatus
  errorMessage: string | null
  input: Record<string, unknown>
  output: unknown
}

export interface InterveneResponse {
  status: string
  runId: string
}

export type TaskEventType =
  | 'agent.start'
  | 'agent.thinking'
  | 'agent.result'
  | 'pipeline.pause'
  | 'pipeline.resume'
  | 'pipeline.done'
  | 'pipeline.error'
  | 'message'

export interface TaskStreamEvent {
  type: TaskEventType
  data: unknown
  rawData: string
  lastEventId: string
}
