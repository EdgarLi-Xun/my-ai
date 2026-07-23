<template>
  <div class="app">
    <!-- 登录遮罩 -->
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
          <input
            v-model="auth.password"
            type="password"
            autocomplete="current-password"
            placeholder="密码（至少 6 位）"
            :disabled="authLoading"
          />
          <button class="primary auth-submit" :disabled="authLoading || !canAuth">
            {{ authLoading ? '处理中...' : authMode === 'login' ? '登录' : '注册' }}
          </button>
        </form>
        <div class="auth-switch">
          <button class="text-button" @click="toggleAuthMode">
            {{ authMode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
          </button>
        </div>
        <div v-if="authError" class="panel-error" @click="authError = ''">{{ authError }}（点击关闭）</div>
      </div>
    </div>

    <header class="header">
      <div>
        <h1>MyAi Chat</h1>
        <p v-if="defaultKey" class="current-key">
          默认 Key：{{ defaultKey.name }} · {{ defaultKey.provider.toUpperCase() }} · {{ defaultKey.modelName }}
        </p>
        <p v-else class="current-key warning">当前用户没有可用的默认 Key</p>
      </div>
      <div class="controls">
        <span v-if="currentUser" class="user-badge">
          👤 {{ currentUser.name }}
        </span>
        <button class="secondary" @click="showManager = !showManager">
          {{ showManager ? '收起管理' : '管理 Key' }}
        </button>
        <button class="secondary" @click="clear" :disabled="loading || messages.length === 0">
          清空
        </button>
        <button class="secondary" @click="logout">退出</button>
      </div>
    </header>

    <div class="workspace">
      <aside v-if="showManager" class="manager">
        <section class="panel-section keys-section">
          <div class="section-title">
            <h2>Key 配置</h2>
            <button
              class="secondary small"
              :disabled="configLoading"
              @click="startCreateKey"
            >新增 Key</button>
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
              <div class="key-meta">{{ key.provider.toUpperCase() }} · {{ key.protocol || '默认' }} · {{ key.modelName }}</div>
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
            <label>
              名称
              <input v-model="keyForm.name" placeholder="例如：工作 OpenAI" :disabled="configLoading" />
            </label>
            <label>
              Provider
              <select v-model="keyForm.provider" :disabled="configLoading" @change="applyProviderDefaults">
                <option v-for="provider in providers" :key="provider.name" :value="provider.name">
                  {{ provider.displayName }}
                </option>
              </select>
            </label>
            <label>
              协议
              <select v-model="keyForm.protocol" :disabled="configLoading">
                <option value="">默认（Provider 决定）</option>
                <option value="OPENAI_COMPATIBLE">OpenAI 兼容</option>
                <option value="ANTHROPIC">Anthropic 兼容</option>
                <option value="OLLAMA">Ollama</option>
              </select>
            </label>
            <label>
              API Key
              <input
                v-model="keyForm.apiKey"
                type="password"
                autocomplete="new-password"
                :placeholder="editingKeyId ? '留空表示保留原 Key' : keyForm.provider === 'openai' ? 'OpenAI 必填' : 'Ollama 可留空'"
                :disabled="configLoading"
              />
            </label>
            <label>
              Base URL
              <input v-model="keyForm.baseUrl" placeholder="http(s)://..." :disabled="configLoading" />
            </label>
            <label>
              模型
              <input v-model="keyForm.modelName" placeholder="模型名称" :disabled="configLoading" />
            </label>
            <label class="checkbox-label">
              <input v-model="keyForm.enabled" type="checkbox" :disabled="configLoading" />
              启用该 Key
            </label>
            <button
              class="primary"
              :disabled="configLoading || !keyForm.name.trim() || !keyForm.baseUrl.trim() || !keyForm.modelName.trim()"
            >{{ configLoading ? '保存中...' : '保存配置' }}</button>
          </form>
        </section>

        <div v-if="configError" class="panel-error" @click="configError = ''">
          {{ configError }}（点击关闭）
        </div>
      </aside>

      <section class="chat">
        <main class="messages" ref="messagesRef">
          <div v-if="messages.length === 0 && !loading" class="empty">
            <template v-if="!defaultKey">请先设置一个启用的默认 Key</template>
            <template v-else>使用 {{ defaultKey.name }} 开始聊天吧 👋</template>
          </div>

          <div v-for="(message, index) in messages" :key="index" :class="['message', message.role]">
            <div class="bubble">{{ message.content }}</div>
            <div class="meta">{{ message.role === 'user' ? '我' : assistantLabel }}</div>
          </div>

          <div v-if="loading" class="message assistant">
            <div class="bubble loading">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </div>
            <div class="meta">{{ assistantLabel }}</div>
          </div>
        </main>

        <div v-if="error" class="error" @click="error = ''">{{ error }}（点击关闭）</div>

        <footer class="composer">
          <textarea
            v-model="input"
            @keydown.enter.exact.prevent="send"
            :disabled="loading || !defaultKey"
            :placeholder="defaultKey ? '输入消息，回车发送，Shift+回车换行' : '请先设置默认 Key'"
            rows="1"
            ref="inputRef"
          />
          <button class="send" @click="send" :disabled="!canSend">发送</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'

const TOKEN_KEY = 'myai.token'

// --- 登录态 ---
const token = ref(null)
const currentUser = ref(null)
const authMode = ref('login')
const authLoading = ref(false)
const authError = ref('')
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

// --- 应用状态 ---
const keys = ref([])
const providers = ref([])
const showManager = ref(true)
const showKeyForm = ref(false)
const editingKeyId = ref(null)
const keyForm = ref(newKeyForm())
const configLoading = ref(false)
const configError = ref('')

const input = ref('')
const messages = ref([])
const loading = ref(false)
const error = ref('')
const messagesRef = ref(null)
const inputRef = ref(null)

const defaultKey = computed(() => keys.value.find(key => key.defaultKey && key.enabled) || null)
const assistantLabel = computed(() => defaultKey.value
  ? `${defaultKey.value.name} · ${defaultKey.value.provider.toUpperCase()}`
  : 'AI')
const canSend = computed(() => Boolean(defaultKey.value && input.value.trim() && !loading.value))

watch(messages, scrollToBottom, { deep: true })
watch(loading, value => { if (!value) inputRef.value?.focus() })

onMounted(async () => {
  await loadProviders()
  const saved = localStorage.getItem(TOKEN_KEY)
  if (saved) {
    token.value = saved
    try {
      currentUser.value = await api('/api/auth/me')
      await loadKeys()
    } catch {
      token.value = null
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})

function newKeyForm(providerName) {
  const fallback = providers.value[0] || { name: 'ollama', defaultBaseUrl: 'http://localhost:11434', defaultModel: 'qwen2.5:7b' }
  const target = providerName
    ? providers.value.find(item => item.name === providerName) || fallback
    : fallback
  return {
    name: '',
    provider: target.name || 'ollama',
    protocol: '',
    apiKey: '',
    baseUrl: target.defaultBaseUrl || 'http://localhost:11434',
    modelName: target.defaultModel || 'qwen2.5:7b',
    enabled: true
  }
}

async function api(url, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (token.value) {
    headers['Authorization'] = 'Bearer ' + token.value
  }
  const response = await fetch(url, { ...options, headers })
  const text = await response.text()
  let body = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
  }
  if (body && typeof body === 'object' && 'code' in body && 'message' in body) {
    if (body.code === 4010) {
      localStorage.removeItem(TOKEN_KEY)
      token.value = null
      currentUser.value = null
      throw new Error('请重新登录')
    }
    if (body.code !== 0) {
      throw new Error(body.message || `业务错误 ${body.code}`)
    }
    return body.data
  }
  if (!response.ok) {
    throw new Error(text || `HTTP ${response.status}`)
  }
  return body
}

// --- 认证 ---
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
  keys.value = []
  messages.value = []
  error.value = ''
  showKeyForm.value = false
}

// --- Provider ---
async function loadProviders() {
  try {
    providers.value = await api('/api/providers')
  } catch {
    providers.value = []
  }
}

// --- Key ---
async function loadKeys() {
  if (!currentUser.value) {
    keys.value = []
    return
  }
  try {
    keys.value = await api(`/api/users/${currentUser.value.id}/keys`)
  } catch {
    keys.value = []
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
  const providerName = (keyForm.value.provider || '').toString()
  if (!providerName) return
  const target = providers.value.find(item => item.name === providerName)
  if (!target) return
  keyForm.value.baseUrl = target.defaultBaseUrl
  keyForm.value.modelName = target.defaultModel
}

async function saveKey() {
  const userId = currentUser.value?.id
  if (!userId) return
  const keyId = editingKeyId.value
  const wasDefault = keys.value.some(key => key.id === keyId && key.defaultKey)
  configLoading.value = true
  configError.value = ''
  try {
    const url = keyId
      ? `/api/users/${userId}/keys/${keyId}`
      : `/api/users/${userId}/keys`
    await api(url, {
      method: keyId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(keyForm.value)
    })
    cancelKeyForm()
    await loadKeys()
    if (wasDefault) clear()
  } catch (e) {
    configError.value = '保存 Key 失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

async function setDefaultKey(key) {
  const userId = currentUser.value?.id
  if (!userId) return
  configLoading.value = true
  configError.value = ''
  try {
    await api(`/api/users/${userId}/keys/${key.id}/default`, { method: 'PUT' })
    clear()
    await loadKeys()
  } catch (e) {
    configError.value = '设置默认 Key 失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

async function deleteKey(key) {
  const userId = currentUser.value?.id
  if (!userId) return
  if (!window.confirm(`确定删除 Key"${key.name}"吗？`)) return
  configLoading.value = true
  configError.value = ''
  try {
    await api(`/api/users/${userId}/keys/${key.id}`, { method: 'DELETE' })
    if (key.defaultKey) clear()
    await loadKeys()
  } catch (e) {
    configError.value = '删除 Key 失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

// --- 聊天 ---
async function send() {
  const text = input.value.trim()
  if (!text || loading.value || !defaultKey.value) return

  error.value = ''
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  try {
    const reply = await api('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: currentUser.value.id,
        messages: messages.value
      })
    })
    messages.value.push({ role: 'assistant', content: reply?.reply ?? '(空回复)' })
  } catch (e) {
    error.value = '请求失败：' + (e.message || String(e))
  } finally {
    loading.value = false
  }
}

function clear() {
  if (loading.value) return
  messages.value = []
  error.value = ''
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1180px;
  margin: 0 auto;
  background: #fff;
  box-shadow: 0 0 24px rgba(0, 0, 0, 0.06);
}

/* ---- 登录遮罩 ---- */
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

.auth-card h2 {
  text-align: center;
  font-size: 18px;
  margin-bottom: 18px;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.auth-form input {
  padding: 9px 12px;
  border: 1px solid #d0d0d0;
  border-radius: 8px;
  font-size: 14px;
}

.auth-submit {
  padding: 10px;
  font-size: 14px;
  margin-top: 4px;
}

.auth-switch {
  text-align: center;
  margin-top: 12px;
}

/* ---- 头部 ---- */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid #ececec;
  background: #fafafa;
}

.header h1 {
  font-size: 18px;
  font-weight: 600;
}

.current-key {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.current-key.warning {
  color: #b45309;
}

.controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.user-badge {
  padding: 4px 10px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 500;
}

select,
input,
textarea,
button {
  font: inherit;
}

.controls select,
.secondary,
.key-form input,
.key-form select {
  padding: 7px 10px;
  border: 1px solid #d0d0d0;
  border-radius: 8px;
  background: #fff;
}

button {
  cursor: pointer;
}

button:disabled,
select:disabled,
input:disabled,
textarea:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.workspace {
  display: flex;
  flex: 1;
  min-height: 0;
}

.manager {
  width: 340px;
  overflow-y: auto;
  border-right: 1px solid #e5e7eb;
  background: #f8fafc;
}

.panel-section {
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.section-title h2 {
  font-size: 14px;
}

.key-form {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.key-form input,
.key-form select {
  width: 100%;
  font-size: 13px;
}

.primary {
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  color: #fff;
  background: #2563eb;
}

.secondary {
  color: #334155;
}

.secondary.small {
  padding: 5px 8px;
  font-size: 12px;
}

.text-button {
  padding: 0;
  border: none;
  color: #2563eb;
  background: transparent;
  font-size: 12px;
}

.text-button.danger {
  color: #b91c1c;
}

.panel-empty {
  padding: 18px 8px;
  color: #94a3b8;
  text-align: center;
  font-size: 13px;
}

.key-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.key-card {
  padding: 11px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}

.key-heading,
.key-actions {
  display: flex;
  align-items: center;
  gap: 7px;
}

.key-heading strong {
  margin-right: auto;
  font-size: 13px;
}

.badge {
  padding: 2px 6px;
  border-radius: 999px;
  font-size: 10px;
}

.badge.default {
  color: #1d4ed8;
  background: #dbeafe;
}

.badge.enabled {
  color: #047857;
  background: #d1fae5;
}

.badge.disabled {
  color: #64748b;
  background: #e2e8f0;
}

.key-meta {
  margin-top: 5px;
  overflow: hidden;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}

.key-actions {
  margin-top: 9px;
}

.key-form label {
  color: #475569;
  font-size: 12px;
}

.key-form label input,
.key-form label select {
  margin-top: 4px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 7px;
}

.checkbox-label input {
  width: auto;
  margin: 0;
}

.panel-error {
  margin: 12px;
  padding: 9px;
  border-radius: 8px;
  color: #b91c1c;
  background: #fee2e2;
  font-size: 12px;
  cursor: pointer;
}

.chat {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

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

.empty {
  margin: auto;
  color: #999;
  font-size: 14px;
  text-align: center;
}

.message {
  display: flex;
  max-width: 80%;
  flex-direction: column;
}

.message.user {
  align-self: flex-end;
  align-items: flex-end;
}

.message.assistant {
  align-self: flex-start;
  align-items: flex-start;
}

.bubble {
  padding: 10px 14px;
  border-radius: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-size: 15px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.message.user .bubble {
  color: #fff;
  background: #2563eb;
  border-bottom-right-radius: 4px;
}

.message.assistant .bubble {
  color: #1d1d1f;
  background: #f1f1f3;
  border-bottom-left-radius: 4px;
}

.bubble.loading {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 52px;
  min-height: 22px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #888;
  animation: bounce 1.2s infinite ease-in-out;
}

.dot:nth-child(2) { animation-delay: 0.15s; }
.dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.meta {
  margin-top: 4px;
  padding: 0 4px;
  color: #999;
  font-size: 11px;
}

.error {
  padding: 10px 16px;
  border-top: 1px solid #fecaca;
  color: #b91c1c;
  background: #fee2e2;
  font-size: 13px;
  cursor: pointer;
}

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

.composer textarea:focus {
  border-color: #2563eb;
}

.send {
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  color: #fff;
  background: #2563eb;
  font-size: 14px;
  font-weight: 500;
}

.send:hover:not(:disabled),
.primary:hover:not(:disabled) {
  background: #1d4ed8;
}

@media (max-width: 760px) {
  .header {
    align-items: flex-start;
    flex-direction: column;
  }

  .controls {
    width: 100%;
    justify-content: flex-start;
  }

  .workspace {
    flex-direction: column;
    overflow-y: auto;
  }

  .manager {
    width: 100%;
    max-height: 48%;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
  }

  .chat {
    min-height: 52%;
  }
}
</style>
