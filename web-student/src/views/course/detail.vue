<template>
  <div class="course-detail-page" v-loading="loading">
    <!-- 错误态：接口失败 -->
    <el-alert
      v-if="loadError"
      type="error"
      title="课程加载失败，请返回列表重试"
      show-icon
      closable
      class="course-detail-page__alert"
    >
      <template #default>
        <el-button type="primary" link size="small" @click="router.push('/courses')">
          返回课程中心
        </el-button>
      </template>
    </el-alert>

    <el-row :gutter="20" v-else>
      <!-- 左侧主内容 -->
      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="course-detail-page__main">
          <!-- 封面占位 -->
          <div class="course-detail-page__cover">
            <el-icon size="60" color="#1677ff"><VideoPlay /></el-icon>
          </div>
          <h1 class="course-detail-page__title">{{ course.title || '课程详情' }}</h1>
          <div class="course-detail-page__tags">
            <el-tag :type="typeColor(course.type)">{{ typeText(course.type) }}</el-tag>
            <el-tag v-if="course.difficulty" type="info">
              {{ difficultyText(course.difficulty) }}
            </el-tag>
            <el-tag v-if="course.totalHours" type="warning">{{ course.totalHours }} 学时</el-tag>
          </div>
          <div class="course-detail-page__desc">
            <h3>课程简介</h3>
            <p>{{ course.description || '暂无简介' }}</p>
          </div>

          <!-- 章节列表（时间线，比 collapse 更好浏览） -->
          <div class="course-detail-page__chapters" v-if="chapters.length > 0">
            <h3>课程章节（{{ chapters.length }} 节）</h3>
            <el-timeline>
              <el-timeline-item
                v-for="(chapter, idx) in chapters"
                :key="chapter.id"
                :timestamp="formatChapterDuration(chapter.duration)"
                placement="top"
                :type="chapter.finished ? 'success' : 'primary'"
              >
                <div class="chapter-item">
                  <el-icon><Document /></el-icon>
                  <span class="chapter-item__idx">第 {{ idx + 1 }} 节</span>
                  <span class="chapter-item__title">{{ chapter.title }}</span>
                  <el-tag size="small" type="info">
                    {{ contentTypeName(chapter.contentType) }}
                  </el-tag>
                  <el-tag v-if="chapter.finished" size="small" type="success">已学完</el-tag>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
          <el-empty v-else description="暂无章节" />
        </el-card>
      </el-col>

      <!-- 右侧操作面板 -->
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="course-detail-page__side">
          <div class="info-row">
            <span class="info-row__label">授课教师</span>
            <span class="info-row__value">{{ course.teacherName || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-row__label">课程类型</span>
            <span class="info-row__value">{{ typeText(course.type) }}</span>
          </div>
          <div class="info-row">
            <span class="info-row__label">难度等级</span>
            <span class="info-row__value">{{ course.difficulty || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-row__label">总学时</span>
            <span class="info-row__value">{{ course.totalHours || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-row__label">章节数</span>
            <span class="info-row__value">{{ chapters.length || '-' }}</span>
          </div>

          <el-button
            v-if="!enrolled"
            type="primary"
            size="large"
            class="course-detail-page__action-btn"
            :loading="actionLoading"
            @click="handleEnroll"
          >
            立即报名
          </el-button>
          <el-button
            v-else
            type="success"
            size="large"
            class="course-detail-page__action-btn"
            @click="router.push(`/courses/${courseId}/learn`)"
          >
            开始学习
          </el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCourseDetail, getChapterList } from '@/api/course'
import { enrollCourse, getProgress } from '@/api/study'
import { typeText, typeColor, difficultyText, contentTypeName } from '@/utils/dict'

const route = useRoute()
const router = useRouter()
const courseId = computed(() => route.params.id)

const loading = ref(false)
const loadError = ref(false)
const actionLoading = ref(false)
const course = ref({})
const chapters = ref([])
const enrolled = ref(false)
const progressList = ref([])

/**
 * 章节时长格式化
 * 后端 CourseChapter.duration 单位为秒，前端展示为「X 小时 Y 分钟 / Y 分钟」
 * 空值/0 返回空字符串（避免时间轴显示「0 分钟」）。
 */
function formatChapterDuration(seconds) {
  if (seconds === null || seconds === undefined || seconds === 0) return ''
  const total = Number(seconds)
  if (isNaN(total) || total <= 0) return ''
  if (total >= 3600) {
    const h = Math.floor(total / 3600)
    const m = Math.floor((total % 3600) / 60)
    return m > 0 ? `${h} 小时 ${m} 分钟` : `${h} 小时`
  }
  if (total >= 60) {
    const m = Math.floor(total / 60)
    const s = total % 60
    return s > 0 ? `${m} 分 ${s} 秒` : `${m} 分钟`
  }
  return `${total} 秒`
}

/**
 * P1-6 修复：把后端 /study/progress 的进度数据 merge 到 chapters，
 * 让 detail.vue 章节时间线能正确显示"已学完"标签。
 */
function mergeChapterProgress() {
  if (!Array.isArray(chapters.value) || chapters.value.length === 0) return
  if (!Array.isArray(progressList.value)) {
    progressList.value = []
  }
  chapters.value = chapters.value.map((ch) => {
    const p = progressList.value.find((pr) => pr.chapterId === ch.id)
    return {
      ...ch,
      finished: p?.completed === 1 || p?.completed === true,
      progress: p?.progress ?? 0,
    }
  })
}

async function fetchData() {
  loading.value = true
  loadError.value = false
  try {
    const detail = await getCourseDetail(courseId.value)
    // 后端返回 { course, chapters }，前端老版本可能直接拿到 course 实体，做双形态兼容
    course.value = detail?.course ?? detail ?? {}
    try {
      const list = detail?.chapters ?? (await getChapterList(courseId.value))
      chapters.value = Array.isArray(list) ? list : []
    } catch (e) {
      chapters.value = []
    }
    // P1-6：拉取学习进度并 merge 到章节（仅在已登录 + 章节列表非空时）
    try {
      const token = localStorage.getItem('token')
      if (token) {
        progressList.value = await getProgress(courseId.value)
        mergeChapterProgress()
      }
    } catch (e) {
      // 未登录或接口失败不影响详情展示
    }
    // enrolled 优先取详情里的标记，否则查 my-courses 兜底（避免报名后 UI 不刷新）
    if (course.value.enrolled !== undefined) {
      enrolled.value = !!course.value.enrolled
    } else {
      enrolled.value = await checkEnrolled()
    }
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/**
 * 兜底校验当前用户是否已报名（后端 /course/detail 未返回 enrolled 时使用）
 */
async function checkEnrolled() {
  try {
    const { getMyCourses } = await import('@/api/study')
    const res = await getMyCourses({ pageNum: 1, pageSize: 200 })
    const list = res?.records || res?.list || res || []
    return Array.isArray(list) && list.some((c) => String(c.id) === String(courseId.value))
  } catch (e) {
    return false
  }
}

async function handleEnroll() {
  actionLoading.value = true
  try {
    await enrollCourse(courseId.value)
    ElMessage.success('报名成功！')
    enrolled.value = true
  } catch (e) {
    // 业务码 1005 = ENROLL_EXISTS（已报名），视为成功，刷新 UI 状态
    const code = e?.code ?? e?.response?.data?.code
    if (code === 1005) {
      ElMessage.info('您已报名该课程')
      enrolled.value = true
    } else {
      ElMessage.warning('报名失败，请稍后重试')
    }
  } finally {
    actionLoading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.course-detail-page__alert {
  margin-bottom: 16px;
}
.course-detail-page__main {
  background: #fff;
  padding: 24px;
}
.course-detail-page__cover {
  height: 200px;
  background: #f0f7ff;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}
.course-detail-page__title {
  font-size: 22px;
  color: #303133;
  margin: 0 0 12px;
}
.course-detail-page__tags {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}
.course-detail-page__desc h3 {
  font-size: 16px;
  margin: 0 0 8px;
  color: #303133;
}
.course-detail-page__desc p {
  color: #606266;
  line-height: 1.6;
  margin: 0;
}
.course-detail-page__chapters {
  margin-top: 24px;
}
.course-detail-page__chapters h3 {
  font-size: 16px;
  margin: 0 0 12px;
}
.chapter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chapter-item__idx {
  color: #909399;
  font-size: 13px;
}
.chapter-item__title {
  color: #303133;
}
.course-detail-page__side {
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
.course-detail-page__action-btn {
  width: 100%;
  margin-top: 16px;
}
</style>
