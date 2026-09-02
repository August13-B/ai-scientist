<script setup lang="ts">
// Vue Flow 思维链画布（占位）
// TODO: 根据 SSE 事件（agent.start / agent.result / pipeline.pause 等）动态渲染七 Agent 节点与连线
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
</script>

<template>
  <div style="height: 480px; border: 1px solid #eee">
    <VueFlow :nodes="[]" :edges="[]" fit-view-on-init />
  </div>
</template>
