<script setup lang="ts">
import {
  Compass,
  Connection,
  DataAnalysis,
  DataBoard,
  Document,
  Files,
  Monitor,
  Platform,
  Trophy,
} from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <el-container class="layout-container">
    <el-aside width="240px" class="aside-menu">
      <div class="logo-box">
        <el-icon :size="24" color="#409EFC"><Platform /></el-icon>
        <span class="logo-text">AI Scientist</span>
      </div>
      <el-menu :default-active="route.path" router class="el-menu-vertical">
        <div class="menu-caption">RESEARCH WORKFLOW</div>
        <el-menu-item index="/home">
          <el-icon><Document /></el-icon>
          <span>研究启动台</span>
        </el-menu-item>
        <el-menu-item index="/pipeline">
          <el-icon><Connection /></el-icon>
          <span>智能体编排 (Agent)</span>
        </el-menu-item>
        <el-menu-item index="/result">
          <el-icon><Document /></el-icon>
          <span>科研假设报告</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><DataBoard /></el-icon>
          <span>历史科研请求</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Files /></el-icon>
          <span>RAG 知识库引擎</span>
        </el-menu-item>
        <div class="menu-caption menu-caption-secondary">INTELLIGENCE CENTER</div>
        <el-menu-item index="/cockpit">
          <el-icon><DataAnalysis /></el-icon>
          <span>科研数据驾驶舱</span>
        </el-menu-item>
        <el-menu-item index="/observatory">
          <el-icon><Monitor /></el-icon>
          <span>模型能力观测站</span>
        </el-menu-item>
        <el-menu-item index="/showcase">
          <el-icon><Trophy /></el-icon>
          <span>科研成果展厅</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-status">
        <div class="status-line"><span class="status-dot"></span><strong>系统运行正常</strong></div>
        <p>Qwen · Multi-Agent · RAG</p>
        <div class="status-meter"><i></i></div>
        <div class="status-meta"><span>PRODUCTION</span><span>v1.0</span></div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="top-header">
        <div class="header-brand">
          <span class="header-eyebrow">SCIENTIFIC DISCOVERY OS</span>
          <span class="header-title">基于国产开源大模型 Qwen 的 AI 科学家系统</span>
        </div>
        <div class="header-badge"><el-icon><Compass /></el-icon> A19 · 科学假设生成</div>
      </el-header>
      <el-main class="main-content">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}
.aside-menu {
  position: relative;
  display: flex;
  flex-direction: column;
  background-color: #001529;
  color: white;
}
.logo-box {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: white;
  font-size: 20px;
  font-weight: bold;
  border-bottom: 1px solid #1a2a3a;
}
.logo-box::after {
  content: '';
  width: 5px;
  height: 5px;
  margin-left: 2px;
  border-radius: 50%;
  background: #37d7ff;
  box-shadow: 0 0 12px #37d7ff;
}
.el-menu-vertical {
  flex: 1;
  border-right: none;
  background-color: transparent;
}
:deep(.el-menu-item) {
  color: #a6adb4;
  margin: 2px 10px;
  border-radius: 8px;
  height: 46px;
  line-height: 46px;
}
:deep(.el-menu-item.is-active) {
  color: #fff;
  background-color: #1890ff;
  box-shadow: 0 7px 20px rgba(24, 144, 255, .28);
}
.menu-caption {
  padding: 18px 20px 8px;
  color: #50677d;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.5px;
}
.menu-caption-secondary { padding-top: 24px; }
.aside-status {
  margin: 12px;
  padding: 14px;
  border: 1px solid rgba(102, 196, 255, .17);
  border-radius: 11px;
  background: linear-gradient(145deg, rgba(20, 73, 116, .45), rgba(5, 29, 52, .78));
}
.status-line { display: flex; align-items: center; gap: 8px; color: #d9efff; font-size: 12px; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: #33e39d; box-shadow: 0 0 10px #33e39d; }
.aside-status p { margin: 7px 0 10px; color: #64839c; font-size: 9px; }
.status-meter { height: 3px; border-radius: 4px; background: #163550; overflow: hidden; }
.status-meter i { display: block; width: 82%; height: 100%; background: linear-gradient(90deg, #1890ff, #35e0b4); }
.status-meta { display: flex; justify-content: space-between; margin-top: 8px; color: #55738c; font-size: 8px; letter-spacing: .8px; }
.top-header {
  background-color: white;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  box-shadow: 0 1px 4px rgba(0,21,41,.08);
  justify-content: space-between;
}
.header-brand { display: flex; flex-direction: column; gap: 2px; }
.header-eyebrow { color: #409eff; font-size: 8px; font-weight: 800; letter-spacing: 1.6px; }
.header-title {
  font-weight: 600;
  color: #303133;
}
.header-badge { display: flex; align-items: center; gap: 6px; padding: 7px 11px; border: 1px solid #d9e9f7; border-radius: 20px; color: #52708c; background: #f7fbff; font-size: 11px; }
.main-content {
  background-color: #f0f2f5;
  padding: 24px;
}
</style>
