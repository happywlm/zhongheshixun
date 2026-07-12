<template>
  <div class="plan-list-page">
    <el-card shadow="never" class="plan-list-page__filter">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item label="计划名称">
          <el-input
            v-model="query.title"
            placeholder="请输入计划名称"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading">
      <el-row :gutter="20" v-if="plans.length > 0">
        <el-col :xs="24" :sm="12" :md="8" v-for="plan in plans" :key="plan.id">
          <el-card class="plan-card" shadow="hover" @click="goDetail(plan)">
            <div class="plan-card__cover">
              <el-icon size="48" color="#1677ff"><Calendar /></el-icon>
            </div>
            <h3 class="plan-card__title" :title="plan.title">{{ plan.title }}</h3>
            <div class="plan-card__desc">{{ plan.description || '暂无简介' }}</div>
            <div class="plan-card__meta">
              <span>📅 创建于 {{ formatTime(plan.createTime) }}</span>
            </div>
            <el-button type="primary" class="plan-card__btn" @click.stop="goDetail(plan)">
              查看详情
            </el-button>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无培训计划" />
    </div>

    <el-pagination
      v-if="!loading && total > 0"
      class="plan-list-page__pagination"
      background
      layout="total, prev, pager, next, jumper"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="handlePageChange"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPlanList } from '@/api/plan'
import { formatTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const plans = ref([])
const total = ref(0)

const query = ref({
  pageNum: 1,
  pageSize: 9,
  title: '',
})

async function fetchList() {
  loading.value = true
  try {
    const params = { ...query.value }
    if (!params.title) delete params.title
    const res = await getPlanList(params)
    plans.value = res?.records || res?.list || res || []
    total.value = res?.total ?? plans.value.length
  } catch (e) {
    ElMessage.warning('培训计划加载失败')
    plans.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.value.pageNum = 1
  fetchList()
}

function handleReset() {
  query.value = { pageNum: 1, pageSize: 9, title: '' }
  fetchList()
}

function handlePageChange(p) {
  query.value.pageNum = p
  fetchList()
}

function goDetail(plan) {
  router.push(`/plans/${plan.id}`)
}

onMounted(fetchList)
</script>

<style scoped>
.plan-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.plan-list-page__filter {
  background: #fff;
}
.plan-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: transform 0.2s;
}
.plan-card:hover {
  transform: translateY(-4px);
}
.plan-card__cover {
  height: 100px;
  background: #f0f7ff;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}
.plan-card__title {
  margin: 0 0 8px;
  font-size: 16px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan-card__desc {
  font-size: 13px;
  color: #606266;
  height: 40px;
  line-height: 1.5;
  overflow: hidden;
  margin-bottom: 8px;
}
.plan-card__meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}
.plan-card__btn {
  width: 100%;
}
.plan-list-page__pagination {
  justify-content: flex-end;
  background: #fff;
  padding: 12px 16px;
  border-radius: 4px;
}
</style>
