import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AppLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'home',
          component: () => import('../views/HomeView.vue')
        },
        {
          path: 'pipeline',
          name: 'pipeline',
          component: () => import('../views/PipelineView.vue')
        },
        {
          path: 'result',
          name: 'result',
          component: () => import('../views/ResultView.vue')
        },
        {
          path: 'history',
          name: 'history',
          component: () => import('../views/HistoryView.vue') // 新增：历史任务看板
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('../views/KnowledgeBaseView.vue') // 新增：四库RAG管理
        },
        {
          path: 'cockpit',
          name: 'cockpit',
          component: () => import('../views/ResearchCockpitView.vue')
        },
        {
          path: 'observatory',
          name: 'observatory',
          component: () => import('../views/ModelObservatoryView.vue')
        },
        {
          path: 'showcase',
          name: 'showcase',
          component: () => import('../views/AchievementShowcaseView.vue')
        },
        {
          path: 'report-print',
          name: 'report-print',
          component: () => import('../views/PrintReportView.vue')
        }
      ]
    }
  ]
})

export default router
