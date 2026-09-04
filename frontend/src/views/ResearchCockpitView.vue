<script setup lang="ts">
const stages = [
  { name: '问题理解', value: 96, color: '#3c9cff' },
  { name: '证据检索', value: 91, color: '#39d6c0' },
  { name: '假设生成', value: 88, color: '#795cff' },
  { name: '实验设计', value: 84, color: '#ffb454' },
]

const activity = [
  ['14:32', '报告生成 Agent 完成十维研究计划综合', '成功'],
  ['14:29', '思辨 Agent 完成正反论证与风险校验', '完成'],
  ['14:26', '实验设计 Agent 输出可证伪验收标准', '完成'],
  ['14:18', '四库 RAG 返回高相关证据片段', '已核验'],
]
</script>

<template>
  <div class="cockpit page-shell">
    <section class="hero-panel">
      <div class="hero-copy">
        <span class="eyebrow">RESEARCH INTELLIGENCE COCKPIT</span>
        <h1>科研数据驾驶舱</h1>
        <p>从科学问题到实验计划，实时汇聚多智能体协作、证据检索与假设评估的全链路态势。</p>
      </div>
      <div class="hero-orbit"><div class="core">AI<span>SCIENTIST</span></div><i></i><i></i><i></i></div>
    </section>

    <section class="metric-grid">
      <article><span>活跃智能体</span><strong>08</strong><small>ALL AGENTS ONLINE</small></article>
      <article><span>知识库向量</span><strong>42.8K</strong><small>4 DOMAINS INDEXED</small></article>
      <article><span>证据可信度</span><strong>94.6%</strong><small>TRACEABLE SOURCES</small></article>
      <article><span>科研方案完成度</span><strong>100%</strong><small>10 DIMENSIONS READY</small></article>
    </section>

    <section class="cockpit-grid">
      <article class="panel capability-panel">
        <div class="panel-title"><div><span>PIPELINE CAPABILITY</span><h3>智能体能力矩阵</h3></div><b>实时</b></div>
        <div v-for="item in stages" :key="item.name" class="cap-row">
          <div><span>{{ item.name }}</span><strong>{{ item.value }}%</strong></div>
          <div class="bar"><i :style="{ width: `${item.value}%`, background: item.color }"></i></div>
        </div>
      </article>

      <article class="panel radar-panel">
        <div class="panel-title"><div><span>SCIENTIFIC QUALITY</span><h3>科研质量雷达</h3></div><b>84 / 100</b></div>
        <div class="radar-wrap">
          <div class="radar"><div class="radar-fill"></div><span class="r1">创新性</span><span class="r2">可行性</span><span class="r3">可复现性</span><span class="r4">证据强度</span><span class="r5">数据可得性</span></div>
          <div class="radar-legend"><p><i></i>综合评估</p><strong>A</strong><small>推荐进入实验验证</small></div>
        </div>
      </article>

      <article class="panel activity-panel">
        <div class="panel-title"><div><span>LIVE ACTIVITY</span><h3>科研流水线动态</h3></div><b>LIVE</b></div>
        <div v-for="row in activity" :key="row[0]" class="activity-row">
          <time>{{ row[0] }}</time><i></i><p>{{ row[1] }}</p><em>{{ row[2] }}</em>
        </div>
      </article>

      <article class="panel map-panel">
        <div class="panel-title"><div><span>KNOWLEDGE LANDSCAPE</span><h3>四库知识覆盖</h3></div><b>CONNECTED</b></div>
        <div class="knowledge-map">
          <div class="map-core">RAG<span>ENGINE</span></div>
          <div class="node n1">论文库<small>18.4K</small></div>
          <div class="node n2">方法库<small>9.7K</small></div>
          <div class="node n3">数据库<small>8.2K</small></div>
          <div class="node n4">证据库<small>6.5K</small></div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.page-shell { min-height: 100%; color: #183047; }
.hero-panel { position: relative; display: flex; justify-content: space-between; min-height: 190px; padding: 34px 42px; overflow: hidden; border-radius: 16px; color: white; background: radial-gradient(circle at 78% 30%, rgba(38,194,255,.28), transparent 27%), linear-gradient(125deg,#051a31,#073d67 58%,#0b567c); box-shadow: 0 18px 45px rgba(15,57,92,.2); }
.hero-panel::before { content:''; position:absolute; inset:0; opacity:.16; background-image:linear-gradient(rgba(255,255,255,.2) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.2) 1px,transparent 1px); background-size:28px 28px; }
.hero-copy { position: relative; z-index: 1; max-width: 590px; }
.eyebrow,.panel-title span { color:#52c8ff; font-size:9px; font-weight:800; letter-spacing:2px; }
h1 { margin:10px 0 8px; font-size:34px; letter-spacing:2px; } .hero-copy p { max-width:580px; color:#bcd6e9; line-height:1.8; }
.hero-orbit { position:relative; width:170px; height:140px; margin-right:45px; }
.hero-orbit::before,.hero-orbit::after,.hero-orbit i { content:''; position:absolute; inset:12px; border:1px solid rgba(82,200,255,.35); border-radius:50%; transform:rotate(-18deg); }
.hero-orbit::after { inset:0 26px; transform:rotate(52deg); }.hero-orbit i:nth-child(2){inset:24px 0;transform:rotate(92deg)}
.core { position:absolute; z-index:2; top:45px; left:48px; display:flex; flex-direction:column; align-items:center; justify-content:center; width:76px;height:76px;border-radius:50%;background:linear-gradient(145deg,#24a8ff,#3154d9);box-shadow:0 0 38px rgba(53,176,255,.65);font-weight:900;font-size:18px}.core span{font-size:7px;letter-spacing:1px}
.metric-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:14px; margin:16px 0; }.metric-grid article,.panel{border:1px solid #e1eaf3;border-radius:13px;background:#fff;box-shadow:0 8px 24px rgba(38,72,108,.06)}
.metric-grid article{position:relative;padding:20px 22px;overflow:hidden}.metric-grid article::after{content:'';position:absolute;right:-18px;bottom:-30px;width:86px;height:86px;border-radius:50%;background:#eaf6ff}.metric-grid span{display:block;color:#7b8c9c;font-size:12px}.metric-grid strong{display:block;margin:7px 0 2px;color:#123e62;font-size:28px}.metric-grid small{color:#9ab0c2;font-size:8px;letter-spacing:1px}
.cockpit-grid{display:grid;grid-template-columns:1.05fr .95fr;gap:14px}.panel{padding:22px}.panel-title{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:20px}.panel-title h3{margin:4px 0 0;font-size:17px}.panel-title b{padding:5px 9px;border-radius:12px;color:#168bd5;background:#eaf7ff;font-size:9px;letter-spacing:.8px}
.cap-row{margin:17px 0}.cap-row>div:first-child{display:flex;justify-content:space-between;font-size:12px}.bar{height:6px;margin-top:8px;border-radius:8px;background:#edf2f7;overflow:hidden}.bar i{display:block;height:100%;border-radius:8px;box-shadow:0 0 10px currentColor}
.radar-wrap{display:flex;align-items:center;justify-content:center;gap:45px}.radar{position:relative;width:170px;height:150px;background:repeating-radial-gradient(circle,transparent 0 19px,#dce9f3 20px 21px,transparent 22px 39px);clip-path:polygon(50% 0,98% 36%,80% 100%,20% 100%,2% 36%)}.radar-fill{position:absolute;inset:25px 22px 18px;background:rgba(40,153,255,.32);clip-path:polygon(50% 0,98% 38%,74% 100%,15% 82%,8% 30%);border:2px solid #299bff}.radar span{position:absolute;font-size:9px}.r1{top:3px;left:67px}.r2{top:45px;right:1px}.r3{bottom:3px;right:10px}.r4{bottom:3px;left:3px}.r5{top:45px;left:0}.radar-legend strong{display:block;color:#168ee5;font-size:48px}.radar-legend p{font-size:11px}.radar-legend p i{display:inline-block;width:8px;height:8px;margin-right:6px;border-radius:2px;background:#299bff}.radar-legend small{color:#8296a8}
.activity-row{display:grid;grid-template-columns:42px 12px 1fr auto;align-items:center;gap:10px;padding:13px 0;border-bottom:1px solid #edf2f7}.activity-row time{color:#8ca0b1;font-size:10px}.activity-row>i{width:7px;height:7px;border-radius:50%;background:#33d29b;box-shadow:0 0 8px #33d29b}.activity-row p{margin:0;font-size:11px}.activity-row em{padding:4px 8px;border-radius:9px;color:#188d67;background:#e8faf4;font-size:9px;font-style:normal}
.knowledge-map{position:relative;height:185px}.map-core{position:absolute;top:55px;left:calc(50% - 42px);display:flex;flex-direction:column;align-items:center;justify-content:center;width:84px;height:84px;border-radius:50%;color:white;background:linear-gradient(145deg,#197de3,#6c5ce7);box-shadow:0 10px 30px rgba(60,99,220,.34);font-weight:900}.map-core span{font-size:7px}.node{position:absolute;padding:9px 14px;border:1px solid #d7e9f7;border-radius:20px;background:#f7fbff;color:#31607f;font-size:10px;box-shadow:0 6px 15px rgba(44,100,145,.08)}.node small{margin-left:7px;color:#2497e8}.n1{top:8px;left:10%}.n2{top:8px;right:10%}.n3{bottom:5px;left:7%}.n4{bottom:5px;right:7%}
@media(max-width:1000px){.metric-grid{grid-template-columns:repeat(2,1fr)}.cockpit-grid{grid-template-columns:1fr}.hero-orbit{display:none}}
</style>
