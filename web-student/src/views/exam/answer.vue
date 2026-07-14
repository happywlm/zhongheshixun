<template>
  <div class="exam-answer-page">
    <!-- 顶部固定条 -->
    <el-card shadow="never" class="exam-answer-page__header">
      <div class="exam-answer-page__header-inner">
        <h2 class="exam-answer-page__title">{{ examData.title || '考试中' }}</h2>
        <div class="exam-answer-page__timer" :class="{ 'is-danger': remaining < 60 }">
          <el-icon><Timer /></el-icon>
          <span>剩余时间：{{ formattedTime }}</span>
        </div>
      </div>
    </el-card>

    <!-- 题目区 -->
    <div class="exam-answer-page__body" v-loading="loading">
      <el-card
        v-for="(q, idx) in questions"
        :key="q.id"
        shadow="never"
        class="question-card"
      >
        <div class="question-card__header">
          <span class="question-card__idx">第 {{ idx + 1 }} 题</span>
          <el-tag size="small" :type="questionTypeColor(q.questionType)">
            {{ questionTypeText(q.questionType) }}
          </el-tag>
          <span class="question-card__score">({{ q.score ?? '-' }} 分)</span>
        </div>
        <div class="question-card__stem">{{ q.title }}</div>

        <!-- 单选 / 判断 -->
        <el-radio-group
          v-if="q.questionType === 1 || q.questionType === 3"
          v-model="answers[q.id]"
          class="question-card__options"
        >
          <el-radio
            v-for="(opt, oidx) in parseOptions(q.options)"
            :key="oidx"
            :label="opt.label"
          >
            {{ opt.label }}. {{ opt.text }}
          </el-radio>
        </el-radio-group>

        <!-- 多选 -->
        <el-checkbox-group
          v-else-if="q.questionType === 2"
          v-model="answers[q.id]"
          class="question-card__options"
        >
          <el-checkbox
            v-for="(opt, oidx) in parseOptions(q.options)"
            :key="oidx"
            :label="opt.label"
          >
            {{ opt.label }}. {{ opt.text }}
          </el-checkbox>
        </el-checkbox-group>

        <!-- 填空 -->
        <el-input
          v-else
          v-model="answers[q.id]"
          placeholder="请输入答案"
          class="question-card__input"
        />
      </el-card>
    </div>

    <!-- 底部交卷 -->
    <el-card shadow="never" class="exam-answer-page__footer">
      <el-button
        type="primary"
        size="large"
        :loading="submitting"
        :disabled="submitting"
        @click="handleSubmit"
      >
        交卷
      </el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { startExam, submitExam } from '@/api/exam'
import { questionTypeText, questionTypeColor, parseOptions } from '@/utils/dict'

const route = useRoute()
const router = useRouter()
const examId = route.params.id

// 记录进入考试时刻的时间戳（与 examRecord.start_time 同步语义），用于后端交叉校验
const clientStartTime = Date.now()

const loading = ref(false)
const submitting = ref(false)
const examData = ref({})
const questions = ref([])
const answers = ref({}) // { questionId: answer }

// 倒计时 — P2-9 修复：基于"结束时间戳"计算剩余秒数，避免浏览器节流 setInterval 导致标签页切走时计时不准
const remaining = ref(0) // 秒
let endTimestamp = 0 // 考试结束时刻的绝对时间戳（ms），由 startTime + duration*1000 推导
let timer = null

const formattedTime = computed(() => {
  const m = Math.floor(remaining.value / 60)
  const s = remaining.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

/**
 * 启动倒计时（基于"结束时间戳"模式）
 *
 * 解决：setInterval 在标签页切走后会被浏览器节流（最低 1Hz 甚至更低），
 * 导致原本"每秒 -1"在恢复时跳过若干秒，剩余时间虚高。
 *
 * 修复策略：
 *   - 计算 endTimestamp = Date.now() + duration*1000（或者服务端 startTime + duration*1000）
 *   - setInterval 仅做"触发刷新"，实际 remaining = max(0, endTimestamp - Date.now()) / 1000
 *   - 切回标签页时下一 tick 自动按真实时间差补正
 */
function startCountdown(durationSec) {
  endTimestamp = Date.now() + durationSec * 1000
  remaining.value = durationSec
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    const ms = endTimestamp - Date.now()
    const sec = Math.max(0, Math.ceil(ms / 1000))
    remaining.value = sec
    if (sec <= 0) {
      clearInterval(timer)
      timer = null
      ElMessageBox.alert('考试时间到，系统将自动交卷', '提示', {
        type: 'warning',
        callback: () => doSubmit(),
      })
    }
  }, 1000)
}

