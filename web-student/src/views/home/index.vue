<template>
  <div class="home-page" v-loading="loading">
    <!-- 欢迎条 -->
    <div class="home-page__welcome">
      <el-icon size="24" color="#1677ff"><UserFilled /></el-icon>
      <span>您好，<strong>{{ userStore.realName }}</strong>！欢迎回来继续学习 📚</span>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="home-page__stats">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card stat-card--blue">
          <div class="stat-card__value">{{ displayedStats.enrolledCount }}</div>
          <div class="stat-card__label">已报名课程数</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card stat-card--green">
          <div class="stat-card__value">{{ displayedStats.completedCount }}</div>
          <div class="stat-card__label">已完成章节数</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card stat-card--orange">
          <div class="stat-card__value">{{ displayedStats.totalHours }}</div>
          <div class="stat-card__label">总学习时长 (h)</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card stat-card--purple">
          <div class="stat-card__value">{{ displayedStats.examCount }}</div>
          <div class="stat-card__label">参加考试数</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 学习趋势图 -->
    <el-card class="home-page__chart" shadow="never">
      <template #header>
        <div class="home-page__chart-header">
          <span class="home-page__section-title">近 7 天学习趋势</span>
          <el-tag v-if="isMockData" size="small" type="info">示例数据</el-tag>
        </div>
      </template>
      <v-chart class="chart" :option="chartOption" autoresize />
    </el-card>

    <el-row :gutter="20" class="home-page__bottom">
      <!-- 继续学习 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="home-page__continue">
          <template #header>
            <span class="home-page__section-title">继续学习</span>
          </template>
          <div v-if="inProgressCourses.length === 0" class="home-page__empty">
            暂无进行中的课程，去
            <router-link to="/courses">课程中心</router-link>
            选课吧～
          </div>
          <div
            v-for="course in inProgressCourses"
            :key="course.id"
            class="continue-item"
          >
            <div class="continue-item__info">
              <div class="continue-item__title">{{ course.title }}</div>
              <el-progress
                :percentage="course.progress ?? 0"
                :stroke-width="8"
              />
            </div>
            <el-button
              type="primary"
              size="small"
              @click="router.push(`/courses/${course.id}/learn`)"
            >
              继续学习
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 推荐课程 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="home-page__recommend">
          <template #header>
            <span class="home-page__section-title">推荐课程</span>
            <el-button
              type="primary"
              link
              size="small"
              style="float: right"
              @click="router.push('/courses')"
            >
              更多
            </el-button>
          </template>
          <el-row :gutter="12">
            <el-col :span="8" v-for="course in recommendCourses" :key="course.id">
              <el-card class="rec-card" shadow="hover" @click="router.push(`/courses/${course.id}`)">
                <div class="rec-card__cover" :style="course.coverUrl ? `background-image:url(${course.coverUrl});background-size:cover;background-position:center` : ''">
                  <el-icon v-if="!course.coverUrl" size="32" color="#1677ff"><VideoPlay /></el-icon>
                </div>
                <div class="rec-card__title">{{ course.title }}</div>
                <el-tag size="small" :type="typeColor(course.type)">
                  {{ typeText(course.type) }}
                </el-tag>
              </el-card>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'
import { useUserStore } from '@/stores/user'
import { getMyStats } from '@/api/stats'
import { getMyCourses, getProgress } from '@/api/study'
import { getCourseList, getChapterList } from '@/api/course'
import { toChartOption } from '@/utils/chart'
import { typeText, typeColor } from '@/utils/dict'
// P3-12 修复：从 utils/format 导入安全格式化函数，避免在视图内重复定义
import { safeNumber } from '@/utils/format'

use([
  CanvasRenderer,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
])

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const stats = ref({})
const myCourses = ref([])
const recommendCourses = ref([])

/**
 * 后端 → 显示字段适配。
 * 后端 /stats/my 返回 enrollCount / completedChapters / totalStudyHours / examCount，
 * 与前端展示名不同，在此统一做一次映射，避免模板里写冗余转换。
 */
