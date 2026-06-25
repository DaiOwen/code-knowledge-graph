<template>
  <div class="file-viewer">
    <!-- File tree sidebar -->
    <div class="file-tree-panel" :class="{ collapsed: treeCollapsed }">
      <div class="panel-header">
        <span v-if="!treeCollapsed">文件列表</span>
        <el-button link @click="treeCollapsed = !treeCollapsed">
          <el-icon><Fold v-if="!treeCollapsed" /><Expand v-else /></el-icon>
        </el-button>
      </div>

      <div v-if="!treeCollapsed" class="file-tree">
        <el-input
          v-model="fileSearchQuery"
          placeholder="搜索文件..."
          prefix-icon="Search"
          size="small"
          class="file-search"
        />

        <el-tree
          ref="treeRef"
          :data="fileTreeData"
          :props="{ label: 'name', children: 'children' }"
          :filter-node-method="filterFileNode"
          highlight-current
          @node-click="handleFileClick"
        >
          <template #default="{ node, data }">
            <span class="file-node">
              <el-icon v-if="data.isFolder"><Folder /></el-icon>
              <el-icon v-else><Document /></el-icon>
              <span>{{ node.label }}</span>
            </span>
          </template>
        </el-tree>
      </div>
    </div>

    <!-- Code viewer -->
    <div class="code-panel">
      <div v-if="!currentFile" class="empty-state">
        <el-empty description="选择一个文件查看代码" />
      </div>

      <template v-else>
        <!-- File header -->
        <div class="file-header">
          <div class="file-info">
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ currentFile.path }}</span>
            <el-tag size="small">{{ currentFile.language }}</el-tag>
            <span class="file-lines">{{ currentFile.totalLines }} 行</span>
          </div>

          <div class="file-actions">
            <el-button-group>
              <el-button size="small" @click="copyCode">
                <el-icon><CopyDocument /></el-icon> 复制
              </el-button>
              <el-button size="small" @click="downloadFile">
                <el-icon><Download /></el-icon> 下载
              </el-button>
            </el-button-group>

            <el-dropdown trigger="click">
              <el-button size="small">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="viewHistory">查看历史</el-dropdown-item>
                  <el-dropdown-item @click="openInNewTab">新标签页打开</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <!-- Monaco Editor -->
        <div class="editor-container" ref="editorContainer">
          <div v-if="loading" class="editor-loading">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          </div>
        </div>

        <!-- Jump to line -->
        <div class="jump-line">
          <el-input-number
            v-model="jumpLineNumber"
            :min="1"
            :max="currentFile.totalLines"
            size="small"
            placeholder="跳转到行"
          />
          <el-button size="small" @click="jumpToLine">跳转</el-button>
        </div>
      </template>
    </div>

    <!-- History dialog -->
    <el-dialog v-model="showHistoryDialog" title="文件历史" width="600px">
      <el-table :data="fileHistory" v-loading="historyLoading">
        <el-table-column prop="hash" label="Commit" width="100">
          <template #default="{ row }">
            <el-link type="primary">{{ row.hash.substring(0, 8) }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="Message" />
        <el-table-column prop="author" label="Author" width="100" />
        <el-table-column prop="date" label="Date" width="150">
          <template #default="{ row }">
            {{ formatDate(row.date) }}
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="80">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewVersion(row.hash)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Fold, Expand, Folder, Document, CopyDocument, Download,
  MoreFilled, Loading
} from '@element-plus/icons-vue'
import * as monaco from 'monaco-editor'
import type { FileInfo, FileContentResult } from '@/api/file'
import { getFileContent, getFileTree } from '@/api/file'

const route = useRoute()
const projectId = computed(() => Number(route.params.id))

// Refs
const editorContainer = ref<HTMLElement>()
const treeRef = ref()

// State
const treeCollapsed = ref(false)
const fileSearchQuery = ref('')
const files = ref<FileInfo[]>([])
const currentFile = ref<FileContentResult | null>(null)
const loading = ref(false)
const jumpLineNumber = ref(1)
const showHistoryDialog = ref(false)
const historyLoading = ref(false)
const fileHistory = ref<any[]>([])

// Monaco editor instance
let editor: monaco.editor.IStandaloneCodeEditor | null = null

// Computed
const fileTreeData = computed(() => {
  return buildFileTree(files.value)
})

// Language mapping
const languageMap: Record<string, string> = {
  java: 'java',
  js: 'javascript',
  ts: 'typescript',
  vue: 'vue',
  html: 'html',
  css: 'css',
  scss: 'scss',
  json: 'json',
  xml: 'xml',
  yaml: 'yaml',
  yml: 'yaml',
  md: 'markdown',
  sql: 'sql',
  py: 'python',
  go: 'go',
  rs: 'rust',
  c: 'c',
  cpp: 'cpp',
  h: 'c',
  hpp: 'cpp',
  sh: 'shell',
  bash: 'shell'
}

// Methods
async function loadFileTree() {
  try {
    const response = await getFileTree(projectId.value)
    if (response.success) {
      files.value = response.data
    }
  } catch (error: any) {
    ElMessage.error('加载文件列表失败')
  }
}

