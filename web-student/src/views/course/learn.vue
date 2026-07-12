<template>
  <div class="course-learn-page" v-loading="loading">
    <!-- P2-8 修复：未报名拦截，提示学员先去详情页报名 -->
    <el-result
      v-if="!loading && !enrolled"
      icon="warning"
      title="您尚未报名该课程"
      sub-title="请先到课程详情页完成报名后，再开始学习"
    >
      <template #extra>
        <el-button type="primary" @click="router.push(`/courses/${courseId}`)">
          去报名
        </el-button>
      </template>
    </el-result>

    <!-- 空章节 -->
    <el-empty v-else-if="!loading && chapters.length === 0" description="该课程暂无章节" />

    <el-row :gutter="16" v-else class="course-learn-page__row">
      <!-- 左侧章节导航 -->
      <el-col :xs="24" :md="6">
        <el-card shadow="never" class="course-learn-page__sidebar">
          <template #header>
            <span class="course-learn-page__course-title">{{ course.title || '课程学习' }}</span>
            <el-progress
              :percentage="totalProgress"
              :stroke-width="6"
              style="margin-top: 8px"
            />
          </template>
          <el-menu
            :default-active="String(activeChapterId)"
            class="course-learn-page__menu"
          >
            <el-menu-item
              v-for="(chapter, idx) in chapters"
              :key="chapter.id"
              :index="String(chapter.id)"
              @click="selectChapter(chapter)"
            >
              <el-icon><Document /></el-icon>
              <span class="course-learn-page__menu-label">{{ idx + 1 }}. {{ chapter.title }}</span>
              <el-icon v-if="chapter.finished" color="#67c23a" class="course-learn-page__check">
                <Check />
              </el-icon>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <!-- 右侧内容区 -->
      <el-col :xs="24" :md="18">
        <el-card shadow="never" class="course-learn-page__content">
          <template #header>
            <h2 class="course-learn-page__chapter-title">
              {{ activeChapter?.title || '请选择章节' }}
            </h2>
          </template>

          <div v-if="activeChapter" class="chapter-content">
            <!-- 视频 -->
            <video
              v-if="activeChapter.contentType === 1"
              :src="activeChapter.content"
              controls
              class="chapter-content__video"
              @ended="handleVideoEnd"
            >
              您的浏览器不支持视频播放
            </video>
            <!-- PDF -->
            <iframe
              v-else-if="activeChapter.contentType === 2"
              :src="activeChapter.content"
              class="chapter-content__pdf"
            />
            <!-- 文本 -->
            <div v-else class="chapter-content__text">
              {{ activeChapter.content || '暂无内容' }}
            </div>
          </div>
          <el-empty v-else description="请从左侧选择章节开始学习" />

          <!-- 底部操作 -->
          <div v-if="activeChapter" class="course-learn-page__footer">
            <el-button
              :disabled="activeChapterId === firstChapterId"
              @click="goPrev"
            >
              上一节
            </el-button>
            <el-button
              :disabled="activeChapterId === lastChapterId"
              @click="goNext"
            >
              下一节
            </el-button>
            <el-button
              v-if="!activeChapter.finished"
              type="success"
              :loading="progressLoading"
              @click="markFinished"
            >
              标记为已学完
            </el-button>
            <el-tag v-else type="success">已完成本节学习</el-tag>
          </div>
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
import { reportProgress, getProgress } from '@/api/study'

const route = useRoute()
const router = useRouter()
const courseId = route.params.id

const loading = ref(false)
const progressLoading = ref(false)
const course = ref({})
const chapters = ref([])
const progressList = ref([])
const enrolled = ref(true) // P2-8：默认 true，避免 loading 期间被错误拦截；fetchData 后会回填

const activeChapterId = ref(null)

const activeChapter = computed(() =>
  chapters.value.find((c) => c.id === activeChapterId.value)
)

const firstChapterId = computed(() => chapters.value[0]?.id)
const lastChapterId = computed(() => chapters.value[chapters.value.length - 1]?.id)

// 总进度 = 已完成章节数 / 总章节数
const totalProgress = computed(() => {
  if (chapters.value.length === 0) return 0
  const done = chapters.value.filter((c) => c.finished).length
  return Math.round((done / chapters.value.length) * 100)
})

