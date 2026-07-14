<template>
  <div class="exam-result-page" v-loading="loading">
    <el-alert
      v-if="loadError"
      type="error"
      title="成绩加载失败，请返回重试"
      show-icon
      closable
      class="exam-result-page__alert"
    >
      <template #default>
        <el-button type="primary" link size="small" @click="router.push('/exams')">
          返回考试中心
        </el-button>
      </template>
    </el-alert>

    <el-row :gutter="20" v-else>
      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="exam-result-page__main">
          <div class="exam-result-page__header">
            <el-icon :size="48" :color="record.passed ? '#67C23A' : '#F56C6C'">
              <component :is="record.passed ? 'CircleCheck' : 'CircleClose'" />
            </el-icon>
            <h1 class="exam-result-page__title">
              {{ record.passed ? '考试通过' : '考试未通过' }}
            </h1>
            <div :class="['exam-result-page__badge', record.passed ? 'is-pass' : 'is-fail']">
              {{ record.passed ? '通  过' : '未通过' }}
            </div>
          </div>

          <div class="exam-result-page__score">
            <el-statistic title="我的得分" :value="record.score ?? 0" />
            <el-statistic title="满分" :value="record.totalScore ?? 0" />
          </div>

          <div class="exam-result-page__breakdown">
            <h3>答题详情</h3>
            <el-row :gutter="16">
              <el-col :span="8">
                <el-statistic title="答对" :value="record.correctCount ?? 0" suffix="题" />
              </el-col>
              <el-col :span="8">
                <el-statistic title="答错" :value="record.wrongCount ?? 0" suffix="题" />
              </el-col>
              <el-col :span="8">
                <el-statistic title="未作答" :value="record.unansweredCount ?? 0" suffix="题" />
              </el-col>
            </el-row>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="exam-result-page__side">
          <div class="info-row">
            <span class="info-row__label">考试得分</span>
            <span class="info-row__value">{{ record.score ?? 0 }} 分</span>
          </div>
          <div class="info-row">
            <span class="info-row__label">满分</span>
            <span class="info-row__value">{{ record.totalScore ?? 0 }} 分</span>
          </div>
          <div class="info-row">
            <span class="info-row__label">答对题数</span>
            <span class="info-row__value">{{ record.correctCount ?? 0 }}</span>
          </div>
          <div class="info-row">
            <span class="info-row__label">答错题数</span>
            <span class="info-row__value">{{ record.wrongCount ?? 0 }}</span>
          </div>
          <div class="info-row">
            <span class="info-row__label">未作答</span>
            <span class="info-row__value">{{ record.unansweredCount ?? 0 }}</span>
          </div>
          <div class="info-row">
            <span class="info-row__label">是否通过</span>
            <span class="info-row__value">
              <el-tag :type="record.passed ? 'success' : 'danger'" size="small">
                {{ record.passed ? '通过' : '未通过' }}
              </el-tag>
            </span>
          </div>
          <el-button type="primary" class="exam-result-page__btn" @click="router.push('/exams')">
            返回考试中心
          </el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExamResult } from '@/api/exam'

const route = useRoute()
const router = useRouter()
const examId = route.params.id

const loading = ref(false)
const loadError = ref(false)
const record = ref({})
const reviewList = ref([])

async function fetchRecord() {
  loading.value = true
  loadError.value = false
  try {
    // 优先从 sessionStorage 读取（answer.vue 提交成功后写入）
    const cached = sessionStorage.getItem('exam:lastResult')
    if (cached) {
      // 读取后立即清除，避免重复查看旧成绩
      sessionStorage.removeItem('exam:lastResult')
      try {
        record.value = JSON.parse(cached)
        reviewList.value = []
        return
      } catch (e) {
        // JSON 解析失败，走兜底逻辑
      }
    }
    // 兜底：通过 examId 查询最新成绩（用户从考试列表查看历史成绩时）
    if (examId) {
      try {
        const data = await getExamResult(examId)
        if (data) {
          record.value = data
          reviewList.value = data?.details || data?.questions || []
          return
        }
      } catch (e) {
        // 接口失败，走下面的提示
      }
    }
    // sessionStorage 为空（用户直接访问 result 页面），提示并引导跳转回列表
    ElMessage.warning('请从考试列表进入查看成绩')
    router.replace('/exams')
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(fetchRecord)
</script>

<style scoped>
.exam-result-page__alert {
  margin-bottom: 16px;
}
.exam-result-page__main {
  background: #fff;
  padding: 24px;
  text-align: center;
}
.exam-result-page__header {
  margin-bottom: 24px;
}
.exam-result-page__title {
  margin: 12px 0 8px;
  font-size: 22px;
  color: #303133;
}
.exam-result-page__badge {
  display: inline-block;
  padding: 4px 16px;
  border-radius: 16px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
}
.exam-result-page__badge.is-pass {
  background: #67C23A;
}
.exam-result-page__badge.is-fail {
  background: #F56C6C;
}
.exam-result-page__score {
  display: flex;
  justify-content: space-around;
  gap: 16px;
  margin: 24px 0;
}
.exam-result-page__breakdown {
  margin-top: 24px;
}
.exam-result-page__breakdown h3 {
  font-size: 16px;
  margin: 0 0 12px;
  color: #303133;
}
.exam-result-page__side {
  background: #fff;
  padding: 20px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}
.info-row:last-of-type {
  border-bottom: none;
}
.info-row__label {
  color: #909399;
}
.info-row__value {
  color: #303133;
  font-weight: 500;
}
.exam-result-page__btn {
  width: 100%;
  margin-top: 16px;
}
</style>
