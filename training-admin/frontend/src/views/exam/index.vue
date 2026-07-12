<template>
  <div class="exam-page">
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
        <el-form-item label="状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已下架" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="queryForm.title"
            placeholder="请输入考试标题"
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
          <el-icon><Plus /></el-icon>新增考试
        </el-button>
      </div>

      <el-table v-loading="loading" :data="examList" stripe border>
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="title" label="考试标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="totalScore" label="总分" width="100" align="center" />
        <el-table-column prop="passScore" label="及格分" width="100" align="center" />
        <el-table-column prop="duration" label="时长(分钟)" width="120" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info">草稿</el-tag>
            <el-tag v-else-if="row.status === 1" type="success">已发布</el-tag>
            <el-tag v-else type="warning">已下架</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="题目数" width="100" align="center">
          <template #default="{ row }">
            {{ questionCount(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="380" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link @click="handleGenerate(row)">自动组卷</el-button>
            <!-- P1-5 修复：草稿态显示"发布"；已发布态显示"下架"；下架态不可再发布 -->
            <el-button
              v-if="row.status === 0"
              type="warning"
              link
              :loading="row.__publishing"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-else-if="row.status === 1"
              type="info"
              link
              :loading="row.__offlining"
              @click="handleOffline(row)"
            >
              下架
            </el-button>
            <el-button
              v-else
              type="warning"
              link
              :loading="row.__republishing"
              @click="handleRepublish(row)"
            >
              重新上架
            </el-button>
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
      :title="dialogMode === 'create' ? '新增考试' : '编辑考试'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="考试标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入考试标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="考试类型" prop="examType">
          <el-radio-group v-model="formData.examType">
            <el-radio :value="1">课程考试</el-radio>
            <el-radio :value="2">计划考试</el-radio>
            <el-radio :value="3">单独考试</el-radio>
          </el-radio-group>
        </el-form-item>
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
        <el-form-item label="总分" prop="totalScore">
          <el-input-number v-model="formData.totalScore" :min="1" :max="999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="及格分" prop="passScore">
          <el-input-number v-model="formData.passScore" :min="1" :max="999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时长(分钟)" prop="duration">
          <el-input-number v-model="formData.duration" :min="1" :max="600" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入考试描述"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 自动组卷弹窗 -->
    <el-dialog
      v-model="generateDialogVisible"
      title="自动组卷"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="目标考试">
          <el-input :value="generateTarget?.title" disabled />
        </el-form-item>
        <el-form-item label="知识点">
          <el-checkbox-group v-model="selectedKnowledgeIds">
            <el-checkbox
              v-for="kp in knowledgeOptions"
              :key="kp.id"
              :value="kp.id"
            >
              {{ kp.title }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerateConfirm">
          生成试卷
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getCoursePage } from '@/api/course'
import {
  getExamPage,
  createExam,
  updateExam,
  deleteExam,
  generateExamPaper,
  publishExam,      // P1-5 修复：发布考试
  offlineExam,      // P1-5 修复：下架考试
  getKnowledgePage
} from '@/api/exam'

const loading = ref(false)
const submitting = ref(false)
const examList = ref<any[]>([])
const total = ref(0)

const courseOptions = ref<any[]>([])
const knowledgeOptions = ref<any[]>([])

const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  title: '',
  status: undefined as number | undefined,
  courseId: undefined as number | undefined
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as number | undefined,
  title: '',
  examType: 1 as number | undefined, // 1课程 2计划 3单独（修复 P1-5：模板 v-model 但 data 缺字段）
  courseId: undefined as number | undefined,
  totalScore: 100,
  passScore: 60,
  duration: 60,
  description: ''
})

const formRules: FormRules = {
  title: [
    { required: true, message: '请输入考试标题', trigger: 'blur' },
    { min: 2, max: 100, message: '标题长度在 2-100 字', trigger: 'blur' }
  ],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  totalScore: [{ required: true, message: '请输入总分', trigger: 'blur' }],
  passScore: [{ required: true, message: '请输入及格分', trigger: 'blur' }],
  duration: [{ required: true, message: '请输入时长', trigger: 'blur' }]
}

const generateDialogVisible = ref(false)
const generateTarget = ref<any>(null)
const selectedKnowledgeIds = ref<number[]>([])
const generating = ref(false)

const questionCount = (row: any) => {
  if (!row.questionIds) return 0
  if (Array.isArray(row.questionIds)) return row.questionIds.length
  try {
    const arr = JSON.parse(row.questionIds)
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return 0
  }
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

async function fetchKnowledgeOptions() {
  try {
    const res: any = await getKnowledgePage({ pageNum: 1, pageSize: 200 })
    if (res.data) {
      knowledgeOptions.value = (res.data.records || []).map((item: any) => ({
        id: item.id,
        title: item.title || item.name
      }))
    } else {
      knowledgeOptions.value = (res.records || []).map((item: any) => ({
        id: item.id,
        title: item.title || item.name
      }))
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
    if (queryForm.title) params.title = queryForm.title
    if (queryForm.status !== undefined) params.status = queryForm.status
    if (queryForm.courseId) params.courseId = queryForm.courseId

    const res: any = await getExamPage(params)
    if (res.data) {
      examList.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      examList.value = res.records || []
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
  queryForm.status = undefined
  queryForm.courseId = undefined
  queryForm.pageNum = 1
  fetchList()
}

function resetForm() {
  formData.id = undefined
  formData.title = ''
  formData.examType = 1 // 修复 P1-5：与 formData 定义保持一致
  formData.courseId = undefined
  formData.totalScore = 100
  formData.passScore = 60
  formData.duration = 60
  formData.description = ''
  formRef.value?.clearValidate()
}

function handleCreate() {
  resetForm()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function handleEdit(row: any) {
  resetForm()
  dialogMode.value = 'edit'
  formData.id = row.id
  formData.title = row.title
  formData.examType = row.examType || 1 // 修复 P1-5：补 examType 回显
  formData.courseId = row.courseId
  formData.totalScore = row.totalScore || 100
  formData.passScore = row.passScore || 60
  formData.duration = row.duration || 60
  formData.description = row.description || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (dialogMode.value === 'create') {
        const { id, ...data } = formData
        await createExam(data)
        ElMessage.success('新增成功')
      } else {
        await updateExam(formData)
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
    `确定删除考试《${row.title}》？删除后不可恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
  try {
    await deleteExam(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 错误已在 request 拦截器处理
  }
}

function handleGenerate(row: any) {
  generateTarget.value = row
  selectedKnowledgeIds.value = []
  generateDialogVisible.value = true
}

async function handleGenerateConfirm() {
  if (!generateTarget.value) return
  if (selectedKnowledgeIds.value.length === 0) {
    ElMessage.warning('请至少选择一个知识点')
    return
  }
  generating.value = true
  try {
    await generateExamPaper({
      examId: generateTarget.value.id,
      knowledgePointIds: selectedKnowledgeIds.value
    })
    ElMessage.success('组卷成功')
    generateDialogVisible.value = false
    fetchList()
  } catch (e) {
    // 错误已在 request 拦截器处理
  } finally {
    generating.value = false
  }
}

/**
 * P1-5 修复：发布考试
 *
 * 交互：先弹窗确认 → 调用 publishExam(id) → 刷新列表。
 * 失败时 ElMessage.error 已由 request 拦截器处理。
 */
async function handlePublish(row: any) {
  await ElMessageBox.confirm(
    `确定发布考试《${row.title}》？发布后学员可在考试中心查看并开始作答。`,
    '发布确认',
    {
      confirmButtonText: '确定发布',
      cancelButtonText: '取消',
      type: 'info',
    }
  )
  row.__publishing = true
  try {
    await publishExam(row.id)
    ElMessage.success('发布成功')
    fetchList()
  } catch (e) {
    // 错误已在 request 拦截器处理
  } finally {
    row.__publishing = false
  }
}

/**
 * P1-5 修复：下架考试
 *
 * 警告：下架后学员端不再可见（listForStudent 仅查 status=1）。
 */
async function handleOffline(row: any) {
  await ElMessageBox.confirm(
    `确定下架考试《${row.title}》？下架后学员端将不可见。`,
    '下架确认',
    {
      confirmButtonText: '确定下架',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
  row.__offlining = true
  try {
    await offlineExam(row.id)
    ElMessage.success('下架成功')
    fetchList()
  } catch (e) {
    // 错误已在 request 拦截器处理
  } finally {
    row.__offlining = false
  }
}

/**
 * M12 修复：下架后重新上架（status=2 → 1）
 *
 * 说明：复用 publishExam 接口，后端 ExamServiceImpl.publish 已放开限制
 * （任意非 1 状态都可切到 1），无需新接口。
 */
async function handleRepublish(row: any) {
  await ElMessageBox.confirm(
    `确定重新上架考试《${row.title}》？上架后学员端将再次可见。`,
    '重新上架确认',
    {
      confirmButtonText: '确定上架',
      cancelButtonText: '取消',
      type: 'info',
    }
  )
  row.__republishing = true
  try {
    await publishExam(row.id)
    ElMessage.success('重新上架成功')
    fetchList()
  } catch (e) {
    // 错误已在 request 拦截器处理
  } finally {
    row.__republishing = false
  }
}

onMounted(() => {
  fetchCourseOptions()
  fetchKnowledgeOptions()
  fetchList()
})
</script>

<style scoped>
.exam-page {
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
</style>
