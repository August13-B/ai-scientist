<script setup lang="ts">
const dimensions = ['科学问题定义', '证据链构建', '研究缺口发现', '候选假设生成', '技术路线设计', '数据域规划', '对比实验设计', '统计验收标准', '风险与边界复核', '科研报告综合']
</script>

<template>
  <div class="showcase">
    <section class="showcase-hero">
      <div class="hero-label">AI SCIENTIST · RESEARCH GALLERY</div>
      <h1>科研成果展厅</h1>
      <p>面向挑战杯 A19 赛题打造的国产大模型科研智能体，呈现从证据到假设、从方法到计划的完整创新成果。</p>
      <div class="hero-tags"><span>国产开源大模型</span><span>八智能体协同</span><span>四库 RAG</span><span>人在回路</span><span>可核验引用</span></div>
      <div class="award-seal"><i>★</i><strong>A19</strong><small>AI SCIENTIST</small></div>
    </section>

    <section class="headline-stats">
      <article><strong>08</strong><span>专业智能体</span><small>AGENT COLLABORATION</small></article>
      <article><strong>04</strong><span>科研知识库</span><small>VECTOR KNOWLEDGE BASE</small></article>
      <article><strong>10</strong><span>报告维度</span><small>RESEARCH DIMENSIONS</small></article>
      <article><strong>100%</strong><span>引用可追溯</span><small>EVIDENCE TRACEABILITY</small></article>
    </section>

    <section class="feature-story">
      <div class="story-copy"><span>CORE INNOVATION 01</span><h2>多智能体科研协作闭环</h2><p>系统不是一次性生成文本，而是让问题理解、文献检索、知识发现、假设生成、科学评估、实验设计、思辨辩论与报告综合八类 Agent 在统一数据总线上协作。</p><div class="story-points"><p><i>01</i>阶段产物结构化流转</p><p><i>02</i>关键节点人工审核</p><p><i>03</i>失败状态全程可观测</p></div></div>
      <div class="agent-constellation"><div class="center">AI<span>SCIENTIST</span></div><i v-for="n in 8" :key="n" :class="`star s${n}`">A{{ n }}</i></div>
    </section>

    <section class="feature-story reverse">
      <div class="evidence-wall"><div class="paper p1"><span>PAPER</span><b>SMART time-series</b><small>DOI VERIFIED</small></div><div class="paper p2"><span>METHOD</span><b>Domain adaptation</b><small>TRACEABLE</small></div><div class="paper p3"><span>DATA</span><b>SSD telemetry</b><small>OPEN SOURCE</small></div><div class="beam"></div></div>
      <div class="story-copy"><span>CORE INNOVATION 02</span><h2>四库 RAG 证据增强</h2><p>论文库、方法库、数据库与证据库共同参与检索，让每一条研究缺口、技术路线和参考文献都能够回到真实来源，降低科研生成中的事实幻觉。</p><div class="story-points"><p><i>01</i>真实向量语义召回</p><p><i>02</i>引用标识白名单校验</p><p><i>03</i>本地证据覆盖修复</p></div></div>
    </section>

    <section class="dimension-gallery">
      <div class="gallery-title"><span>FINAL DELIVERABLE</span><h2>十维科学假设与研究计划</h2><p>一份报告覆盖科学研究从问题提出到实验验收的完整生命周期。</p></div>
      <div class="dimension-grid"><article v-for="(item,index) in dimensions" :key="item"><strong>{{ String(index+1).padStart(2,'0') }}</strong><span>{{ item }}</span><i></i></article></div>
    </section>

    <footer class="showcase-footer"><div><span>CHALLENGE CUP · A19</span><strong>让每一个科学问题，都拥有一条可验证的研究路径。</strong></div><b>POWERED BY QWEN</b></footer>
  </div>
</template>

