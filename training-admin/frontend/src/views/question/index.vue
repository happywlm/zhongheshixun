<template>
  <div class="question-page">
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select
            v-model="queryForm.courseId"
            placeholder="全部"
            filterable
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="item in courseOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select
            v-model="queryForm.questionType"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option label="单选题" :value="1" />
            <el-option label="多选题" :value="2" />
            <el-option label="判断题" :value="3" />
            <el-option label="填空题" :value="4" />
            <el-option label="问答题" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select
            v-model="queryForm.difficulty"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option label="简单" :value="1" />
            <el-option label="中等" :value="2" />
            <el-option label="困难" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="queryForm.title"
            placeholder="请输入题干关键词"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作区 -->
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>新增试题
        </el-button>
      </div>

      <el-table v-loading="loading" :data="questionList" stripe border>
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="title" label="题干" min-width="280" show-overflow-tooltip />
        <el-table-column prop="type" label="题型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.questionType)">\n              {{ typeText(row.questionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="difficultyTag(row.difficulty)">
              {{ difficultyText(row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="courseId" label="课程ID" width="100" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryForm.pageNum"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        class="pagination"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 新增/编辑 弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增试题' : '编辑试题'"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" filterable style="width: 100%">
            <el-option
              v-for="item in courseOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="题型" prop="questionType">
          <el-radio-group v-model="formData.questionType">
            <el-radio :value="1">单选题</el-radio>
            <el-radio :value="2">多选题</el-radio>
            <el-radio :value="3">判断题</el-radio>
            <el-radio :value="4">填空题</el-radio>
            <el-radio :value="5">问答题</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-radio-group v-model="formData.difficulty">
            <el-radio :value="1">简单</el-radio>
            <el-radio :value="2">中等</el-radio>
            <el-radio :value="3">困难</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分值" prop="score">
          <el-input-number v-model="formData.score" :min="1" :max="100" :step="1" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">分</span>
        </el-form-item>
        <el-form-item label="题干" prop="title">
          <el-input
            v-model="formData.title"
            type="textarea"
            :rows="3"
            placeholder="请输入题目内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <!-- 单选/多选：选项列表 -->
        <template v-if="formData.questionType === 1 || formData.questionType === 2">
          <el-form-item label="选项" required>
            <div v-for="(opt, idx) in optionList" :key="idx" class="option-row">
              <el-tag class="option-key">{{ opt.key }}</el-tag>
              <el-input v-model="opt.value" placeholder="请输入选项内容" style="flex: 1" />
              <el-button
                type="danger"
                link
                :disabled="optionList.length <= 2"
                @click="removeOption(idx)"
              >
                删除
              </el-button>
            </div>
            <el-button type="primary" link @click="addOption" v-if="optionList.length < 8">
              <el-icon><Plus /></el-icon>添加选项
            </el-button>
          </el-form-item>
          <el-form-item label="标准答案" prop="answer">
            <el-select
              v-if="formData.questionType === 1"
              v-model="formData.answer"
              placeholder="请选择正确选项"
              style="width: 200px"
            >
              <el-option v-for="opt in optionList" :key="opt.key" :label="opt.key" :value="opt.key" />
            </el-select>
            <el-select
              v-else
              v-model="multiAnswer"
              multiple
              placeholder="请选择正确选项（多选）"
              style="width: 300px"
            >
              <el-option v-for="opt in optionList" :key="opt.key" :label="opt.key" :value="opt.key" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 判断：正确/错误 -->
        <el-form-item v-if="formData.questionType === 3" label="标准答案" prop="answer">
          <el-radio-group v-model="formData.answer">
            <el-radio value="正确">正确</el-radio>
            <el-radio value="错误">错误</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 填空：多个答案 | 分隔 -->
        <el-form-item v-if="formData.questionType === 4" label="标准答案" prop="answer">
          <el-input
            v-model="formData.answer"
            type="textarea"
            :rows="2"
            placeholder="多个答案用 | 分隔，如：答案A|答案B"
          />
        </el-form-item>

        <!-- 问答：参考答案 -->
        <el-form-item v-if="formData.questionType === 5" label="参考答案" prop="answer">
          <el-input
            v-model="formData.answer"
            type="textarea"
            :rows="4"
            placeholder="请输入参考答案"
          />
        </el-form-item>

        <el-form-item label="解析" prop="analysis">
          <el-input
            v-model="formData.analysis"
            type="textarea"
            :rows="2"
            placeholder="请输入题目解析（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, nextTick } from 'vue'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getCoursePage } from '@/api/course'
import {
  getQuestionPage,
  createQuestion,
  updateQuestion,
  deleteQuestion,
  getQuestionDetail
} from '@/api/question'

const loading = ref(false)
const submitting = ref(false)
const questionList = ref<any[]>([])
const total = ref(0)

const courseOptions = ref<any[]>([])

const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  courseId: undefined as number | undefined,
  title: '',
  questionType: undefined as number | undefined,
  difficulty: undefined as number | undefined
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as number | undefined,
  courseId: undefined as number | undefined,
  title: '',
  questionType: 1,
  difficulty: 1,
  score: 5,
  answer: '',
  options: '',
  analysis: ''
})
const optionList = ref<{ key: string; value: string }[]>([
  { key: 'A', value: '' },
  { key: 'B', value: '' }
])
const multiAnswer = ref<string[]>([])
// 编辑回填标志位：阻止 watch 在编辑数据回填时清空答案
const isEditLoading = ref(false)

const formRules: FormRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  title: [
    { required: true, message: '请输入题干', trigger: 'blur' },
    { min: 2, max: 500, message: '题干长度在 2-500 字', trigger: 'blur' }
  ]
}

const typeText = (type: number) => {
  const map: Record<number, string> = { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '问答' }
  return map[type] || '-'
}
const typeTag = (type: number) => {
  const map: Record<number, string> = { 1: 'primary', 2: 'warning', 3: 'success', 4: 'info', 5: '' }
  return map[type] || ''
}
const difficultyText = (d: number) => {
  const map: Record<number, string> = { 1: '简单', 2: '中等', 3: '困难' }
  return map[d] || '-'
}
const difficultyTag = (d: number) => {
  const map: Record<number, string> = { 1: 'success', 2: 'warning', 3: 'danger' }
  return map[d] || ''
}

async function fetchCourseOptions() {
  try {
    const res: any = await getCoursePage({ pageNum: 1, pageSize: 200 })
    if (res.data) {
      courseOptions.value = res.data.records || []
    } else {
      courseOptions.value = res.records || []
    }
  } catch (e) {
    // 错误已在 request 拦截器处理
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: any = {
      pageNum: queryForm.pageNum,
      pageSize: queryForm.pageSize
    }
    if (queryForm.courseId) params.courseId = queryForm.courseId
    if (queryForm.title) params.title = queryForm.title
    if (queryForm.questionType !== undefined) params.questionType = queryForm.questionType
    if (queryForm.difficulty !== undefined) params.difficulty = queryForm.difficulty

    const res: any = await getQuestionPage(params)
    if (res.data) {
      questionList.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      questionList.value = res.records || []
      total.value = res.total || 0
    }
  } catch (e) {
    // 错误已在 request 拦截器处理
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryForm.pageNum = 1
  fetchList()
}

function handleReset() {
  queryForm.title = ''
  queryForm.courseId = undefined
  queryForm.questionType = undefined
  queryForm.difficulty = undefined
  queryForm.pageNum = 1
  fetchList()
}

function resetForm() {
  formData.id = undefined
  formData.courseId = undefined
  formData.title = ''
  formData.questionType = 1
  formData.difficulty = 1
  formData.score = 5
  formData.answer = ''
  formData.options = ''
  formData.analysis = ''
  optionList.value = [
    { key: 'A', value: '' },
    { key: 'B', value: '' }
  ]
  multiAnswer.value = []
  formRef.value?.clearValidate()
}

function handleCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

async function handleEdit(row: any) {
  resetForm()
  // 设置标志位，阻止 watch 在 questionType 变化时清空已回填的答案
  isEditLoading.value = true
  dialogMode.value = 'edit'
  try {
    const res: any = await getQuestionDetail(row.id)
    const data = res.data || res
    formData.id = data.id
    formData.courseId = data.courseId
    formData.title = data.title
    formData.questionType = data.questionType
    formData.difficulty = data.difficulty || 1
    formData.score = data.score || 5
    formData.answer = data.answer || ''
    formData.analysis = data.analysis || ''
    // 回填选项
    if (data.options && (data.questionType === 1 || data.questionType === 2)) {
      try {
        const opts = JSON.parse(data.options)
        if (Array.isArray(opts) && opts.length > 0) {
          optionList.value = opts
        }
      } catch (e) {
        // 解析失败保持默认
      }
    }
    if (data.questionType === 2 && data.answer) {
      multiAnswer.value = data.answer.split(',').map((s: string) => s.trim())
    }
    // 等待 watch 触发完毕后再关闭标志位，确保答案不被清空
    await nextTick()
    isEditLoading.value = false
    dialogVisible.value = true
  } catch (e) {
    isEditLoading.value = false
    // 错误已在 request 拦截器处理
  }
}

function addOption() {
  if (optionList.value.length >= 8) return
  const nextKey = String.fromCharCode(65 + optionList.value.length)
  optionList.value.push({ key: nextKey, value: '' })
}

function removeOption(idx: number) {
  if (optionList.value.length <= 2) return
  optionList.value.splice(idx, 1)
  // 重新编号
  optionList.value.forEach((opt, i) => {
    opt.key = String.fromCharCode(65 + i)
  })
}

// 监听题型变化，重置选项和答案（编辑回填时跳过）
watch(() => formData.questionType, (newType) => {
  // 编辑数据回填期间不清空答案，避免覆盖已回填的数据
  if (isEditLoading.value) return
  if (newType === 1 || newType === 2) {
    if (optionList.value.length === 0) {
      optionList.value = [
        { key: 'A', value: '' },
        { key: 'B', value: '' }
      ]
    }
    if (newType === 1) {
      formData.answer = ''
      multiAnswer.value = []
    } else {
      formData.answer = ''
      multiAnswer.value = []
    }
  } else if (newType === 3) {
    formData.answer = '正确'
  } else {
    formData.answer = ''
  }
})

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    // 选项校验
    if (formData.questionType === 1 || formData.questionType === 2) {
      const hasEmpty = optionList.value.some((o) => !o.value.trim())
      if (hasEmpty) {
        ElMessage.warning('请填写所有选项内容')
        return
      }
      if (formData.questionType === 1 && !formData.answer) {
        ElMessage.warning('请选择标准答案')
        return
      }
      formData.options = JSON.stringify(optionList.value)
    }

    submitting.value = true
    try {
      const submitData = { ...formData }
      // 多选题：答案来自 multiAnswer（需先于下面校验写入 submitData.answer，否则校验读到空值）
      if (formData.questionType === 2) {
        if (!multiAnswer.value || multiAnswer.value.length === 0) {
          ElMessage.warning('请选择标准答案')
          submitting.value = false
          return
        }
        submitData.answer = multiAnswer.value.join(',')
        submitData.options = JSON.stringify(optionList.value)
      }
      if (dialogMode.value === 'create') {
        const { id, ...data } = submitData
        await createQuestion(data)
        ElMessage.success('新增成功')
      } else {
        await updateQuestion(submitData)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      fetchList()
    } catch (e) {
      // 错误已在 request 拦截器处理
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(
    `确定删除试题「${row.title.slice(0, 30)}${row.title.length > 30 ? '...' : ''}」？`,
    '删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
  try {
    await deleteQuestion(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 错误已在 request 拦截器处理
  }
}

onMounted(() => {
  fetchCourseOptions()
  fetchList()
})
</script>

<style scoped>
.question-page {
  padding: 16px;
}

.filter-card {
  margin-bottom: 16px;
}

.table-card {
  margin-bottom: 16px;
}

.toolbar {
  margin-bottom: 16px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.option-key {
  width: 36px;
  text-align: center;
  flex-shrink: 0;
}
</style>
