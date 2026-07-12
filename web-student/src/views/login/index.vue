<template>
  <div class="login-page">
    <div class="login-page__card">
      <!-- 顶部 logo + 标题 -->
      <div class="login-page__header">
        <el-icon size="48" color="#1677ff"><Reading /></el-icon>
        <h1 class="login-page__title">四川省基层卫生人员网络培训平台</h1>
        <p class="login-page__subtitle">学员登录</p>
      </div>

      <!-- 登录表单 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-page__form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-page__btn"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-page__footer">© 2026 四川省基层卫生人员网络培训平台</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = ref({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// 已登录则直接跳首页
onMounted(() => {
  if (userStore.isLoggedIn) {
    router.push('/home')
  }
})

async function handleLogin() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const loginData = await userStore.login(form.value)
      const role = loginData?.userInfo?.role || loginData?.userInfo?.roleCode || ''
      // 教师和管理员引导去管理后台 5176
      if (role === 'ADMIN' || role === 'TEACHER') {
        const roleText = role === 'ADMIN' ? '管理员' : '讲师'
        ElMessage({
          message: `${roleText}账号请使用管理后台（5176）获得完整体验，3 秒后自动跳转...`,
          type: 'success',
          duration: 3000,
        })
        setTimeout(() => {
          window.open('http://localhost:5176', '_blank')
        }, 3000)
        // 不进入学员前台，留在登录页
        loading.value = false
        return
      }
      ElMessage.success('登录成功')
      router.push('/home')
    } catch (err) {
      if (err && err.code === 1002) {
        ElMessage.error('用户名或密码错误')
      } else if (err && err.code === 403) {
        ElMessage.error('账号已被禁用')
      } else if (err && err.message) {
        ElMessage.error(err.message)
      }
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  width: 100%;
  background: linear-gradient(135deg, #1677ff 0%, #409eff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-page__card {
  width: 400px;
  padding: 40px 32px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}
.login-page__header {
  text-align: center;
  margin-bottom: 32px;
}
.login-page__title {
  font-size: 20px;
  color: #1677ff;
  margin: 12px 0 4px;
}
.login-page__subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}
.login-page__form {
  margin-top: 16px;
}
.login-page__btn {
  width: 100%;
}
.login-page__footer {
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 16px;
  margin-bottom: 0;
}
</style>
