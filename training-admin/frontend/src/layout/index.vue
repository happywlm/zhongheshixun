<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">{{ title }}</div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#001529"
        text-color="#fff"
        active-text-color="#1677ff"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="'/' + item.path"
        >
          <el-icon v-if="item.meta?.icon">
            <component :is="(item.meta.icon as string)" />
          </el-icon>
          <span>{{ item.meta?.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ title }}</span>
        <el-dropdown @command="onCommand">
          <span class="user">{{ userStore.userInfo?.realName || '管理员' }} ▾</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/user'
import * as icons from '@element-plus/icons-vue'

const title = import.meta.env.VITE_APP_TITLE || '后台管理'
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 从路由配置中动态提取顶级菜单（Layout 的 children）
const menuItems = computed(() => {
  const layoutRoute = router.options.routes.find(r => r.path === '/')
  const children = (layoutRoute?.children || []) as RouteRecordRaw[]
  const userRole = (userStore.userInfo?.role || '').toUpperCase()
  return children.filter(c => {
    if (!c.meta?.title || c.meta?.hidden) return false
    // 非 ADMIN 角色隐藏用户管理和讲师管理
    if (userRole !== 'ADMIN' && (c.path === 'users' || c.path === 'teachers')) {
      return false
    }
    return true
  })
})

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}

// 让 template 能用 icons 对象
const iconsRef = icons
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #001529; }
.logo { color: #fff; text-align: center; padding: 16px; font-weight: bold; }
.header { background: #fff; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #eee; }
.user { cursor: pointer; }
</style>