async function fetchExam() {
  loading.value = true
  try {
    const data = await startExam(examId)
    examData.value = data
    questions.value = data.questions || []
    if (questions.value.length === 0) {
      ElMessageBox.alert('该考试暂无题目，请稍后再来', '提示', {
        type: 'warning',
        callback: () => router.replace('/exams'),
      })
      return
    }
    // 初始化答案
    questions.value.forEach((q) => {
      answers.value[q.id] = q.questionType === 2 ? [] : ''
    })
    // 启动倒计时（duration 分钟 → 秒）
    const durationSec = (data.duration || 60) * 60
    startCountdown(durationSec)
  } catch (e) {
    // 业务错误（如题库不足 code=1000）已由拦截器提示
    const msg = e?.message || '考试加载失败'
    ElMessageBox.alert(msg, '提示', {
      type: 'warning',
      callback: () => router.replace('/exams'),
    })
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (submitting.value) return
  const unanswered = questions.value.length - Object.values(answers.value).filter(
    (v) => v && (Array.isArray(v) ? v.length > 0 : String(v).trim())
  ).length
  const msg = unanswered > 0
    ? `还有 ${unanswered} 题未作答，确定要交卷吗？`
    : '确定要交卷吗？'
  await ElMessageBox.confirm(msg, '提示', { type: 'warning' })
  await doSubmit()
}

async function doSubmit() {
  if (submitting.value) return // 防重复提交
  if (timer) clearInterval(timer)
  submitting.value = true
  try {
    const payload = {
      examId,
      clientStartTime,
      clientEndTime: Date.now(),
      answers: questions.value.map((q) => ({
        questionId: q.id,
        answer: Array.isArray(answers.value[q.id])
          ? answers.value[q.id].join('')
          : answers.value[q.id] || '',
      })),
    }
    const result = await submitExam(payload)
    ElMessage.success('交卷成功！')
    // 把判分结果写入 sessionStorage，避免 URL query 被篡改
    sessionStorage.setItem(
      'exam:lastResult',
      JSON.stringify({
        score: result?.score ?? 0,
        totalScore: result?.totalScore ?? 0,
        passed: result?.passed ?? false,
        correctCount: result?.correctCount ?? 0,
        wrongCount: result?.wrongCount ?? 0,
        unansweredCount: result?.unansweredCount ?? 0,
        examTitle: examData.value?.title || '',
      })
    )
    router.replace(`/exams/${examId}/result`)
  } catch (e) {
    // 错误已由拦截器处理
    submitting.value = false
  }
}

// 路由离开守卫：考试进行中切换页面时提示
onBeforeRouteLeave(async (to, from, next) => {
  // 先清理计时器，避免 keep-alive / 路由复用时 setInterval 泄漏
  if (timer) { clearInterval(timer); timer = null; }
  if (submitting.value) {
    // 已交卷，直接放行
    next()
    return
  }
  try {
    await ElMessageBox.confirm(
      '考试进行中，离开页面将不保存当前答题进度（但考试记录仍保留，可从列表"继续考试"重新进入）。确定要离开吗？',
      '提示',
      { type: 'warning', confirmButtonText: '确定离开', cancelButtonText: '继续考试' }
    )
    next()
  } catch (e) {
    next(false)
  }
})

onMounted(fetchExam)
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.exam-answer-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.exam-answer-page__header {
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 10;
}
.exam-answer-page__header-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.exam-answer-page__title {
  margin: 0;
  font-size: 18px;
  color: #303133;
}
.exam-answer-page__timer {
  font-size: 18px;
  color: #1677ff;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 6px;
}
.exam-answer-page__timer.is-danger {
  color: #f56c6c;
  animation: blink 1s infinite;
}
@keyframes blink {
  50% { opacity: 0.5; }
}
.exam-answer-page__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.question-card {
  background: #fff;
}
.question-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.question-card__idx {
  font-weight: 500;
  color: #303133;
}
.question-card__score {
  color: #909399;
  font-size: 12px;
}
.question-card__stem {
  font-size: 15px;
  color: #303133;
  margin-bottom: 16px;
  line-height: 1.6;
}
.question-card__options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-left: 8px;
}
.question-card__input {
  max-width: 400px;
}
.exam-answer-page__footer {
  background: #fff;
  text-align: center;
  position: sticky;
  bottom: 0;
}
</style>
