<script setup lang="ts">
// 流水线工作台（占位）
// TODO: 科研问题提交表单 + 任务列表 + SSE 流式展示 + 人在回路交互
import FlowCanvas from '../components/FlowCanvas.vue'
</script>

<template>
  <div>
    <el-card>
      <template #header>提交科研问题</template>
      <el-input type="textarea" :rows="4" placeholder="请输入科研问题描述…" />
      <el-button type="primary" style="margin-top: 12px">开始研究</el-button>
    </el-card>
    <FlowCanvas style="margin-top: 16px" />
  </div>
</template>
