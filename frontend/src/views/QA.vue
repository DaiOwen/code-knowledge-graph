<template>
  <div class="qa-page">
    <!-- Session sidebar -->
    <div class="session-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <span v-if="!sidebarCollapsed">对话历史</span>
        <el-button link @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon><Fold v-if="!sidebarCollapsed" /><Expand v-else /></el-icon>
        </el-button>
      </div>

      <div v-if="!sidebarCollapsed" class="sidebar-content">
        <el-button type="primary" class="new-session-btn" @click="createNewSession">
          <el-icon><Plus /></el-icon> 新对话
        </el-button>

        <div class="session-list">
          <div
            v-for="session in sessions"
            :key="session.id"
            :class="['session-item', { active: currentSessionId === session.id }]"
            @click="selectSession(session.id)"
          >
            <el-icon><ChatDotRound /></el-icon>
            <span class="session-title">{{ session.title }}</span>
            <el-dropdown trigger="click" @command="handleSessionAction($event, session.id)">
              <el-button link size="small" @click.stop>
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delete">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>

    <!-- Chat area -->
    <div class="chat-area">
      <!-- Messages -->
      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0" class="welcome-message">
          <el-icon :size="64" color="#409eff"><ChatDotRound /></el-icon>
          <h2>代码知识图谱问答</h2>
          <p>您可以问我关于代码的问题，例如：</p>
          <div class="suggestions">
            <el-tag
              v-for="suggestion in suggestions"
              :key="suggestion"
              class="suggestion-tag"
              @click="askSuggestion(suggestion)"
            >
              {{ suggestion }}
            </el-tag>
          </div>
        </div>

        <div v-else class="messages-list">
          <div
            v-for="message in messages"
            :key="message.id"
            :class="['message', message.role.toLowerCase()]"
          >
            <div class="message-avatar">
              <el-avatar v-if="message.role === 'USER'" :size="32">
                {{ userStore.username?.charAt(0)?.toUpperCase() || 'U' }}
              </el-avatar>
              <el-avatar v-else :size="32" class="ai-avatar">
                <el-icon><Cpu /></el-icon>
              </el-avatar>
            </div>

            <div class="message-content">
              <div class="message-header">
                <span class="message-role">
                  {{ message.role === 'USER' ? '你' : 'AI 助手' }}
                </span>
                <span class="message-time">{{ formatTime(message.createdAt) }}</span>
              </div>

              <div class="message-body">
                <template v-if="message.role === 'ASSISTANT'">
                  <div class="markdown-content" v-html="renderMarkdown(message.content)"></div>

                  <!-- Citations -->
                  <div v-if="message.citations && parseCitations(message.citations).length > 0" class="citations">
                    <div class="citations-header">
                      <el-icon><Link /></el-icon> 引用来源
                    </div>
                    <div class="citations-list">
                      <div
                        v-for="(citation, index) in parseCitations(message.citations)"
                        :key="index"
                        class="citation-item"
                        @click="goToCitation(citation)"
                      >
                        <el-icon><Document /></el-icon>
                        <span class="citation-file">{{ citation.filePath }}</span>
                        <span v-if="citation.line" class="citation-line">:{{ citation.line }}</span>
                      </div>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <div class="user-message">{{ message.content }}</div>
                </template>
              </div>
            </div>
          </div>

          <div v-if="loading" class="message assistant">
            <div class="message-avatar">
              <el-avatar :size="32" class="ai-avatar">
                <el-icon><Cpu /></el-icon>
              </el-avatar>
            </div>
            <div class="message-content">
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Input area -->
      <div class="input-area">
        <el-input
          v-model="inputQuestion"
          type="textarea"
          :rows="3"
          placeholder="输入您的问题..."
          :disabled="loading"
          @keydown.enter.ctrl="submitQuestion"
        />
        <div class="input-actions">
          <span class="input-hint">Ctrl + Enter 发送</span>
          <el-button
            type="primary"
            :loading="loading"
            :disabled="!inputQuestion.trim()"
            @click="submitQuestion"
          >
            <el-icon><Promotion /></el-icon> 发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Fold, Expand, Plus, ChatDotRound, MoreFilled, Cpu,
  Document, Link, Promotion
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import { useUserStore } from '@/stores/user'
import type { ChatSession, ChatMessage } from '@/api/qa'
import { askQuestion, getChatSessions, getSessionMessages, createSession, deleteSession } from '@/api/qa'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const projectId = computed(() => Number(route.params.id))