async function loadFile(path: string, startLine?: number, endLine?: number) {
  loading.value = true
  try {
    const response = await getFileContent(projectId.value, path, {
      startLine,
      endLine
    })
    if (response.success) {
      currentFile.value = response.data
      await nextTick()
      initEditor()

      if (startLine) {
        jumpLineNumber.value = startLine
        jumpToLine()
      }
    }
  } catch (error: any) {
    ElMessage.error('加载文件失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function initEditor() {
  if (!editorContainer.value || !currentFile.value) return

  // Dispose existing editor
  if (editor) {
    editor.dispose()
  }

  const language = languageMap[currentFile.value.language.toLowerCase()] || 'plaintext'

  // Large file optimization
  const isLargeFile = currentFile.value.totalLines > 1000

  editor = monaco.editor.create(editorContainer.value, {
    value: currentFile.value.content,
    language,
    theme: 'vs',
    readOnly: true,
    minimap: { enabled: !isLargeFile },
    foldingMaximumRegions: isLargeFile ? 5000 : undefined,
    largeFileOptimizations: isLargeFile,
    wordWrap: isLargeFile ? 'on' : 'off',
    scrollBeyondLastLine: false,
    lineNumbers: 'on',
    glyphMargin: false,
    lineDecorationsWidth: 10,
    renderLineHighlight: 'line',
    scrollbars: {
      vertical: 'auto',
      horizontal: 'auto'
    },
    fontSize: 14,
    fontFamily: 'Fira Code, Consolas, monospace'
  })

  // Update line count display
  editor.onDidChangeCursorPosition((e) => {
    jumpLineNumber.value = e.position.lineNumber
  })
}

function jumpToLine() {
  if (editor && jumpLineNumber.value > 0) {
    editor.revealLineInCenter(jumpLineNumber.value)
    editor.setPosition({
      lineNumber: jumpLineNumber.value,
      column: 1
    })
    editor.focus()
  }
}

function copyCode() {
  if (currentFile.value) {
    navigator.clipboard.writeText(currentFile.value.content)
    ElMessage.success('代码已复制到剪贴板')
  }
}

function downloadFile() {
  if (currentFile.value) {
    const blob = new Blob([currentFile.value.content], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = currentFile.value.path.split('/').pop() || 'file'
    a.click()
    URL.revokeObjectURL(url)
  }
}

async function viewHistory() {
  showHistoryDialog.value = true
  historyLoading.value = true

  // TODO: Implement git history API call
  setTimeout(() => {
    fileHistory.value = []
    historyLoading.value = false
  }, 500)
}

function viewVersion(hash: string) {
  // TODO: Load file at specific commit
  console.log('View version:', hash)
}

function openInNewTab() {
  // TODO: Open file in new browser tab
  console.log('Open in new tab')
}

function handleFileClick(data: any) {
  if (!data.isFolder && data.path) {
    loadFile(data.path)
  }
}

function filterFileNode(value: string, data: any) {
  if (!value) return true
  return data.name.toLowerCase().includes(value.toLowerCase())
}

function formatDate(date: string) {
  return new Date(date).toLocaleString('zh-CN')
}

function buildFileTree(fileList: FileInfo[]): any[] {
  const root: any[] = []
  const map = new Map<string, any>()

  // Sort files by path
  const sorted = [...fileList].sort((a, b) => a.path.localeCompare(b.path))

  for (const file of sorted) {
    const parts = file.path.split('/')
    let current = root
    let currentPath = ''

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      currentPath += (currentPath ? '/' : '') + part
      const isLast = i === parts.length - 1

      let node = map.get(currentPath)
      if (!node) {
        node = {
          name: part,
          path: isLast ? file.path : currentPath,
          isFolder: !isLast,
          children: isLast ? undefined : [],
          language: isLast ? file.language : undefined,
          totalLines: isLast ? file.totalLines : undefined
        }
        map.set(currentPath, node)
        current.push(node)
      }

      if (!isLast && node.children) {
        current = node.children
      }
    }
  }

  return root
}

// Watch for search query changes
watch(fileSearchQuery, (val) => {
  treeRef.value?.filter(val)
})

// Watch for project ID changes
watch(projectId, () => {
  loadFileTree()
}, { immediate: true })

// Lifecycle
onMounted(() => {
  loadFileTree()
})

onUnmounted(() => {
  if (editor) {
    editor.dispose()
  }
})

// Expose methods for parent components
defineExpose({
  loadFile,
  jumpToLine
})
</script>

<style scoped lang="scss">
.file-viewer {
  height: 100%;
  display: flex;
  background: #fff;
}

.file-tree-panel {
  width: 280px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;

  &.collapsed {
    width: 48px;
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid #e4e7ed;
    font-weight: 500;
  }

  .file-tree {
    flex: 1;
    overflow: auto;
    padding: 8px;

    .file-search {
      margin-bottom: 8px;
    }

    .file-node {
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }
}

.code-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;

  .empty-state {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .file-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid #e4e7ed;
    background: #fafafa;

    .file-info {
      display: flex;
      align-items: center;
      gap: 8px;

      .file-name {
        font-weight: 500;
      }

      .file-lines {
        color: #909399;
        font-size: 12px;
      }
    }
  }

  .editor-container {
    flex: 1;
    min-height: 0;
    position: relative;

    .editor-loading {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(255, 255, 255, 0.8);
      z-index: 10;
    }
  }

  .jump-line {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    border-top: 1px solid #e4e7ed;
    background: #fafafa;
  }
}
</style>
