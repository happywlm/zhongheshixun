<template>
  <el-container class="main-layout">
    <!-- 左侧导航 -->
    <el-aside width="220px" class="main-layout__aside">
      <div class="main-layout__logo">
        <el-icon size="24" color="#1677ff"><Reading /></el-icon>
        <span class="main-layout__logo-text">卫培在线</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="main-layout__menu"
        background-color="#001529"
        text-color="#fff"
        active-text-color="#1677ff"
      >
        <!-- 6 个核心菜单：学员专用前台，所有登录用户可见 -->
        <el-menu-item v-for="m in coreMenus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧内容 -->
    <el-container>
      <el-header class="main-layout__header">
        <div class="main-layout__header-title">{{ pageTitle }}</div>
        <div class="main-layout__header-actions">
          <el-dropdown @command="handleCommand">
            <span class="main-layout__header-user">
              <el-avatar :size="32" class="main-layout__header-avatar">
                {{ userStore.realName.charAt(0) }}
              </el-avatar>
              <span class="main-layout__header-username">
                {{ userStore.realName }}
                <el-tag
                  v-if="roleBadge"
                  :type="roleBadgeType"
                  size="small"
                  effect="light"
                  class="main-layout__role-badge"
                >
                  {{ roleBadge }}
                </el-tag>
              </span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-layout__main">
        <!-- 默认插槽：由 App.vue 注入 router-view，便于按 meta.layout 切换/无布局页 -->
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  House,
  Reading,
  DocumentChecked,
  Calendar,
  ChatDotRound,
  User,
  ArrowDown,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 角色显示文案与 el-tag 类型（顶部 Badge 视觉提示）
const roleBadge = computed(() => {
  const r = userStore.roleCode || ''
  if (r === 'ADMIN') return '管理员'
  if (r === 'TEACHER') return '讲师'
  if (r === 'STUDENT') return '学员'
  return ''
})
const roleBadgeType = computed(() => {
  const r = userStore.roleCode || ''
  if (r === 'ADMIN') return 'danger'
  if (r === 'TEACHER') return 'warning'
  return 'success'
})

// 6 个核心菜单：学员专用前台
const coreMenus = [
  { path: '/home', title: '首页', icon: House },
  { path: '/courses', title: '课程中心', icon: Reading },
  { path: '/exams', title: '考试中心', icon: DocumentChecked },
  { path: '/plans', title: '培训计划', icon: Calendar },
  { path: '/consult', title: '在线咨询', icon: ChatDotRound },
  { path: '/profile', title: '个人中心', icon: User },
]

const activeMenu = computed(() => {
  const path = route.path
  const items = coreMenus.map((m) => m.path)
  const exact = items.find((m) => m === path)
  if (exact) return exact
  const candidates = items
    .filter((m) => m !== '/home' && path.startsWith(m))
    .sort((a, b) => b.length - a.length)
  return candidates[0] || path
})

const pageTitle = computed(() => route.meta.title || '')

async function handleCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      })
      userStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch (e) {
      // 用户主动取消，无需处理
    }
  }
}
</script>

<style scoped>
.main-layout {
  height: 100vh;
}
.main-layout__aside {
  background-color: #001529;
  color: #fff;
}
.main-layout__logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid #1f2d3d;
}
.main-layout__logo-text {
  letter-spacing: 1px;
}
.main-layout__menu {
  border-right: none;
}
.main-layout__menu-divider {
  padding: 16px 20px 8px;
  font-size: 12px;
  color: #6b7785;
  letter-spacing: 1px;
  border-top: 1px solid #1f2d3d;
  margin-top: 8px;
}
.main-layout__menu-divider span {
  text-transform: uppercase;
}
.main-layout__header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  padding: 0 24px;
}
.main-layout__header-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}
.main-layout__header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.main-layout__admin-btn {
  margin-right: 4px;
}
.main-layout__header-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.main-layout__header-username {
  display: flex;
  align-items: center;
  gap: 6px;
}
.main-layout__role-badge {
  font-weight: 500;
}
.main-layout__header-avatar {
  background-color: #1677ff;
  color: #fff;
}
.main-layout__main {
  background-color: #f5f7fa;
  padding: 20px;
}
</style>