// Refs
const messagesContainer = ref<HTMLElement>()

// State
const sidebarCollapsed = ref(false)
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])
const inputQuestion = ref('')
const loading = ref(false)

// Suggestions
const suggestions = [
  '谁调用了 createOrder 方法？',
  '修改 validateOrder 会影响什么？',
  'OrderService 类有哪些方法？',
  'checkStock 方法是谁写的？'
]

// Methods
async function loadSessions() {
  try {
    const response = await getChatSessions(projectId.value)
    if (response.success) {
      sessions.value = response.data
    }
  } catch (error) {
    console.error('Load sessions failed:', error)
  }
}

async function loadMessages(sessionId: number) {
  try {
    const response = await getSessionMessages(sessionId)
    if (response.success) {
      messages.value = response.data
      scrollToBottom()
    }
  } catch (error) {
    console.error('Load messages failed:', error)
  }
}

async function createNewSession() {
  try {
    const response = await createSession(projectId.value)
    if (response.success) {
      sessions.value.unshift(response.data)
      currentSessionId.value = response.data.id
      messages.value = []
    }
  } catch (error) {
    ElMessage.error('创建对话失败')
  }
}

function selectSession(sessionId: number) {
  currentSessionId.value = sessionId
  loadMessages(sessionId)
}

async function handleSessionAction(action: string, sessionId: number) {
  if (action === 'delete') {
    try {
      await deleteSession(sessionId)
      sessions.value = sessions.value.filter(s => s.id !== sessionId)
      if (currentSessionId.value === sessionId) {
        currentSessionId.value = null
        messages.value = []
      }
      ElMessage.success('对话已删除')
    } catch (error) {
      ElMessage.error('删除失败')
    }
  }
}

