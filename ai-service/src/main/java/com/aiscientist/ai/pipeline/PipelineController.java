package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.rag.RagSearchService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 管线 HTTP 接口（ai-service 8081，供 backend 调用/转发）。
 *
 * <p>对应 docs/api-design.md 内部接口清单：</p>
 * <ul>
 *   <li>{@code POST /pipeline/run} 启动管线，立即返回 runId（异步执行）</li>
 *   <li>{@code GET /pipeline/{runId}/stream} SSE 事件流（backend 转发给前端）</li>
 *   <li>{@code POST /pipeline/{runId}/resume} 人在回路恢复（提交审阅意见）</li>
 *   <li>{@code GET /pipeline/{runId}/state} 查询当前管线状态</li>
 *   <li>{@code POST /rag/search} 四库混合检索（Agent 内部 / 直连接口）</li>
 * </ul>
 */
@RestController
@RequestMapping("/pipeline")
public class PipelineController {

    private final PipelineEngine engine;
    private final RagSearchService ragSearchService;

    public PipelineController(PipelineEngine engine, RagSearchService ragSearchService) {
        this.engine = engine;
        this.ragSearchService = ragSearchService;
    }

    /** 启动管线请求体 */
    public record RunRequest(String question) {
    }

    /** 启动管线响应 */
    public record RunResponse(String runId) {
    }

    /** 启动管线：立即返回 runId，后台异步执行 */
    @PostMapping("/run")
    public RunResponse run(@RequestBody RunRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        return new RunResponse(engine.start(request.question()));
    }