const displayedStats = computed(() => ({
  enrolledCount: safeNumber(stats.value.enrollCount),
  completedCount: safeNumber(stats.value.completedChapters),
  totalHours: safeNumber(stats.value.totalStudyHours),
  examCount: safeNumber(stats.value.examCount),
}))

// 进行中的课程（进度 < 100%）
const inProgressCourses = computed(() =>
  myCourses.value.filter((c) => (c.progress ?? 0) < 100).slice(0, 5)
)

// 趋势图：后端 recent7Days → 适配为 trendData → toChartOption（真实优先，mock 兜底）
const chartData = computed(() => ({ trendData: stats.value.recent7Days }))
const chartOption = computed(() => toChartOption(chartData.value, { unit: '分钟' }))
const isMockData = computed(() => chartOption.value.__isMock === true)

async function fetchData() {
  loading.value = true
  try {
    // 三个接口各自独立兜底，单个失败不影响其他，绝不抛异常
    if (userStore.userId) {
      try {
        // P2-11 修复：不再传 studentId，后端从 request attribute 取 userId
        stats.value = await getMyStats()
      } catch (e) {
        stats.value = {}
      }
    }
    try {
      const res = await getMyCourses({ pageNum: 1, pageSize: 20 })
      const list = res?.records || res?.list || res || []
      // ISSUE-001 修复：后端 /study/my-courses 仅返回 Course 实体，无 progress 字段，
      // 导致首页"继续学习"进度条全部显示 0%。这里对每门课程并行拉取
      // 章节列表 + 学习进度，按"已完成章节 / 总章节"算出真实进度，
      // 与课程学习页 /courses/:id/learn 的 totalProgress 计算口径保持一致。
      myCourses.value = await Promise.all(
        list.map(async (course) => {
          try {
            const [chapters, progressList] = await Promise.all([
              getChapterList(course.id).catch(() => []),
              getProgress(course.id).catch(() => []),
            ])
            const total = Array.isArray(chapters) ? chapters.length : 0
            const done = Array.isArray(progressList)
              ? progressList.filter(
                  (p) => p?.completed === 1 || p?.completed === true
                ).length
              : 0
            return {
              ...course,
              progress: total > 0 ? Math.round((done / total) * 100) : 0,
            }
          } catch (e) {
            return { ...course, progress: 0 }
          }
        })
      )
    } catch (e) {
      myCourses.value = []
    }
    try {
      const res = await getCourseList({ pageNum: 1, pageSize: 6 })
      recommendCourses.value = res?.records || res?.list || res || []
    } catch (e) {
      recommendCourses.value = []
    }
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.home-page__welcome {
  background: #fff;
  padding: 16px 20px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  color: #303133;
}
.home-page__stats {
  margin: 0;
}
.stat-card {
  text-align: center;
  color: #fff;
  border: none;
}
.stat-card--blue {
  background: linear-gradient(135deg, #1677ff, #409eff);
}
.stat-card--green {
  background: linear-gradient(135deg, #67c23a, #95d475);
}
.stat-card--orange {
  background: linear-gradient(135deg, #e6a23c, #eebe77);
}
.stat-card--purple {
  background: linear-gradient(135deg, #9c27b0, #ba68c8);
}
.stat-card__value {
  font-size: 32px;
  font-weight: bold;
}
.stat-card__label {
  font-size: 14px;
  margin-top: 4px;
  opacity: 0.9;
}
.home-page__section-title {
  font-size: 16px;
  font-weight: 500;
}
.home-page__chart {
  background: #fff;
}
.home-page__chart-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chart {
  height: 280px;
  width: 100%;
}
.home-page__bottom {
  margin: 0;
}
.home-page__empty {
  color: #909399;
  text-align: center;
  padding: 20px 0;
}
.continue-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
}
.continue-item:last-child {
  border-bottom: none;
}
.continue-item__info {
  flex: 1;
}
.continue-item__title {
  margin-bottom: 6px;
  font-size: 14px;
  color: #303133;
}
.rec-card {
  cursor: pointer;
  margin-bottom: 12px;
  text-align: center;
}
.rec-card__cover {
  height: 80px;
  background: #f0f7ff;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}
.rec-card__title {
  font-size: 13px;
  color: #303133;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
