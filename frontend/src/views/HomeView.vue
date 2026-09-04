<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { createTask } from '@/api/tasks'
import { rememberCurrentTask } from '@/utils/currentTask'

const router = useRouter()
const researchQuestion = ref('')
const isStarting = ref(false)

const startResearch = async () => {
  const question = researchQuestion.value.trim()
  if (!question || isStarting.value) return

  isStarting.value = true
  try {
    const { taskId } = await createTask(question)
    rememberCurrentTask(taskId, question)
    await router.push({ path: '/pipeline', query: { taskId: String(taskId) } })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '启动科研任务失败，请稍后重试')
  } finally {
    isStarting.value = false
  }
}
</script>

<template>
  <div class="home-container">
    <el-card class="box-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <h2>开启您的科研灵感流水线</h2>
          <span class="subtitle">请输入特定学科领域的科学问题，AI将为您挖掘文献并生成可验证假设</span>
        </div>
      </template>

      <el-input
        v-model="researchQuestion"
        type="textarea"
        :rows="4"
        placeholder="例如：如何利用多组学数据预测药物靶点？存在哪些痛点和局限性？"
        class="main-input"
      />

      <div class="upload-section">
        <el-upload class="upload-demo" drag action="#" multiple :auto-upload="false">
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">拖拽 PDF文献 或 CSV实验数据 到此处，或 <em>点击上传</em></div>
        </el-upload>
      </div>

      <div class="action-bar">
        <el-button
          type="primary"
          size="large"
          @click="startResearch"
          :disabled="!researchQuestion.trim()"
          :loading="isStarting"
        >
          启动 AI Scientist 核心管线
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.home-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}
.box-card {
  width: 800px;
  border-radius: 12px;
}
.card-header h2 {
  margin: 0 0 8px 0;
  color: #303133;
}
.subtitle {
  font-size: 14px;
  color: #909399;
}
.main-input {
  margin-bottom: 24px;
}
.upload-section {
  margin-bottom: 24px;
}
.action-bar {
  text-align: center;
}
</style>
