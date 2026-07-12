import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      },
      {
        path: 'courses',
        name: 'Courses',
        component: () => import('@/views/course/index.vue'),
        meta: { title: '课程管理', icon: 'Reading' }
      },
      {
        path: 'stats',
        name: 'Stats',
        component: () => import('@/views/stats/index.vue'),
        meta: { title: '统计报表', icon: 'DataAnalysis' }
      },
      {
        path: 'consult',
        name: 'Consult',
        component: () => import('@/views/consult/index.vue'),
        meta: { title: '咨询管理', icon: 'ChatDotRound' }
      },
      {
        path: 'chapters',
        name: 'Chapters',
        component: () => import('@/views/chapter/index.vue'),
        meta: { title: '章节管理', icon: 'Film' }
      },
      {
        path: 'questions',
        name: 'Questions',
        component: () => import('@/views/question/index.vue'),
        meta: { title: '试题管理', icon: 'Edit' }
      },
      {
        path: 'exams',
        name: 'Exams',
        component: () => import('@/views/exam/index.vue'),
        meta: { title: '考试管理', icon: 'Notebook' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/index.vue'),
        meta: { title: '知识点管理', icon: 'Collection' }
      },
      {
        path: 'train-plans',
        name: 'TrainPlans',
        component: () => import('@/views/train-plan/index.vue'),
        meta: { title: '培训计划', icon: 'Calendar' }
      },
      {
        path: 'train-plans/:id',
        name: 'TrainPlanDetail',
        component: () => import('@/views/train-plan/detail.vue'),
        meta: { title: '计划详情', hidden: true }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'teachers',
        name: 'Teachers',
        component: () => import('@/views/teacher/index.vue'),
        meta: { title: '讲师管理', icon: 'Avatar' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    next()
  } else if (!userStore.token) {
    next('/login')
  } else {
    // 非 ADMIN 角色禁止直接访问用户管理和讲师管理
    const userRole = (userStore.userInfo?.role || '').toUpperCase()
    if (userRole !== 'ADMIN' && (to.path === '/users' || to.path === '/teachers')) {
      next('/dashboard')
    } else {
      next()
    }
  }
})

export default router
