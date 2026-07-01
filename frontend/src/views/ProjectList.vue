<template>
  <div class="project-list">
    <div class="page-header">
      <el-button type="primary" @click="showImportDialog">
        <el-icon><Plus /></el-icon>
        导入项目
      </el-button>
    </div>

    <el-row :gutter="20" v-loading="loading">
      <el-col :span="8" v-for="project in projects" :key="project.id">
        <el-card class="project-card" @click="goToProject(project.id)">
          <template #header>
            <div class="card-header">
              <span class="project-name">{{ project.name }}</span>
              <el-tag :type="getStatusType(project.status)">{{ getStatusText(project.status) }}</el-tag>
            </div>
          </template>
          <div class="project-info">
            <p><strong>Git URL:</strong> {{ project.gitUrl }}</p>
            <p><strong>分支:</strong> {{ project.branch || 'main' }}</p>
            <p><strong>语言:</strong> {{ project.language || 'Java' }}</p>
            <p><strong>创建时间:</strong> {{ formatDate(project.createdAt) }}</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Import Dialog -->
    <el-dialog v-model="importDialogVisible" title="导入项目" width="500px">
      <el-form :model="importForm" :rules="importRules" ref="importFormRef" label-width="80px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="importForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="Git URL" prop="gitUrl">
          <el-input v-model="importForm.gitUrl" placeholder="https://gitlab.example.com/group/project.git" />
        </el-form-item>
        <el-form-item label="分支">
          <el-input v-model="importForm.branch" placeholder="main" />
        </el-form-item>
        <el-form-item label="语言">
          <el-select v-model="importForm.language" placeholder="选择语言">
            <el-option label="Java" value="java" />
            <el-option label="Vue/JavaScript" value="javascript" />
            <el-option label="Python" value="python" />
          </el-select>
        </el-form-item>
        <el-form-item label="解析范围">
          <el-input v-model="importForm.parseScope" placeholder="src/main/java" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport" :loading="importLoading">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getProjects, createProject } from '@/api/project'

const router = useRouter()

const loading = ref(false)
const importLoading = ref(false)
const importDialogVisible = ref(false)
const importFormRef = ref()

const projects = ref<any[]>([])

const importForm = reactive({
  name: '',
  gitUrl: '',
  branch: 'main',
  language: 'java',
  parseScope: 'src/main/java'
})

const importRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  gitUrl: [{ required: true, message: '请输入 Git URL', trigger: 'blur' }]
}

onMounted(async () => {
  await loadProjects()
})

async function loadProjects() {
  loading.value = true
  try {
    const res = await getProjects()
    if (res.success) {
      projects.value = res.data || []
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载项目失败')
  } finally {
    loading.value = false
  }
}

function showImportDialog() {
  importDialogVisible.value = true
}

async function handleImport() {
  await importFormRef.value?.validate()
  importLoading.value = true
  try {
    const res = await createProject(importForm) as any
    if (res.success) {
      ElMessage.success('项目导入成功')
      importDialogVisible.value = false
      await loadProjects()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

function goToProject(id: number) {
  router.push(`/project/${id}`)
}

function getStatusType(status: string) {
  const types: Record<string, any> = {
    PENDING: 'info',
    PARSING: 'warning',
    READY: 'success',
    ERROR: 'danger',
    NO_SOURCE: 'info'
  }
  return types[status] || 'info'
}

function getStatusText(status: string) {
  const texts: Record<string, string> = {
    PENDING: '待解析',
    PARSING: '解析中',
    READY: '可用',
    ERROR: '解析失败',
    NO_SOURCE: '无代码'
  }
  return texts[status] || status
}

function formatDate(date: string) {
  if (!date) return ''
  return new Date(date).toLocaleString('zh-CN')
}
</script>

<style scoped>
.project-list {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.project-card {
  cursor: pointer;
  transition: all 0.3s;
}

.project-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.project-name {
  font-weight: bold;
}

.project-info p {
  margin: 8px 0;
  color: #666;
}
</style>