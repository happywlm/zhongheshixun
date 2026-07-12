<template>
  <div class="course-list-page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="course-list-page__filter">
      <el-form inline @submit.prevent="handleSearch">
        <el-form-item label="课程名称">
          <el-input
            v-model="query.title"
            placeholder="请输入课程名称"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="课程类型">
          <el-select
            v-model="query.courseType"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option label="必修" :value="1" />
            <el-option label="选修" :value="2" />
            <el-option label="计划" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 课程卡片网格 -->
    <div v-loading="loading">
      <el-row :gutter="20" v-if="courses.length > 0">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="course in courses" :key="course.id">
          <el-card class="course-card" shadow="hover">
            <div class="course-card__cover" :style="course.coverUrl ? `background-image:url(${course.coverUrl});background-size:cover;background-position:center` : ''">
              <el-icon v-if="!course.coverUrl" size="40" color="#1677ff"><VideoPlay /></el-icon>
            </div>
            <div class="course-card__title" :title="course.title">{{ course.title }}</div>
            <div class="course-card__tags">
              <el-tag size="small" :type="typeColor(course.type)">
                {{ typeText(course.type) }}
              </el-tag>
              <el-tag v-if="course.difficulty" size="small" type="info">
                {{ difficultyText(course.difficulty) }}
              </el-tag>
            </div>
            <div class="course-card__desc">{{ course.description || '暂无描述' }}</div>
            <div class="course-card__meta">
              <span v-if="course.hours">📚 {{ course.hours }} 学时</span>
              <span v-if="course.teacherName">👨‍🏫 {{ course.teacherName }}</span>
            </div>
            <el-button
              type="primary"
              class="course-card__btn"
              @click="router.push(`/courses/${course.id}`)"
            >
              查看详情
            </el-button>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无课程" />
    </div>

    <!-- 分页：空列表或加载中时不显示，避免误导 -->
    <el-pagination
      v-if="!loading && total > 0"
      class="course-list-page__pagination"
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
import { getCourseList } from '@/api/course'
import { typeText, typeColor, difficultyText } from '@/utils/dict'

const router = useRouter()
const loading = ref(false)
const courses = ref([])
const total = ref(0)

const query = ref({
  pageNum: 1,
  pageSize: 12,
  title: '',
  courseType: null,
})

async function fetchList() {
  loading.value = true
  try {
    const params = { ...query.value }
    if (!params.courseType) delete params.courseType
    if (!params.title) delete params.title
    const res = await getCourseList(params)
    courses.value = res?.records || res?.list || res || []
    total.value = res?.total ?? courses.value.length
  } catch (e) {
    ElMessage.warning('课程列表加载失败，请刷新重试')
    courses.value = []
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
  query.value = { pageNum: 1, pageSize: 12, title: '', courseType: null }
  fetchList()
}

function handlePageChange(p) {
  query.value.pageNum = p
  fetchList()
}

onMounted(fetchList)
</script>

<style scoped>
.course-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.course-list-page__filter {
  background: #fff;
}
.course-card {
  margin-bottom: 20px;
  text-align: center;
  cursor: pointer;
  transition: transform 0.2s;
}
.course-card:hover {
  transform: translateY(-4px);
}
.course-card__cover {
  height: 120px;
  background: #f0f7ff;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}
.course-card__title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-card__tags {
  display: flex;
  gap: 6px;
  justify-content: center;
  margin-bottom: 8px;
}
.course-card__desc {
  font-size: 12px;
  color: #909399;
  height: 32px;
  overflow: hidden;
  margin-bottom: 8px;
}
.course-card__meta {
  font-size: 12px;
  color: #606266;
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 12px;
}
.course-card__btn {
  width: 100%;
}
.course-list-page__pagination {
  justify-content: flex-end;
  background: #fff;
  padding: 12px 16px;
  border-radius: 4px;
}
</style>