    /** SSE 事件流：订阅 runId 的 Agent 状态事件（含历史重放） */
    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        engine.registerStream(runId, emitter);
        return emitter;
    }

    /** 人在回路恢复：提交人类审阅意见/修改后的候选假设 */
    @PostMapping("/{runId}/resume")
    public Map<String, String> resume(
            @PathVariable String runId,
            @RequestBody(required = false) PipelineModels.HumanFeedback feedback
    ) {
        engine.resume(runId, feedback == null ? new PipelineModels.HumanFeedback(null, List.of()) : feedback);
        return Map.of("status", "resumed", "runId", runId);
    }

    /** 查询管线当前状态（各阶段产物快照） */
    @GetMapping("/{runId}/state")
    public PipelineContext state(@PathVariable String runId) {
        return engine.state(runId);
    }

    /** 全部已启动的 run（进行中/暂停/已完成），调试列表用 */
    @GetMapping("/runs")
    public List<PipelineEngine.RunInfo> runs() {
        return engine.runs();
    }

    /** Agent 级执行追踪 JSON（input/output/耗时/状态，前端可消费） */
    @GetMapping("/{runId}/trace")
    public List<AgentTraceRecord> trace(@PathVariable String runId) {
        return engine.trace(runId);
    }

    /** 调试首页：输入 runId 跳转 + 历史 run 列表 */
    @GetMapping(value = "/debug", produces = MediaType.TEXT_HTML_VALUE)
    public String debugHome() {
        return DEBUG_INDEX_TEMPLATE;
    }

    /** 内嵌 HTML 调试页：可视化每个 Agent 的输入输出（增量追加，保留展开状态） */
    @GetMapping(value = "/{runId}/debug", produces = MediaType.TEXT_HTML_VALUE)
    public String debugPage(@PathVariable String runId) {
        return DEBUG_PAGE_TEMPLATE.replace("__RUN_ID__", runId);
    }

    /** 调试入口首页模板：runId 输入框 + run 列表（点击/回车跳转） */
    private static final String DEBUG_INDEX_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
            <meta charset="UTF-8"><title>Agent 调试面板 · 首页</title>
            <style>
              body { font-family: -apple-system, Segoe UI, Microsoft YaHei, sans-serif; margin: 16px; background: #f6f7f9; }
              h1 { font-size: 18px; } .desc { color: #666; font-size: 13px; margin-bottom: 16px; }
              .jump { display: flex; gap: 8px; margin-bottom: 20px; }
              input { flex: 1; padding: 10px 12px; border: 1px solid #ccc; border-radius: 8px; font-size: 14px; }
              button { padding: 10px 18px; border: 0; border-radius: 8px; background: #3b82f6; color: #fff; font-size: 14px; cursor: pointer; }
              button:hover { background: #2f6fd0; }
              .list-title { font-size: 14px; color: #444; margin-bottom: 8px; }
              .item { background: #fff; border: 1px solid #e2e5ea; border-radius: 8px; padding: 10px 16px;
                margin-bottom: 8px; cursor: pointer; display: flex; align-items: center; gap: 10px; }
              .item:hover { border-color: #3b82f6; }
              .rid { font-family: monospace; font-size: 12px; color: #3b82f6; }
              .q { color: #333; font-size: 13px; flex: 1; word-break: break-all; }
              .done { font-size: 12px; color: #22a06b; } .runing { font-size: 12px; color: #f59e0b; }
              .empty { color: #999; font-size: 13px; }
            </style>
            </head>
            <body>
            <h1>🎛 Agent 调试面板</h1>
            <div class="desc">输入 runId 跳转到该管线运行的 Agent 输入/输出可视化。</div>
            <div class="jump">
              <input id="ridInput" placeholder="请输入 runId，例如 bd682f45-677a-47ae-81d0-6908c3858e38" />
              <button onclick="go()">跳转</button>
            </div>
            <div class="list-title">历史运行</div>
            <div id="runs"><div class="empty">加载中…</div></div>
            <script>
            function go() {
              const rid = document.getElementById('ridInput').value.trim();
              if (rid) location.href = '/pipeline/' + rid + '/debug';
            }
            document.getElementById('ridInput').addEventListener('keydown', e => { if (e.key === 'Enter') go(); });
            async function loadRuns() {
              try {
                const runs = await (await fetch('/pipeline/runs')).json();
                const box = document.getElementById('runs');
                if (!runs.length) { box.innerHTML = '<div class="empty">暂无运行记录，先去 POST /pipeline/run 发起一个管线运行。</div>'; return; }
                box.innerHTML = '';
                runs.forEach(r => {
                  const div = document.createElement('div');
                  div.className = 'item';
                  div.onclick = () => location.href = '/pipeline/' + r.runId + '/debug';
                  div.innerHTML = '<span class="rid">' + r.runId.slice(0, 18) + '…</span>'
                    + '<span class="q">' + escapeHtml(r.question || '—') + '</span>'
                    + '<span class="' + (r.done ? 'done' : 'runing') + '">' + (r.done ? '✅ 已完成' : '⏳ 进行中') + '</span>';
                  box.appendChild(div);
                });
              } catch (e) { document.getElementById('runs').innerHTML = '<div class="empty">加载失败，服务是否已启动？</div>'; }
            }
            function escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
            loadRuns(); setInterval(loadRuns, 3000);
            </script>
            </body>
            </html>
            """;

    /** 单 Run 调试页模板：增量追加 Agent 卡片（保留 details 展开状态） */
    private static final String DEBUG_PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
            <meta charset="UTF-8"><title>Agent 调试面板</title>
            <style>
              body { font-family: -apple-system, Segoe UI, Microsoft YaHei, sans-serif; margin: 16px; background: #f6f7f9; }
              h1 { font-size: 18px; } .meta { color: #666; font-size: 13px; margin-bottom: 12px; word-break: break-all; }
              .card { background: #fff; border: 1px solid #e2e5ea; border-radius: 8px;
                padding: 12px 16px; margin-bottom: 12px; box-shadow: 0 1px 2px rgba(0,0,0,.04); }
              .head { display: flex; align-items: center; gap: 10px; }
              .stage { font-weight: 600; font-size: 14px; }
              .badge { padding: 2px 8px; border-radius: 10px; font-size: 12px; color: #fff; }
              .ok { background: #22a06b; } .fail { background: #e5484d; } .run { background: #3b82f6; }
              .time { color: #888; font-size: 12px; margin-left: auto; }
              details { margin-top: 8px; border-top: 1px dashed #e8eaef; padding-top: 6px; }
              summary { cursor: pointer; font-size: 13px; color: #3b82f6; user-select: none; }
              pre { background: #f0f2f5; padding: 8px; border-radius: 6px; overflow: auto; font-size: 12px; max-height: 260px; }
              .err { color: #e5484d; margin-top: 6px; font-size: 13px; }
            </style>
            </head>
            <body>
            <h1>🎛 Agent 调试面板</h1>
            <div class="meta" id="meta">runId: __RUN_ID__</div>
            <div id="cards"></div>
            <script>
            const RUN_ID = "__RUN_ID__";
            const rendered = new Set();   // 已渲染的 Agent key，避免重建导致 <details> 收起
            async function load() {
              try {
                const state = await (await fetch('/pipeline/' + RUN_ID + '/state')).json();
                document.getElementById('meta').textContent =
                  'runId: ' + RUN_ID + ' ｜ 问题: ' + (state.question || '—')
                  + (state.finalReport ? ' ｜ ✅ 已完成（10 字段报告已产出）' : '');
                const trace = await (await fetch('/pipeline/' + RUN_ID + '/trace')).json();
                const box = document.getElementById('cards');
                trace.forEach(t => {
                  const key = t.stage + '|' + t.agent;
                  if (rendered.has(key)) return;   // 已渲染：保留用户展开的 details
                  rendered.add(key);
                  const ok = t.status === 'SUCCESS';
                  const card = document.createElement('div');
                  card.className = 'card';
                  card.innerHTML =
                    '<div class="head"><span class="badge ' + (ok ? 'ok' : 'fail') + '">'
                    + (ok ? '✓ 成功' : '✗ 失败') + '</span>'
                    + '<span class="stage">' + t.stage + '</span>'
                    + '<span>' + t.agent + '</span>'
                    + '<span class="time">' + t.durationMillis + ' ms</span></div>'
                    + (t.errorMessage ? '<div class="err">⚠ ' + escapeHtml(t.errorMessage) + '</div>' : '')
                    + '<details><summary>📥 输入</summary><pre>' + escapeHtml(pretty(t.input)) + '</pre></details>'
                    + '<details><summary>📤 输出</summary><pre>' + escapeHtml(pretty(t.output)) + '</pre></details>';
                  box.appendChild(card);
                });
              } catch (e) { /* 管线尚未就绪/已结束，继续轮询 */ }
            }
            function pretty(obj) { try { return JSON.stringify(obj, null, 2); } catch (e) { return String(obj); } }
            function escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
            load();
            setInterval(load, 2000);
            </script>
            </body>
            </html>
            """;

    /** 四库 RAG 检索（papers / methods / datasets / evidence） */
    @PostMapping(value = "/rag/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence> ragSearch(
            @RequestBody RagSearchRequest request
    ) {
        return ragSearchService.search(request.knowledgeBase(), request.query(), request.topK());
    }

    /** RAG 检索请求体 */
    public record RagSearchRequest(String knowledgeBase, String query, int topK) {
        public RagSearchRequest {
            if (knowledgeBase == null || knowledgeBase.isBlank()) {
                throw new IllegalArgumentException("knowledgeBase must not be blank");
            }
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query must not be blank");
            }
            if (topK <= 0 || topK > 100) {
                throw new IllegalArgumentException("topK must be between 1 and 100");
            }
        }
    }
}
