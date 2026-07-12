<template>
  <div class="user-page">
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="角色">
          <el-select
            v-model="queryForm.role"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option label="管理员" value="admin" />
            <el-option label="教师" value="teacher" />
            <el-option label="学员" value="student" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option label="禁用" :value="0" />
            <el-option label="启用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="queryForm.keyword"
            placeholder="用户名/姓名"
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
          <el-icon><Plus /></el-icon>新增用户
        </el-button>
      </div>

      <el-table v-loading="loading" :data="userList" stripe border>
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="120" align="center" />
        <el-table-column prop="phone" label="手机号" min-width="130" align="center" />
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orgName" label="所属机构" min-width="160" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              link
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
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
      :title="dialogMode === 'create' ? '新增用户' : '编辑用户'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formData.username" placeholder="请输入用户名" maxlength="50" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            maxlength="50"
            show-password
          />
          <div class="form-tip" v-if="dialogMode === 'edit'">留空则不修改密码</div>
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="formData.realName" placeholder="请输入姓名" maxlength="50" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入手机号" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入邮箱" maxlength="100" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="formData.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="管理员" value="admin" />
            <el-option label="教师" value="teacher" />
            <el-option label="学员" value="student" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属机构" prop="orgName">
          <el-input v-model="formData.orgName" placeholder="请输入所属机构" maxlength="100" />
        </el-form-item>
        <el-form-item label="岗位类型" prop="jobType">
          <el-input v-model="formData.jobType" placeholder="请输入岗位类型" maxlength="50" />
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
import { ref, reactive, onMounted } from 'vue'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getUserPage,
  createUser,
  updateUser,
  updateUserStatus,
  deleteUser
} from '@/api/user'

const loading = ref(false)
const submitting = ref(false)
const userList = ref<any[]>([])
const total = ref(0)

const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  role: '',
  keyword: '',
  status: undefined as number | undefined
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const formData = reactive({
  id: undefined as number | undefined,
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  role: '',
  orgName: '',
  jobType: ''
})

const buildFormRules = (isEdit: boolean): FormRules => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度在 3-50 字', trigger: 'blur' }
  ],
  password: isEdit ? [] : [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度在 6-50 字', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
})

const formRules = ref<FormRules>(buildFormRules(false))

const roleText = (role: string) => { const map: Record<string, string> = { admin: '管理员', teacher: '教师', student: '学员' }; return map[(role || '').toLowerCase()] || '-' }

const roleTag = (role: string) => { const map: Record<string, string> = { admin: 'danger', teacher: 'warning', student: 'success' }; return map[(role || '').toLowerCase()] || '' }

async function fetchList() {
  loading.value = true
  try {
    const params: any = {
      pageNum: queryForm.pageNum,
      pageSize: queryForm.pageSize
    }
    if (queryForm.role) params.role = queryForm.role
    if (queryForm.keyword) params.keyword = queryForm.keyword
    if (queryForm.status !== undefined) params.status = queryForm.status

    const res: any = await getUserPage(params)
    if (res.data) {
      userList.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      userList.value = res.records || []
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
  queryForm.role = ''
  queryForm.keyword = ''
  queryForm.status = undefined
  queryForm.pageNum = 1
  fetchList()
}

function resetForm() {
  formData.id = undefined
  formData.username = ''
  formData.password = ''
  formData.realName = ''
  formData.phone = ''
  formData.email = ''
  formData.role = ''
  formData.orgName = ''
  formData.jobType = ''
  formRef.value?.clearValidate()
}

function handleCreate() {
  resetForm()
  dialogMode.value = 'create'
  formRules.value = buildFormRules(false)
  dialogVisible.value = true
}

function handleEdit(row: any) {
  resetForm()
  dialogMode.value = 'edit'
  formRules.value = buildFormRules(true)
  formData.id = row.id
  formData.username = row.username
  formData.realName = row.realName
  formData.phone = row.phone || ''
  formData.email = row.email || ''
  // 后端返回 role_code 为大写（TEACHER/ADMIN/STUDENT），前端 select 选项 value 为小写，需转换以正确回填
  formData.role = (row.role || '').toLowerCase()
  formData.orgName = row.orgName || ''
  formData.jobType = row.jobType || ''
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
        await createUser(data)
        ElMessage.success('新增成功')
      } else {
        const { id, password, ...rest } = formData
        const data: any = { id, ...rest }
        if (password) data.password = password
        await updateUser(data)
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

async function handleToggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  const actionText = newStatus === 1 ? '启用' : '禁用'
  try {
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(`${actionText}成功`)
    fetchList()
  } catch (e) {
    // 错误已在 request 拦截器处理
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(
    `确定删除用户「${row.username}」？删除后不可恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 错误已在 request 拦截器处理
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.user-page {
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

.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