// 同步进度到章节
function mergeProgress() {
  chapters.value = chapters.value.map((ch) => {
    const p = progressList.value.find((pr) => pr.chapterId === ch.id)
    return {
      ...ch,
      finished: p?.completed === 1 || p?.completed === true,
      progress: p?.progress ?? 0,
    }
  })
}

function selectChapter(chapter) {
  activeChapterId.value = chapter.id
}

function goPrev() {
  const idx = chapters.value.findIndex((c) => c.id === activeChapterId.value)
  if (idx > 0) activeChapterId.value = chapters.value[idx - 1].id
}

function goNext() {
  const idx = chapters.value.findIndex((c) => c.id === activeChapterId.value)
  if (idx < chapters.value.length - 1) activeChapterId.value = chapters.value[idx + 1].id
}

async function markFinished() {
  if (!activeChapterId.value) return
  progressLoading.value = true
  try {
    await reportProgress({
      courseId,
      chapterId: activeChapterId.value,
      progress: 100,
      completed: true,
    })
    ElMessage.success('已标记为学完')
    // 更新本地状态
    const ch = chapters.value.find((c) => c.id === activeChapterId.value)
    if (ch) {
      ch.finished = true
      ch.progress = 100
    }
  } catch (e) {
    ElMessage.warning('学习进度上报失败，请重试')
  } finally {
    progressLoading.value = false
  }
}

// 视频播放结束自动标记
async function handleVideoEnd() {
  if (activeChapter.value && !activeChapter.value.finished) {
    await markFinished()
  }
}

async function fetchData() {
  loading.value = true
  try {
    // P0-2 修复：getCourseDetail 返回 { course, chapters }，前端需解构取 course 实体
    // 兼容双形态：detail.course / detail 自身是 course（旧版本）
    const detail = await getCourseDetail(courseId)
    course.value = detail?.course ?? detail ?? {}
    // 如果 detail 内含 chapters（后端 CourseDetailVO 现状），优先用之，否则单独拉取
    const inlineChapters = detail?.chapters
    if (Array.isArray(inlineChapters) && inlineChapters.length > 0) {
      chapters.value = inlineChapters
    } else {
      chapters.value = await getChapterList(courseId)
    }
    try {
      progressList.value = await getProgress(courseId)
    } catch (e) {
      progressList.value = []
    }
    mergeProgress()
    // P2-8 修复：校验报名状态（后端 /course/detail 若返回 enrolled 优先用，否则用 enrollment 检查结果）
    if (course.value && course.value.enrolled !== undefined) {
      enrolled.value = !!course.value.enrolled
    } else {
      enrolled.value = await checkEnrolled()
    }
    // 默认选中第一个未完成的章节
    const firstUnfinished = chapters.value.find((c) => !c.finished)
    activeChapterId.value = firstUnfinished?.id ?? chapters.value[0]?.id
  } catch (e) {
    ElMessage.warning('课程加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * P2-8 修复：兜底校验当前用户是否已报名（后端 /course/detail 未返回 enrolled 时使用）
 * 避免直接 URL 跳过报名进入学习
 */
async function checkEnrolled() {
  try {
    const { getMyCourses } = await import('@/api/study')
    const res = await getMyCourses({ pageNum: 1, pageSize: 200 })
    const list = res?.records || res?.list || res || []
    return Array.isArray(list) && list.some((c) => String(c.id) === String(courseId))
  } catch (e) {
    return false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.course-learn-page {
  min-height: calc(100vh - 140px);
}
.course-learn-page__row {
  height: 100%;
}
.course-learn-page__sidebar,
.course-learn-page__content {
  height: calc(100vh - 140px);
  overflow-y: auto;
}
.course-learn-page__course-title {
  font-size: 15px;
  font-weight: 500;
}
.course-learn-page__menu {
  border-right: none;
}
.course-learn-page__menu-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 4px;
}
.course-learn-page__check {
  margin-left: auto;
}
.course-learn-page__chapter-title {
  margin: 0;
  font-size: 18px;
  color: #303133;
}
.chapter-content {
  min-height: 400px;
}
.chapter-content__video {
  width: 100%;
  max-height: 500px;
  background: #000;
  border-radius: 4px;
}
.chapter-content__pdf {
  width: 100%;
  height: 600px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.chapter-content__text {
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
}
.course-learn-page__footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
