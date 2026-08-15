<template>
  <div class="app">
    <!-- 登录 / 注册遮罩 -->
    <div v-if="!token" class="auth-overlay">
      <div class="auth-card">
        <h2>{{ authMode === 'login' ? '登录' : '注册' }}</h2>
        <form @submit.prevent="doAuth" class="auth-form">
          <input
            v-if="authMode === 'register'"
            v-model="auth.name"
            placeholder="用户名"
            :disabled="authLoading"
          />
          <input
            v-model="auth.email"
            type="email"
            placeholder="邮箱"
            :disabled="authLoading"
          />
          <div class="password-field">
            <input
              v-model="auth.password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="密码（至少 6 位）"
              :disabled="authLoading"
            />
            <!-- 切换密码可见性（type=button 避免触发表单提交） / toggle password visibility (type=button to avoid form submit) -->
            <button
              type="button"
              class="password-toggle"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              :title="showPassword ? '隐藏密码 / hide password' : '显示密码 / show password'"
              :disabled="authLoading"
              @click="showPassword = !showPassword"
            >
              <!-- Unicode emoji 作为图标（避免引入字体图标库） / Unicode emoji as icon (no font-icon dependency) -->
              <span aria-hidden="true">{{ showPassword ? '🙈' : '👁' }}</span>
            </button>
          </div>
          <button class="primary auth-submit" :disabled="authLoading || !canAuth">
            {{ authLoading ? '处理中...' : authMode === 'login' ? '登录' : '注册' }}
          </button>
        </form>
        <div class="auth-switch">
          <button class="text-button" @click="toggleAuthMode">
            {{ authMode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
          </button>
        </div>
        <div v-if="authError" class="panel-error" @click="authError = ''">
          {{ authError }}（点击关闭）
        </div>
      </div>
    </div>

    <!-- 顶栏 -->
    <header class="header">
      <div class="brand">
        <h1>MyAi Chat</h1>
        <p v-if="defaultKey" class="current-key">
          默认 Key：{{ defaultKey.name }} · {{ defaultKey.provider.toUpperCase() }} · {{ defaultKey.modelName }}
        </p>
        <p v-else class="current-key warning">当前用户没有可用的默认 Key</p>
      </div>
      <div class="controls">
        <span v-if="currentUser" class="user-badge">👤 {{ currentUser.name }}</span>
        <button class="secondary" @click="showKeyDrawer = !showKeyDrawer">
          {{ showKeyDrawer ? '收起管理' : '管理 Key' }}
        </button>
        <button class="secondary" @click="logout">退出</button>
      </div>
    </header>

    <div class="workspace">
      <!-- 左侧对话栏 -->
      <aside class="sidebar">
        <button class="primary new-conv" @click="createConversation" :disabled="!defaultKey">
          + 新对话
        </button>
        <div v-if="activeConversations.length === 0" class="sidebar-empty">
          还没有对话
        </div>
        <ul class="conv-list">
          <li
            v-for="conv in activeConversations"
            :key="conv.id"
            :class="['conv-item', { active: conv.id === activeConversationId }]"
            @click="selectConversation(conv.id)"
          >
            <div class="conv-title">{{ conv.title || '新对话' }}</div>
            <div class="conv-actions">
              <button class="text-button danger" @click.stop="deleteConversation(conv)">删</button>
            </div>
          </li>
        </ul>
        <div v-if="deletedConversations.length > 0" class="trash-section">
          <button class="text-button" @click="showTrash = !showTrash">
            {{ showTrash ? '收起' : '展开' }} 已删除 ({{ deletedConversations.length }})
          </button>
          <ul v-if="showTrash" class="conv-list deleted">
            <li v-for="conv in deletedConversations" :key="conv.id" class="conv-item deleted">
              <div class="conv-title">{{ conv.title || '新对话' }}</div>
              <div class="conv-actions">
                <button class="text-button" @click="restoreConversation(conv)">恢复</button>
                <button class="text-button danger" @click="permanentlyDelete(conv)">永久删</button>
              </div>
            </li>
          </ul>
        </div>
      </aside>

      <!-- 主聊天区 -->
      <section class="chat">
        <div v-if="activeConversationId == null" class="empty">
          <template v-if="!defaultKey">请先设置一个启用的默认 Key</template>
          <template v-else>选择左侧对话，或点击"新对话"开始聊天 👋</template>
        </div>

        <main v-else class="messages" ref="messagesRef">
          <div v-if="visibleMessages.length === 0 && !streaming" class="empty">
            <template v-if="!defaultKey">请先设置一个启用的默认 Key</template>
            <template v-else>开始你的第一条消息吧</template>
          </div>

          <article
            v-for="m in visibleMessages"
            :key="m.id ?? `pending-${m.localId}`"
            :class="['message', m.role.toLowerCase(), { orphaned: m.isOrphaned }]"
          >
            <div v-if="editingId === m.id" class="bubble edit-bubble">
              <textarea v-model="editingContent" rows="3"></textarea>
              <div class="edit-actions">
                <button class="primary small" @click="commitEdit(m)">保存</button>
                <button class="text-button" @click="cancelEdit">取消</button>
              </div>
            </div>
            <div v-else class="bubble" v-html="renderMarkdown(m.content)"></div>
            <div class="meta">
              <span v-if="!m.isOrphaned && m.role === 'USER' && editingId !== m.id"
                    class="meta-action" @click="startEdit(m)">编辑</span>
              <span v-if="!m.isOrphaned && m.role === 'ASSISTANT' && !streaming"
                    class="meta-action" @click="regenerateMessage(m)">重新生成</span>
              <span v-if="m.isOrphaned" class="orphan-tag">已作废</span>
              <span class="meta-text">{{ m.role === 'USER' ? '我' : 'AI' }}</span>
            </div>
          </article>

          <div v-if="streaming" class="message assistant streaming">
            <div class="bubble" v-html="renderMarkdown(streamingContent)"></div>
            <div class="meta">{{ defaultKey ? `${defaultKey.name} · ${defaultKey.provider.toUpperCase()}` : 'AI' }}</div>
          </div>
        </main>

        <div v-if="error" class="error" @click="error = ''">
          {{ error }}（点击关闭）
        </div>

        <footer v-if="activeConversationId != null" class="composer">
          <textarea
            v-model="input"
            @keydown.enter.exact.prevent="send"
            :disabled="streaming || !defaultKey"
            :placeholder="defaultKey ? '输入消息，回车发送，Shift+回车换行' : '请先设置默认 Key'"
            rows="1"
            ref="inputRef"
          ></textarea>
          <button v-if="!streaming" class="send" @click="send" :disabled="!canSend">发送</button>
          <button v-else class="send stop" @click="stop">停止</button>
        </footer>
      </section>

      <!-- 右侧 Key 管理 Drawer -->
      <aside v-if="showKeyDrawer" class="key-drawer">
        <section class="panel-section keys-section">
          <div class="section-title">
            <h2>Key 配置</h2>
            <button class="secondary small" :disabled="configLoading" @click="startCreateKey">新增 Key</button>
          </div>
          <div v-if="keys.length === 0" class="panel-empty">还没有 Key，请新增一个</div>
          <div v-else class="key-list">
            <article v-for="key in keys" :key="key.id" class="key-card">
              <div class="key-heading">
                <strong>{{ key.name }}</strong>
                <span v-if="key.defaultKey" class="badge default">默认</span>
                <span :class="['badge', key.enabled ? 'enabled' : 'disabled']">
                  {{ key.enabled ? '启用' : '禁用' }}
                </span>
              </div>
              <div class="key-meta">
                {{ key.provider.toUpperCase() }} · {{ key.protocol || '默认' }} · {{ key.modelName }}
              </div>
              <div class="key-meta">{{ key.maskedApiKey || '无需 API Key' }}</div>
              <div class="key-actions">
                <button class="text-button" @click="startEditKey(key)">编辑</button>
                <button
                  class="text-button"
                  :disabled="key.defaultKey || !key.enabled || configLoading"
                  @click="setDefaultKey(key)"
                >设为默认</button>
                <button class="text-button danger" :disabled="configLoading" @click="deleteKey(key)">删除</button>
              </div>
            </article>
          </div>
        </section>

        <section v-if="showKeyForm" class="panel-section key-form-section">
          <div class="section-title">
            <h2>{{ editingKeyId ? '编辑 Key' : '新增 Key' }}</h2>
            <button class="text-button" @click="cancelKeyForm">取消</button>
          </div>
          <form class="key-form" @submit.prevent="saveKey">
            <label>名称 <input v-model="keyForm.name" :disabled="configLoading" /></label>
            <label>Provider
              <select v-model="keyForm.provider" :disabled="configLoading" @change="applyProviderDefaults">
                <option v-for="provider in providers" :key="provider.name" :value="provider.name">
                  {{ provider.displayName }}
                </option>
              </select>
            </label>
            <label>协议
              <select v-model="keyForm.protocol" :disabled="configLoading">
                <option value="">默认（Provider 决定）</option>
                <option value="OPENAI_COMPATIBLE">OpenAI 兼容</option>
                <option value="ANTHROPIC">Anthropic 兼容</option>
                <option value="OLLAMA">Ollama</option>
              </select>
            </label>
            <label>API Key
              <input v-model="keyForm.apiKey" type="password" autocomplete="new-password"
                :placeholder="editingKeyId ? '留空表示保留原 Key' : 'Ollama 可留空'"
                :disabled="configLoading" />
            </label>
            <label>Base URL <input v-model="keyForm.baseUrl" :disabled="configLoading" /></label>
            <label>模型 <input v-model="keyForm.modelName" :disabled="configLoading" /></label>
            <label class="checkbox-label">
              <input v-model="keyForm.enabled" type="checkbox" :disabled="configLoading" />
              启用该 Key
            </label>
            <button class="primary"
              :disabled="configLoading || !keyForm.name.trim() || !keyForm.baseUrl.trim() || !keyForm.modelName.trim()">
              {{ configLoading ? '保存中...' : '保存配置' }}
            </button>
          </form>
        </section>

        <div v-if="configError" class="panel-error" @click="configError = ''">
          {{ configError }}（点击关闭）
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { renderMarkdown } from './lib/markdown.js'
import {
  FetchHttpClient,
  AuthService,
  ProviderApi,
  UserApiKeyApi,
  ConversationApi,
  MessageApi,
  LocalStorageAdapter,
  createStorage,
  unwrap,
  streamConversationMessage,
  streamRegenerate,
} from '@myai/sdk'

const TOKEN_KEY = 'myai.token'
const ACTIVE_CONV_KEY = 'myai.last_active_conversation_id'
const BROADCAST_CHANNEL = 'my-ai-conversations'

// ============ SDK 单例 / SDK singletons ============
// baseUrl 留空字符串：dev 走 Vite /api 代理；prod 走 Spring Boot 同源 /api。
// baseUrl empty: dev goes through Vite /api proxy; prod goes through Spring Boot same-origin /api.
const sdkStorage = createStorage(new LocalStorageAdapter())
const http = new FetchHttpClient({
  baseUrl: '',
  getToken: () => localStorage.getItem(TOKEN_KEY),
  onUnauthorized: () => {
    // 4010 → 清前端态 + 保留 onMounted 重新登录的逻辑
    // 4010 → clear frontend state; onMounted will re-trigger login on next interaction
    localStorage.removeItem(TOKEN_KEY)
    token.value = null
    currentUser.value = null
  },
})
const authService = new AuthService({ http, storage: sdkStorage })
const providerApi = new ProviderApi(http)
const userApiKeyApi = new UserApiKeyApi(http)
const conversationApi = new ConversationApi(http)
const messageApi = new MessageApi(http)

// ============ 登录态 ============
const token = ref(null)
const currentUser = ref(null)
const authMode = ref('login')
const authLoading = ref(false)
const authError = ref('')
// 密码框可见性开关 / password visibility toggle
const showPassword = ref(false)
const auth = ref({ name: '', email: '', password: '' })

const canAuth = computed(() => {
  const e = (auth.value.email || '').trim()
  const p = (auth.value.password || '').trim()
  if (!e || !p) return false
  if (authMode.value === 'register') {
    return (auth.value.name || '').trim().length > 0 && p.length >= 6
  }
  return true
})

// ============ 对话 ============
const conversations = ref([])
const deletedConversations = ref([])
const activeConversationId = ref(null)
const activeMessages = ref([])
const streaming = ref(false)
const streamingContent = ref('')
const abortCtl = ref(null)
const channel = ref(null)
const showTrash = ref(false)
const editingId = ref(null)
const editingContent = ref('')

const input = ref('')
const messagesRef = ref(null)
const inputRef = ref(null)
const error = ref('')

// ============ Key 管理 ============
const keys = ref([])
const providers = ref([])
const showKeyDrawer = ref(false)
const showKeyForm = ref(false)
const editingKeyId = ref(null)
const keyForm = ref(newKeyForm())
const configLoading = ref(false)
const configError = ref('')

const defaultKey = computed(() => keys.value.find(k => k.defaultKey && k.enabled) || null)
const activeConversations = computed(() => conversations.value)
const canSend = computed(() => Boolean(defaultKey.value && input.value.trim() && !streaming.value))
const visibleMessages = computed(() =>
  activeMessages.value.filter(m => !m.isOrphaned)
)

// ============ api 封装（转 SDK）/ api wrapper (delegates to SDK) ============
// 保留旧签名（接 path 含前导 /api），内部委托给 SDK 的 FetchHttpClient + unwrap。
// Preserve old signature (accepts path with leading /api); delegates to SDK FetchHttpClient + unwrap.
async function api(url, options = {}) {
  // SDK 期望 path 含前导 /api（baseUrl 仅 origin，不含路径前缀）；直接透传 url。
  // SDK expects path with leading /api (baseUrl is origin only, no path prefix); pass url through.
  let body
  if (options.body && typeof options.body === 'string') {
    body = JSON.parse(options.body)
  } else if (options.body !== undefined) {
    body = options.body
  }
  const result = await http.request(url, {
    method: options.method || 'GET',
    body,
  })
  return unwrap(result)
}

// ============ 生命周期 ============
onMounted(async () => {
  await loadProviders()
  const saved = localStorage.getItem(TOKEN_KEY)
  if (saved) {
    token.value = saved
    try {
      currentUser.value = await api('/api/auth/me')
      await loadKeys()
      await loadConversations()
      setupChannel()
      const lastId = localStorage.getItem(ACTIVE_CONV_KEY)
      if (lastId) await selectConversation(Number(lastId))
    } catch {
      token.value = null
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})

onBeforeUnmount(() => {
  if (channel.value) channel.value.close()
  if (abortCtl.value) abortCtl.value.abort()
})

function setupChannel() {
  if (typeof BroadcastChannel === 'undefined') return
  channel.value = new BroadcastChannel(BROADCAST_CHANNEL)
  channel.value.addEventListener('message', async e => {
    const { type, conversationId } = e.data || {}
    if (type === 'conversation:created' || type === 'conversation:updated'
        || type === 'conversation:deleted') {
      await loadConversations()
    } else if (type === 'message:created' && conversationId === activeConversationId.value) {
      await loadMessages()
    }
  })
}

function broadcast(type, payload = {}) {
  if (channel.value) channel.value.postMessage({ type, ...payload })
}

// ============ 认证 ============
function toggleAuthMode() {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
  authError.value = ''
}

async function doAuth() {
  authLoading.value = true
  authError.value = ''
  try {
    const endpoint = authMode.value === 'login' ? '/api/auth/login' : '/api/auth/register'
    const body = authMode.value === 'login'
      ? { email: auth.value.email.trim(), password: auth.value.password }
      : { name: auth.value.name.trim(), email: auth.value.email.trim(), password: auth.value.password }
    const result = await api(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    token.value = result.token
    currentUser.value = { id: result.userId, name: result.name, email: result.email }
    localStorage.setItem(TOKEN_KEY, result.token)
    auth.value = { name: '', email: '', password: '' }
    await loadProviders()
    await loadKeys()
    await loadConversations()
    setupChannel()
  } catch (e) {
    authError.value = (authMode.value === 'login' ? '登录' : '注册') + '失败：' + (e.message || String(e))
  } finally {
    authLoading.value = false
  }
}

function logout() {
  token.value = null
  currentUser.value = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ACTIVE_CONV_KEY)
  if (channel.value) { channel.value.close(); channel.value = null }
  keys.value = []
  conversations.value = []
  deletedConversations.value = []
  activeMessages.value = []
  activeConversationId.value = null
}

// ============ Provider / Key ============
async function loadProviders() {
  try { providers.value = await api('/api/providers') } catch { providers.value = [] }
}

async function loadKeys() {
  if (!currentUser.value) { keys.value = []; return }
  try { keys.value = await api(`/api/users/${currentUser.value.id}/keys`) } catch { keys.value = [] }
}

function newKeyForm() {
  const fallback = providers.value[0] || { name: 'ollama', defaultBaseUrl: 'http://localhost:11434', defaultModel: 'qwen2.5:7b' }
  return {
    name: '',
    provider: fallback.name || 'ollama',
    protocol: '',
    apiKey: '',
    baseUrl: fallback.defaultBaseUrl || 'http://localhost:11434',
    modelName: fallback.defaultModel || 'qwen2.5:7b',
    enabled: true
  }
}

function startCreateKey() {
  editingKeyId.value = null
  keyForm.value = newKeyForm()
  showKeyForm.value = true
}

function startEditKey(key) {
  editingKeyId.value = key.id
  keyForm.value = {
    name: key.name || '',
    provider: key.provider || providers.value[0]?.name || 'ollama',
    protocol: key.protocol || '',
    apiKey: '',
    baseUrl: key.baseUrl,
    modelName: key.modelName,
    enabled: key.enabled
  }
  showKeyForm.value = true
}

function cancelKeyForm() {
  editingKeyId.value = null
  keyForm.value = newKeyForm()
  showKeyForm.value = false
}

function applyProviderDefaults() {
  const target = providers.value.find(p => p.name === keyForm.value.provider)
  if (!target) return
  keyForm.value.baseUrl = target.defaultBaseUrl
  keyForm.value.modelName = target.defaultModel
}

async function saveKey() {
  if (!currentUser.value) return
  const keyId = editingKeyId.value
  configLoading.value = true
  configError.value = ''
  try {
    const url = keyId
      ? `/api/users/${currentUser.value.id}/keys/${keyId}`
      : `/api/users/${currentUser.value.id}/keys`
    await api(url, {
      method: keyId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(keyForm.value)
    })
    cancelKeyForm()
    await loadKeys()
  } catch (e) {
    configError.value = '保存 Key 失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

async function setDefaultKey(key) {
  if (!currentUser.value) return
  configLoading.value = true
  configError.value = ''
  try {
    await api(`/api/users/${currentUser.value.id}/keys/${key.id}/default`, { method: 'PUT' })
    await loadKeys()
  } catch (e) {
    configError.value = '设置默认 Key 失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

async function deleteKey(key) {
  if (!currentUser.value) return
  if (!window.confirm(`确定删除 Key "${key.name}" 吗？`)) return
  configLoading.value = true
  configError.value = ''
  try {
    await api(`/api/users/${currentUser.value.id}/keys/${key.id}`, { method: 'DELETE' })
    await loadKeys()
  } catch (e) {
    configError.value = '删除 Key 失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

// ============ 对话 ============
async function loadConversations() {
  if (!currentUser.value) {
    conversations.value = []
    deletedConversations.value = []
    return
  }
  try {
    const [active, deleted] = await Promise.all([
      api('/api/conversations'),
      api('/api/conversations?include_deleted=true').catch(() => [])
    ])
    conversations.value = active || []
    deletedConversations.value = deleted || []
  } catch {
    conversations.value = []
    deletedConversations.value = []
  }
}

async function createConversation() {
  if (!currentUser.value) return
  try {
    const conv = await api('/api/conversations', { method: 'POST' })
    await loadConversations()
    await selectConversation(conv.id)
    broadcast('conversation:created', { conversationId: conv.id })
  } catch (e) {
    error.value = '创建对话失败：' + (e.message || String(e))
  }
}

async function selectConversation(id) {
  // 切会话前先停掉旧会话的 SSE 流 + 清空 streaming 缓冲，避免旧 token 渲染到新会话
  if (abortCtl.value) {
    abortCtl.value.abort()
    abortCtl.value = null
  }
  streaming.value = false
  streamingContent.value = ''
  activeConversationId.value = id
  if (id != null) localStorage.setItem(ACTIVE_CONV_KEY, String(id))
  await loadMessages()
}

async function loadMessages() {
  if (activeConversationId.value == null) {
    activeMessages.value = []
    return
  }
  try {
    const list = await api(`/api/conversations/${activeConversationId.value}/messages`)
    activeMessages.value = list || []
  } catch (e) {
    error.value = '加载消息失败：' + (e.message || String(e))
  }
}

async function deleteConversation(conv) {
  if (!window.confirm(`确定删除对话 "${conv.title}" 吗？`)) return
  try {
    await api(`/api/conversations/${conv.id}`, { method: 'DELETE' })
    if (activeConversationId.value === conv.id) {
      activeConversationId.value = null
      activeMessages.value = []
      localStorage.removeItem(ACTIVE_CONV_KEY)
    }
    await loadConversations()
    broadcast('conversation:deleted', { conversationId: conv.id })
  } catch (e) {
    error.value = '删除对话失败：' + (e.message || String(e))
  }
}

async function restoreConversation(conv) {
  try {
    await api(`/api/conversations/${conv.id}/restore`, { method: 'POST' })
    await loadConversations()
    broadcast('conversation:updated', { conversationId: conv.id })
  } catch (e) {
    error.value = '恢复对话失败：' + (e.message || String(e))
  }
}

async function permanentlyDelete(conv) {
  if (!window.confirm(`永久删除对话 "${conv.title}"？所有消息也会被删除。`)) return
  try {
    await api(`/api/conversations/${conv.id}/permanent`, { method: 'DELETE' })
    await loadConversations()
    broadcast('conversation:deleted', { conversationId: conv.id })
  } catch (e) {
    error.value = '永久删除失败：' + (e.message || String(e))
  }
}

// ============ 消息：发 / 收 / 停 ============
async function send() {
  const text = input.value.trim()
  if (!text || streaming.value || !defaultKey.value || activeConversationId.value == null) return
  error.value = ''
  input.value = ''

  // 乐观插入 USER 消息
  activeMessages.value.push({
    id: null,
    localId: Date.now(),
    conversationId: activeConversationId.value,
    role: 'USER',
    content: text,
    isOrphaned: false,
    createdAt: new Date().toISOString()
  })
  streaming.value = true
  streamingContent.value = ''
  const abortController = new AbortController()
  abortCtl.value = abortController

  // SDK 流式 / SDK streaming
  let sseStream = null
  try {
    sseStream = streamConversationMessage({
      baseUrl: '',
      conversationId: activeConversationId.value,
      content: text,
      getToken: () => localStorage.getItem(TOKEN_KEY),
      signal: abortController.signal,
    })
    for await (const ev of sseStream.events()) {
      if (ev.type === 'token') {
        streamingContent.value += ev.text
      } else if (ev.type === 'done') {
        await loadMessages()
        await loadConversations()
        broadcast('conversation:updated', { conversationId: activeConversationId.value })
        broadcast('message:created', { conversationId: activeConversationId.value, messageId: ev.messageId })
        break
      } else if (ev.type === 'error') {
        error.value = `AI 调用失败 (${ev.code})：${ev.message}`
        break
      }
    }
  } catch (e) {
    if (e?.name !== 'AbortError') {
      error.value = '请求失败：' + (e?.message || String(e))
    }
  } finally {
    streaming.value = false
    streamingContent.value = ''
    abortCtl.value = null
  }
}

function stop() {
  if (abortCtl.value) abortCtl.value.abort()
  streaming.value = false
  streamingContent.value = ''
}

// ============ 编辑 / 重新生成 ============
function startEdit(m) {
  editingId.value = m.id
  editingContent.value = m.content
}

function cancelEdit() {
  editingId.value = null
  editingContent.value = ''
}

async function commitEdit(m) {
  const text = editingContent.value.trim()
  if (!text) return
  try {
    await api(`/api/messages/${m.id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: text })
    })
    editingId.value = null
    editingContent.value = ''
    await loadMessages()
  } catch (e) {
    error.value = '编辑失败：' + (e.message || String(e))
  }
}

async function regenerateMessage(m) {
  if (streaming.value) return
  streaming.value = true
  streamingContent.value = ''
  const abortController = new AbortController()
  abortCtl.value = abortController

  try {
    const sseStream = streamRegenerate({
      baseUrl: '',
      messageId: m.id,
      getToken: () => localStorage.getItem(TOKEN_KEY),
      signal: abortController.signal,
    })
    for await (const ev of sseStream.events()) {
      if (ev.type === 'token') {
        streamingContent.value += ev.text
      } else if (ev.type === 'done') {
        await loadMessages()
        await loadConversations()
        break
      } else if (ev.type === 'error') {
        error.value = `重新生成失败 (${ev.code})：${ev.message}`
        break
      }
    }
  } catch (e) {
    if (e?.name !== 'AbortError') error.value = '请求失败：' + (e?.message || String(e))
  } finally {
    streaming.value = false
    streamingContent.value = ''
    abortCtl.value = null
  }
}

// ============ 滚动 ============
watch(activeMessages, scrollToBottom, { deep: true })
function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}
</script>

<style scoped>
.app {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  background: #fff;
  box-shadow: 0 0 24px rgba(0, 0, 0, 0.06);
}

/* ===== 登录遮罩 ===== */
.auth-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
}
.auth-card {
  width: 360px;
  padding: 28px 24px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}
.auth-card h2 { text-align: center; font-size: 18px; margin-bottom: 18px; }
.auth-form { display: flex; flex-direction: column; gap: 10px; }
.auth-form input {
  padding: 9px 12px;
  border: 1px solid #d0d0d0;
  border-radius: 8px;
  font-size: 14px;
}
/* 密码框 wrapper：相对定位容纳眼睛按钮 / password wrapper: relative to host the eye button */
.password-field { position: relative; }
/* 给眼睛按钮留出右侧空间 / reserve space on the right for the eye button */
.password-field input { padding-right: 36px; }
/* 眼睛切换按钮：绝对定位右侧、垂直居中 / eye toggle: absolute right, vertically centered */
.password-toggle {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: #666;
  border-radius: 4px;
}
.password-toggle:hover:not(:disabled) { background: #f0f0f0; color: #333; }
.password-toggle:disabled { cursor: not-allowed; opacity: 0.5; }
.auth-submit { padding: 10px; font-size: 14px; margin-top: 4px; }
.auth-switch { text-align: center; margin-top: 12px; }

/* ===== 头部 ===== */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 20px;
  border-bottom: 1px solid #ececec;
  background: #fafafa;
}
.brand h1 { font-size: 18px; font-weight: 600; }
.current-key { margin-top: 3px; color: #64748b; font-size: 12px; }
.current-key.warning { color: #b45309; }
.controls { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.user-badge {
  padding: 4px 10px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 500;
}

/* ===== 三栏布局 ===== */
.workspace {
  display: grid;
  grid-template-columns: 260px 1fr auto;
  flex: 1;
  min-height: 0;
}

/* ===== 左侧栏 ===== */
.sidebar {
  border-right: 1px solid #e5e7eb;
  background: #f8fafc;
  padding: 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.new-conv { width: 100%; padding: 8px; border-radius: 8px; border: none; background: #2563eb; color: #fff; cursor: pointer; }
.sidebar-empty { color: #94a3b8; text-align: center; font-size: 13px; padding: 14px; }
.conv-list { list-style: none; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.conv-item:hover { background: #e2e8f0; }
.conv-item.active { background: #dbeafe; color: #1d4ed8; }
.conv-item.deleted { opacity: 0.65; }
.conv-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-actions { display: flex; gap: 6px; }
.trash-section { margin-top: auto; border-top: 1px solid #e2e8f0; padding-top: 8px; }

/* ===== 主聊天区 ===== */
.chat { display: flex; min-width: 0; flex-direction: column; }
.messages {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  background: #fff;
}
.empty { margin: auto; color: #999; font-size: 14px; text-align: center; }
.message {
  display: flex;
  max-width: 80%;
  flex-direction: column;
}
.message.user { align-self: flex-end; align-items: flex-end; }
.message.assistant { align-self: flex-start; align-items: flex-start; }
.message.orphaned { opacity: 0.45; }
.bubble {
  padding: 10px 14px;
  border-radius: 14px;
  line-height: 1.55;
  font-size: 15px;
  word-wrap: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.bubble :deep(pre) {
  background: #f6f8fa;
  border-radius: 8px;
  padding: 10px 12px;
  overflow-x: auto;
  font-size: 13px;
}
.bubble :deep(code) { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.9em; }
.bubble :deep(pre code) { background: transparent; padding: 0; }
.bubble :deep(p) { margin: 0 0 8px 0; }
.bubble :deep(p:last-child) { margin-bottom: 0; }
.bubble :deep(ul), .bubble :deep(ol) { padding-left: 22px; margin: 6px 0; }
.bubble :deep(table) { border-collapse: collapse; margin: 8px 0; }
.bubble :deep(th), .bubble :deep(td) { border: 1px solid #d0d7de; padding: 4px 8px; }
.message.user .bubble { color: #fff; background: #2563eb; border-bottom-right-radius: 4px; }
.message.assistant .bubble { color: #1d1d1f; background: #f1f1f3; border-bottom-left-radius: 4px; }
.edit-bubble { background: #f1f1f3; }
.edit-bubble textarea {
  width: 100%;
  border: 1px solid #d0d0d0;
  border-radius: 6px;
  padding: 6px;
  resize: vertical;
  font: inherit;
}
.edit-actions { display: flex; gap: 8px; margin-top: 6px; }

.meta {
  margin-top: 4px;
  padding: 0 4px;
  color: #999;
  font-size: 11px;
  display: flex;
  gap: 10px;
}
.meta-action { cursor: pointer; color: #2563eb; }
.meta-action:hover { text-decoration: underline; }
.orphan-tag { color: #b45309; font-weight: 500; }

/* ===== 输入区 ===== */
.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #ececec;
  background: #fafafa;
}
.composer textarea {
  flex: 1;
  max-height: 160px;
  padding: 10px 12px;
  resize: none;
  border: 1px solid #d0d0d0;
  border-radius: 10px;
  outline: none;
  background: #fff;
  font-size: 15px;
  line-height: 1.5;
}
.composer textarea:focus { border-color: #2563eb; }
.send {
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  color: #fff;
  background: #2563eb;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.send:disabled { opacity: 0.55; cursor: not-allowed; }
.send.stop { background: #b91c1c; }
.error {
  padding: 10px 16px;
  border-top: 1px solid #fecaca;
  color: #b91c1c;
  background: #fee2e2;
  font-size: 13px;
  cursor: pointer;
}

/* ===== Key Drawer ===== */
.key-drawer {
  width: 340px;
  overflow-y: auto;
  border-left: 1px solid #e5e7eb;
  background: #f8fafc;
}
.panel-section { padding: 16px; border-bottom: 1px solid #e5e7eb; }
.section-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
.section-title h2 { font-size: 14px; }
.panel-empty { padding: 18px 8px; color: #94a3b8; text-align: center; font-size: 13px; }
.key-list { display: flex; flex-direction: column; gap: 9px; }
.key-card {
  padding: 11px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}
.key-heading, .key-actions { display: flex; align-items: center; gap: 7px; }
.key-heading strong { margin-right: auto; font-size: 13px; }
.badge { padding: 2px 6px; border-radius: 999px; font-size: 10px; }
.badge.default { color: #1d4ed8; background: #dbeafe; }
.badge.enabled { color: #047857; background: #d1fae5; }
.badge.disabled { color: #64748b; background: #e2e8f0; }
.key-meta { margin-top: 5px; overflow: hidden; color: #64748b; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }
.key-actions { margin-top: 9px; }
.key-form { display: flex; flex-direction: column; gap: 9px; }
.key-form input, .key-form select {
  width: 100%;
  padding: 7px 10px;
  border: 1px solid #d0d0d0;
  border-radius: 8px;
  font-size: 13px;
  margin-top: 4px;
}
.key-form label { color: #475569; font-size: 12px; }
.checkbox-label { display: flex; align-items: center; gap: 7px; }
.checkbox-label input { width: auto; margin: 0; }
.primary {
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  color: #fff;
  background: #2563eb;
  cursor: pointer;
}
.primary.small { padding: 5px 10px; font-size: 12px; }
.secondary {
  color: #334155;
  padding: 7px 10px;
  border: 1px solid #d0d0d0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}
.secondary.small { padding: 5px 8px; font-size: 12px; }
.text-button {
  padding: 0;
  border: none;
  color: #2563eb;
  background: transparent;
  font-size: 12px;
  cursor: pointer;
}
.text-button.danger { color: #b91c1c; }
.text-button:hover { text-decoration: underline; }
.panel-error {
  margin: 12px;
  padding: 9px;
  border-radius: 8px;
  color: #b91c1c;
  background: #fee2e2;
  font-size: 12px;
  cursor: pointer;
}

@media (max-width: 880px) {
  .workspace { grid-template-columns: 1fr; }
  .sidebar, .key-drawer { display: none; }
}
</style>