async function submitQuestion() {
  const question = inputQuestion.value.trim()
  if (!question || loading.value) return

  // Add user message immediately
  const userMessage: ChatMessage = {
    id: Date.now(),
    role: 'USER',
    content: question,
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMessage)
  inputQuestion.value = ''

  loading.value = true
  scrollToBottom()

  try {
    const response = await askQuestion({
      projectId: projectId.value,
      question,
      sessionId: currentSessionId.value || undefined
    })

    if (response.success) {
      const assistantMessage: ChatMessage = {
        id: Date.now() + 1,
        role: 'ASSISTANT',
        content: response.data.answer,
        citations: JSON.stringify(response.data.citations),
        createdAt: new Date().toISOString()
      }
      messages.value.push(assistantMessage)

      // Update session ID if new
      if (!currentSessionId.value && response.data.sessionId) {
        currentSessionId.value = response.data.sessionId
        loadSessions()
      }
    }
  } catch (error: any) {
    ElMessage.error('提问失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function askSuggestion(suggestion: string) {
  inputQuestion.value = suggestion
  submitQuestion()
}

function renderMarkdown(content: string) {
  return marked(content, {
    breaks: true,
    gfm: true
  })
}

function parseCitations(citationsJson: string) {
  try {
    return JSON.parse(citationsJson)
  } catch {
    return []
  }
}

function goToCitation(citation: { filePath: string; line: number }) {
  // Navigate to file viewer with line highlighted
  router.push({
    path: `/project/${projectId.value}/files`,
    query: { path: citation.filePath, line: citation.line }
  })
}

function formatTime(dateString: string) {
  const date = new Date(dateString)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// Watch for project ID changes
watch(projectId, () => {
  loadSessions()
}, { immediate: true })

// Lifecycle
onMounted(() => {
  loadSessions()
})
</script>

<style scoped lang="scss">
.qa-page {
  height: 100%;
  display: flex;
  background: #f5f7fa;
}

.session-sidebar {
  width: 280px;
  background: white;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;

  &.collapsed {
    width: 48px;
  }

  .sidebar-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    border-bottom: 1px solid #e4e7ed;
    font-weight: 500;
  }

  .sidebar-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 12px;
    overflow: hidden;

    .new-session-btn {
      margin-bottom: 12px;
    }

    .session-list {
      flex: 1;
      overflow-y: auto;

      .session-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 12px;
        border-radius: 6px;
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
          background: #f5f7fa;
        }

        &.active {
          background: #ecf5ff;
          color: #409eff;
        }

        .session-title {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          font-size: 14px;
        }
      }
    }
  }
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;

  .messages-container {
    flex: 1;
    overflow-y: auto;
    padding: 20px;

    .welcome-message {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      text-align: center;

      h2 {
        margin: 16px 0 8px;
        color: #303133;
      }

      p {
        color: #909399;
        margin-bottom: 16px;
      }

      .suggestions {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        justify-content: center;

        .suggestion-tag {
          cursor: pointer;
          transition: all 0.2s;

          &:hover {
            background: #409eff;
            color: white;
          }
        }
      }
    }

    .messages-list {
      max-width: 900px;
      margin: 0 auto;
    }
  }

  .message {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;

    .message-avatar {
      flex-shrink: 0;

      .ai-avatar {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      }
    }

    .message-content {
      flex: 1;
      min-width: 0;

      .message-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;

        .message-role {
          font-weight: 500;
          color: #303133;
        }

        .message-time {
          font-size: 12px;
          color: #909399;
        }
      }

      .message-body {
        .user-message {
          background: #ecf5ff;
          padding: 12px 16px;
          border-radius: 8px;
          color: #303133;
        }

        .markdown-content {
          line-height: 1.6;

          :deep(code) {
            background: #f5f7fa;
            padding: 2px 6px;
            border-radius: 4px;
            font-family: 'Fira Code', monospace;
          }

          :deep(pre) {
            background: #282c34;
            padding: 16px;
            border-radius: 8px;
            overflow-x: auto;

            code {
              background: none;
              color: #abb2bf;
            }
          }
        }

        .citations {
          margin-top: 12px;
          padding: 12px;
          background: #fafafa;
          border-radius: 8px;
          border: 1px solid #e4e7ed;

          .citations-header {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 13px;
            font-weight: 500;
            color: #606266;
            margin-bottom: 8px;
          }

          .citations-list {
            display: flex;
            flex-direction: column;
            gap: 6px;

            .citation-item {
              display: flex;
              align-items: center;
              gap: 6px;
              font-size: 13px;
              color: #409eff;
              cursor: pointer;

              &:hover {
                text-decoration: underline;
              }

              .citation-file {
                font-family: 'Fira Code', monospace;
              }

              .citation-line {
                color: #909399;
              }
            }
          }
        }
      }
    }

    &.assistant {
      .message-content {
        .message-body {
          background: white;
          padding: 16px;
          border-radius: 8px;
          border: 1px solid #e4e7ed;
        }
      }
    }
  }

  .typing-indicator {
    display: flex;
    gap: 4px;
    padding: 8px 0;

    span {
      width: 8px;
      height: 8px;
      background: #909399;
      border-radius: 50%;
      animation: bounce 1.4s infinite ease-in-out;

      &:nth-child(1) { animation-delay: -0.32s; }
      &:nth-child(2) { animation-delay: -0.16s; }
    }
  }

  @keyframes bounce {
    0%, 80%, 100% { transform: scale(0); }
    40% { transform: scale(1); }
  }
}

.input-area {
  padding: 16px 20px;
  background: white;
  border-top: 1px solid #e4e7ed;

  .input-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 12px;

    .input-hint {
      font-size: 12px;
      color: #909399;
    }
  }
}
</style>