<style scoped>
.showcase{min-height:100%;color:#142e45}.showcase-hero{position:relative;padding:50px 52px;border-radius:17px;overflow:hidden;color:white;background:radial-gradient(circle at 80% 35%,rgba(37,189,255,.25),transparent 25%),linear-gradient(125deg,#041527,#07385d 60%,#07567a);box-shadow:0 20px 45px rgba(7,45,75,.22)}.showcase-hero::before{content:'';position:absolute;inset:0;opacity:.13;background-image:linear-gradient(30deg,transparent 48%,#fff 49%,#fff 50%,transparent 51%);background-size:45px 45px}.hero-label{position:relative;color:#55ccff;font-size:9px;font-weight:800;letter-spacing:2.4px}.showcase h1{position:relative;margin:12px 0 10px;font-size:39px;letter-spacing:3px}.showcase-hero>p{position:relative;max-width:690px;color:#bad3e6;line-height:1.8}.hero-tags{position:relative;display:flex;gap:8px;margin-top:20px}.hero-tags span{padding:7px 12px;border:1px solid rgba(119,212,255,.24);border-radius:15px;color:#caecff;background:rgba(22,93,137,.28);font-size:9px}.award-seal{position:absolute;z-index:2;right:72px;top:42px;display:flex;flex-direction:column;align-items:center;justify-content:center;width:145px;height:145px;border:1px solid rgba(99,211,255,.5);border-radius:50%;background:rgba(4,33,58,.55);box-shadow:0 0 45px rgba(44,178,255,.25)}.award-seal::after{content:'';position:absolute;inset:9px;border:1px dashed rgba(99,211,255,.4);border-radius:50%}.award-seal i{color:#ffd266;font-style:normal}.award-seal strong{font-size:31px}.award-seal small{color:#5fd0ff;font-size:7px;letter-spacing:1.5px}
.headline-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:13px;margin:15px 0}.headline-stats article{padding:22px;border:1px solid #dfe9f2;border-radius:13px;background:white;box-shadow:0 8px 24px rgba(37,74,107,.06)}.headline-stats strong{display:block;color:#147ac1;font-family:Georgia;font-size:30px}.headline-stats span{display:block;margin:3px 0;color:#304e67;font-size:11px}.headline-stats small{color:#9aabba;font-size:7px;letter-spacing:1px}
.feature-story{display:grid;grid-template-columns:1fr 1fr;align-items:center;min-height:315px;margin-bottom:14px;padding:30px 45px;border:1px solid #e0e9f1;border-radius:15px;background:white;overflow:hidden}.story-copy>span,.gallery-title>span{color:#239ce8;font-size:8px;font-weight:800;letter-spacing:1.8px}.story-copy h2,.gallery-title h2{margin:8px 0 10px;font-size:25px}.story-copy>p{color:#71869a;line-height:1.8}.story-points{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:20px}.story-points p{margin:0;padding:12px 8px;border-radius:9px;color:#4f6c83;background:#f4f8fb;font-size:9px}.story-points i{display:block;margin-bottom:5px;color:#249be4;font-family:Georgia;font-size:16px;font-style:normal}
.agent-constellation{position:relative;width:350px;height:245px;margin:auto}.agent-constellation::before,.agent-constellation::after{content:'';position:absolute;inset:35px 85px;border:1px dashed #bcdcf1;border-radius:50%}.agent-constellation::after{inset:70px 55px;transform:rotate(90deg)}.center{position:absolute;z-index:2;left:133px;top:83px;display:flex;flex-direction:column;align-items:center;justify-content:center;width:84px;height:84px;border-radius:50%;color:white;background:linear-gradient(145deg,#168ee7,#655be9);box-shadow:0 12px 28px rgba(50,100,220,.3);font-weight:900}.center span{font-size:7px}.star{position:absolute;z-index:3;display:flex;align-items:center;justify-content:center;width:34px;height:34px;border:1px solid #bfe1f5;border-radius:50%;color:#208fd4;background:#f4fbff;font-size:8px;font-style:normal;box-shadow:0 5px 13px rgba(30,121,180,.12)}.s1{left:35px;top:35px}.s2{left:155px;top:0}.s3{right:35px;top:35px}.s4{right:0;top:110px}.s5{right:55px;bottom:10px}.s6{left:155px;bottom:0}.s7{left:55px;bottom:10px}.s8{left:0;top:110px}
.evidence-wall{position:relative;width:420px;height:230px}.paper{position:absolute;width:160px;padding:18px;border:1px solid #d7e7f3;border-radius:10px;background:white;box-shadow:0 12px 28px rgba(31,78,115,.12)}.paper span{display:block;color:#2b9fe6;font-size:7px;letter-spacing:1.5px}.paper b{display:block;margin:8px 0;color:#284d69;font-size:12px}.paper small{color:#28b280;font-size:7px}.p1{left:20px;top:20px;transform:rotate(-5deg)}.p2{right:35px;top:52px;transform:rotate(4deg)}.p3{left:105px;bottom:5px;transform:rotate(-1deg)}.beam{position:absolute;left:200px;top:95px;width:90px;height:90px;border-radius:50%;background:rgba(49,173,247,.1);box-shadow:0 0 35px rgba(49,173,247,.25)}
.dimension-gallery{padding:35px 42px;border:1px solid #e0e9f1;border-radius:15px;background:linear-gradient(145deg,#fff,#f6fbff)}.gallery-title{text-align:center}.gallery-title p{margin:0 0 25px;color:#8293a2}.dimension-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:9px}.dimension-grid article{position:relative;min-height:88px;padding:15px;border:1px solid #dde9f2;border-radius:10px;background:white;overflow:hidden}.dimension-grid strong{display:block;color:#c0d8e9;font-family:Georgia;font-size:20px}.dimension-grid span{display:block;margin-top:10px;color:#34566f;font-size:10px}.dimension-grid i{position:absolute;right:-15px;bottom:-15px;width:48px;height:48px;border-radius:50%;background:#edf8ff}.showcase-footer{display:flex;justify-content:space-between;align-items:center;margin-top:14px;padding:24px 30px;border-radius:13px;color:white;background:#09233b}.showcase-footer div{display:flex;flex-direction:column;gap:5px}.showcase-footer span{color:#55c9ff;font-size:8px;letter-spacing:1.8px}.showcase-footer strong{font-size:14px}.showcase-footer>b{color:#68849b;font-size:9px;letter-spacing:1.4px}
@media(max-width:1000px){.feature-story{grid-template-columns:1fr}.dimension-grid{grid-template-columns:repeat(2,1fr)}.award-seal{display:none}.headline-stats{grid-template-columns:repeat(2,1fr)}}
</style>
