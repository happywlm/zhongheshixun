<template>
  <div class="exam-list-page">
    <el-card shadow="never" class="exam-list-page__card">
      <!-- 状态标签页 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="未开始" name="0" />
        <el-tab-pane label="进行中" name="1" />
        <el-tab-pane label="已批阅" name="2" />
      </el-tabs>

      <div v-loading="loading">
        <el-row :gutter="20" v-if="exams.length > 0">
          <el-col :xs="24" :sm="12" :md="8" v-for="exam in exams" :key="exam.id">
            <el-card class="exam-card" shadow="hover">
              <div class="exam-card__header">
                <h3 class="exam-card__title" :title="exam.title">{{ exam.title }}</h3>
                <el-tag :type="statusColor(exam.status)">
                  {{ statusText(exam.status) }}
                </el-tag>
              </div>
              <div class="exam-card__info">
                <div>📝 题目数：{{ exam.questionCount ?? '-' }}</div>
                <div>⏱ 考试时长：{{ exam.duration ?? '-' }} 分钟</div>
                <div>📊 总分：{{ exam.totalScore ?? '-' }} 分（百分制）</div>
                <div>✅ 及格分：{{ exam.passScore ?? '-' }}%</div>
              </div>
              <el-button
                v-if="canStart(exam)"
                type="primary"
                class="exam-card__btn"
                @click="handleStart(exam)"
              >
                开始考试
              </el-button>
              <el-button
                v-else-if="exam.status === 1"
                type="warning"
                class="exam-card__btn"
                @click="handleStart(exam)"
              >
                继续考试
              </el-button>
              <el-button
                v-else-if="exam.status === 2"
                type="success"
                class="exam-card__btn"
                @click="router.push(`/exams/${exam.id}/result`)"
              >
                查看成绩
              </el-button>
              <el-button
                v-else
                class="exam-card__btn"
                disabled
              >
                无重考次数
              </el-button>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else description="暂无考试" />
      </div>

      <!-- 分页 -->
      <el-pagination
        v-if="!loading && total > 0"
        class="exam-list-page__pagination"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.pageSize"
        :current-page="query.pageNum"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExamList } from '@/api/exam'
import { statusText, statusColor } from '@/utils/dict'

const router = useRouter()
const loading = ref(false)
const exams = ref([])
const total = ref(0)
const activeTab = ref('')

const query = ref({
  pageNum: 1,
  pageSize: 9,
  status: null,
})

// status 0=未开始 / 1=进行中（但已无重考次数时也禁用）/ 2=已批阅
// canStart: status=0 且剩余重考次数 > 0
// 注：进行中（status=1）走"继续考试"按钮，不进 canStart
// ⚠️ status 字段语义说明（2026-07-10 审计备注）：
//   学员端 exam.status 是"学员维度"状态：0=未开始/1=进行中/2=已批阅
//   管理端 exam.status 是"管理员维度"状态：0=草稿/1=已发布/2=已下架
//   两者字段名相同但语义不同，请勿与 admin/frontend/src/views/exam/index.vue 混淆
function canStart(exam) {
  return exam.status === 0 && exam.retryLeft > 0
}

async function fetchList() {
  loading.value = true
  try {
    const params = { ...query.value }
    if (!params.status && params.status !== 0) delete params.status
    const res = await getExamList(params)
    // 后端直接返回数组（Result 解包后）
    const list = Array.isArray(res) ? res : (res?.records || res?.list || res?.data || [])
    exams.value = list
    total.value = res?.total ?? list.length
  } catch (e) {
    ElMessage.warning('考试列表加载失败，请刷新重试')
    exams.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleTabChange(tab) {
  query.value.status = tab === '' ? null : Number(tab)
  query.value.pageNum = 1
  fetchList()
}

function handlePageChange(p) {
  query.value.pageNum = p
  fetchList()
}

function handleStart(exam) {
  router.push(`/exams/${exam.id}`)
}

onMounted(fetchList)
</script>

<style scoped>
.exam-list-page__card {
  background: #fff;
}
.exam-card {
  margin-bottom: 20px;
}
.exam-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.exam-card__title {
  margin: 0;
  font-size: 16px;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}
.exam-card__info {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
  margin-bottom: 12px;
}
.exam-card__btn {
  width: 100%;
}
.exam-list-page__pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
