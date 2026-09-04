<script setup lang="ts">
const agents = [
  { code: 'A-01', name: '问题理解 Agent', role: '语义拆解与研究边界识别', score: 96, tone: '#35a7ff' },
  { code: 'A-02', name: '文献检索 Agent', role: '真实论文召回与证据提炼', score: 92, tone: '#36cfa6' },
  { code: 'A-03', name: '知识发现 Agent', role: '四库 RAG 与研究缺口发现', score: 94, tone: '#7c68ff' },
  { code: 'A-04', name: '假设生成 Agent', role: '候选假设与推理链构建', score: 89, tone: '#ffae4b' },
  { code: 'A-05', name: '科学评估 Agent', role: '创新性与可行性多维打分', score: 87, tone: '#ff6e86' },
  { code: 'A-06', name: '实验设计 Agent', role: '基线、指标与可证伪协议', score: 91, tone: '#1cc5d8' },
  { code: 'A-07', name: '思辨辩论 Agent', role: '正反论证、风险与边界复核', score: 88, tone: '#5a86ff' },
  { code: 'A-08', name: '报告综合 Agent', role: '十维科研计划与引用锁定', score: 95, tone: '#20bb78' },
]
</script>

<template>
  <div class="observatory">
    <header class="obs-hero">
      <div><span>MODEL & AGENT OBSERVATORY</span><h1>模型能力观测站</h1><p>面向科研任务的多智能体能力画像、运行健康度与可信性观测中心。</p></div>
      <div class="pulse"><i></i><strong>QWEN</strong><small>MODEL ONLINE</small></div>
    </header>

    <section class="status-strip">
      <div><i class="green"></i><span>模型网关</span><strong>ONLINE</strong></div>
      <div><i class="blue"></i><span>平均响应</span><strong>9.7s</strong></div>
      <div><i class="violet"></i><span>上下文窗口</span><strong>32K</strong></div>
      <div><i class="orange"></i><span>报告上限</span><strong>6.5K TOKENS</strong></div>
    </section>

    <section class="agent-grid">
      <article v-for="(agent, index) in agents" :key="agent.code" class="agent-card" :style="{ '--tone': agent.tone }">
        <div class="card-head"><span>{{ agent.code }}</span><i>0{{ index + 1 }}</i></div>
        <div class="agent-symbol"><b></b><b></b><strong>{{ agent.score }}</strong></div>
        <h3>{{ agent.name }}</h3><p>{{ agent.role }}</p>
        <div class="score-line"><span>CAPABILITY SCORE</span><strong>{{ agent.score }}%</strong></div>
        <div class="score-bar"><i :style="{ width: `${agent.score}%` }"></i></div>
        <footer><span><i></i>READY</span><em>PRODUCTION</em></footer>
      </article>
    </section>

    <section class="bottom-grid">
      <article class="matrix">
        <div class="section-title"><span>ORCHESTRATION MATRIX</span><h3>智能体协同矩阵</h3></div>
        <div class="matrix-flow"><div>理解问题</div><i>→</i><div>双路检索</div><i>→</i><div>生成假设</div><i>→</i><div>人工审核</div><i>→</i><div>研究终稿</div></div>
      </article>
      <article class="guardrails">
        <div class="section-title"><span>TRUST GUARDRAILS</span><h3>科研可信护栏</h3></div>
        <div class="guard-list"><p><i>✓</i>引用白名单核验</p><p><i>✓</i>数据来源锁定</p><p><i>✓</i>预期语态校正</p><p><i>✓</i>人在回路审阅</p></div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.observatory{min-height:100%;color:#1b334a}.obs-hero{position:relative;display:flex;align-items:center;justify-content:space-between;padding:32px 38px;border-radius:16px;overflow:hidden;color:white;background:linear-gradient(118deg,#09192e,#142a54 55%,#183f64);box-shadow:0 18px 40px rgba(13,34,65,.2)}.obs-hero::after{content:'';position:absolute;right:-80px;top:-130px;width:390px;height:390px;border:1px solid rgba(85,199,255,.17);border-radius:50%;box-shadow:0 0 0 35px rgba(85,199,255,.035),0 0 0 70px rgba(85,199,255,.025)}.obs-hero>div:first-child{position:relative;z-index:1}.obs-hero span,.section-title span{color:#59c7ff;font-size:9px;font-weight:800;letter-spacing:2px}.obs-hero h1{margin:9px 0 7px;font-size:32px}.obs-hero p{margin:0;color:#a9c7df}.pulse{position:relative;z-index:2;display:flex;flex-direction:column;align-items:center;justify-content:center;width:125px;height:125px;margin-right:55px;border:1px solid rgba(91,205,255,.42);border-radius:50%;background:rgba(21,72,116,.5);box-shadow:0 0 40px rgba(36,167,255,.28)}.pulse i{position:absolute;inset:-11px;border:1px dashed rgba(89,199,255,.4);border-radius:50%}.pulse strong{font-size:25px}.pulse small{color:#66d6ff;font-size:7px;letter-spacing:1.5px}
.status-strip{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;margin:15px 0;border:1px solid #e0e9f2;border-radius:12px;overflow:hidden;background:#e0e9f2}.status-strip>div{display:grid;grid-template-columns:10px 1fr;align-items:center;gap:5px;padding:16px 20px;background:#fff}.status-strip i{width:7px;height:7px;border-radius:50%}.status-strip span{font-size:11px}.status-strip strong{grid-column:2;color:#31516b;font-size:10px}.green{background:#31d394;box-shadow:0 0 8px #31d394}.blue{background:#2fa7ff}.violet{background:#856aff}.orange{background:#ffa84c}
.agent-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:13px}.agent-card{position:relative;padding:18px;border:1px solid #e0e8f1;border-radius:13px;overflow:hidden;background:#fff;box-shadow:0 7px 23px rgba(43,76,108,.06);transition:.2s}.agent-card:hover{transform:translateY(-4px);border-color:var(--tone);box-shadow:0 14px 30px rgba(43,76,108,.13)}.agent-card::before{content:'';position:absolute;left:0;top:0;width:100%;height:3px;background:var(--tone)}.card-head{display:flex;justify-content:space-between}.card-head span{padding:4px 7px;border-radius:8px;color:var(--tone);background:#f2f7fb;font-size:8px;font-weight:800;letter-spacing:1px}.card-head>i{color:#e6edf3;font-family:Georgia;font-size:17px;font-style:normal}.agent-symbol{position:relative;display:flex;align-items:center;justify-content:center;width:68px;height:68px;margin:16px 0 12px;border:1px solid color-mix(in srgb,var(--tone) 35%,white);border-radius:50%}.agent-symbol b{position:absolute;inset:8px;border:1px dashed color-mix(in srgb,var(--tone) 35%,white);border-radius:50%}.agent-symbol b:nth-child(2){inset:15px;border-style:solid}.agent-symbol strong{color:var(--tone);font-size:20px}.agent-card h3{margin:0 0 5px;font-size:14px}.agent-card>p{height:32px;margin:0;color:#8091a1;font-size:10px;line-height:1.6}.score-line{display:flex;justify-content:space-between;margin-top:13px;color:#91a1ae;font-size:8px}.score-line strong{color:var(--tone)}.score-bar{height:4px;margin:6px 0 15px;border-radius:5px;background:#edf2f6}.score-bar i{display:block;height:100%;border-radius:5px;background:var(--tone)}.agent-card footer{display:flex;justify-content:space-between;border-top:1px solid #eff3f6;padding-top:10px;color:#8da0af;font-size:8px}.agent-card footer span{color:#32a77d}.agent-card footer span i{display:inline-block;width:5px;height:5px;margin-right:5px;border-radius:50%;background:#32d496}.agent-card footer em{font-style:normal;letter-spacing:.7px}
.bottom-grid{display:grid;grid-template-columns:1.5fr 1fr;gap:13px;margin-top:13px}.bottom-grid article{padding:20px 23px;border:1px solid #e0e8f1;border-radius:13px;background:#fff}.section-title h3{margin:4px 0 15px}.matrix-flow{display:flex;align-items:center;justify-content:space-between}.matrix-flow div{padding:10px 14px;border:1px solid #dce9f5;border-radius:9px;color:#35617f;background:#f7fbff;font-size:10px}.matrix-flow i{color:#76a8cd;font-style:normal}.guard-list{display:grid;grid-template-columns:1fr 1fr;gap:8px}.guard-list p{margin:0;padding:9px;border-radius:8px;color:#46647c;background:#f5f9fc;font-size:10px}.guard-list i{margin-right:6px;color:#25b780;font-style:normal}
@media(max-width:1100px){.agent-grid{grid-template-columns:repeat(2,1fr)}.bottom-grid{grid-template-columns:1fr}}@media(max-width:760px){.status-strip,.agent-grid{grid-template-columns:1fr}.pulse{display:none}}
</style>
