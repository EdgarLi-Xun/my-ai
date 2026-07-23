<template>
  <div class="app">
    <header class="header">
      <div>
        <h1>MyAi Chat</h1>
        <p v-if="defaultKey" class="current-key">
          默认 Key：{{ defaultKey.name }} · {{ defaultKey.provider.toUpperCase() }} · {{ defaultKey.modelName }}
        </p>
        <p v-else class="current-key warning">当前用户没有可用的默认 Key</p>
      </div>
      <div class="controls">
        <label for="user">用户</label>
        <select id="user" v-model.number="selectedUserId" :disabled="loading || configLoading">
          <option :value="null">请选择用户</option>
          <option v-for="user in users" :key="user.id" :value="user.id">
            {{ user.name }}
          </option>
        </select>
        <button class="secondary" @click="showManager = !showManager">
          {{ showManager ? '收起管理' : '管理用户与 Key' }}
        </button>
        <button class="secondary" @click="clear" :disabled="loading || messages.length === 0">
          清空
        </button>
      </div>
    </header>

    <div class="workspace">
      <aside v-if="showManager" class="manager">
        <section class="panel-section">
          <div class="section-title">
            <h2>用户</h2>
            <button
              v-if="selectedUser"
              class="danger-link"
              :disabled="configLoading"
              @click="deleteUser"
            >删除当前用户</button>
          </div>
          <form class="compact-form" @submit.prevent="createUser">
            <input v-model="userForm.name" placeholder="用户名" :disabled="configLoading" />
            <input v-model="userForm.email" placeholder="邮箱（可选）" :disabled="configLoading" />
            <button class="primary" :disabled="configLoading || !userForm.name.trim()">新增用户</button>
          </form>
        </section>

        <section class="panel-section keys-section">
          <div class="section-title">
            <h2>Key 配置</h2>
            <button
              class="secondary small"
              :disabled="!selectedUser || configLoading"
              @click="startCreateKey"
            >新增 Key</button>
          </div>

          <div v-if="!selectedUser" class="panel-empty">请先选择或创建用户</div>
          <div v-else-if="keys.length === 0" class="panel-empty">该用户还没有 Key</div>
          <div v-else class="key-list">
            <article v-for="key in keys" :key="key.id" class="key-card">
              <div class="key-heading">
                <strong>{{ key.name }}</strong>
                <span v-if="key.defaultKey" class="badge default">默认</span>
                <span :class="['badge', key.enabled ? 'enabled' : 'disabled']">
                  {{ key.enabled ? '启用' : '禁用' }}
                </span>
              </div>
              <div class="key-meta">{{ key.provider.toUpperCase() }} · {{ key.modelName }}</div>
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
            <template v-if="!selectedUser">选择用户后开始聊天 👋</template>
            <template v-else-if="!defaultKey">请先为该用户设置一个启用的默认 Key</template>
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

const users = ref([])
const selectedUserId = ref(null)
const keys = ref([])
const providers = ref([])
const showManager = ref(true)
const showKeyForm = ref(false)
const editingKeyId = ref(null)
const userForm = ref({ name: '', email: '' })
const keyForm = ref(newKeyForm())
const configLoading = ref(false)
const configError = ref('')

const input = ref('')
const messages = ref([])
const loading = ref(false)
const error = ref('')
const messagesRef = ref(null)
const inputRef = ref(null)

const selectedUser = computed(() => users.value.find(user => user.id === selectedUserId.value) || null)
const defaultKey = computed(() => keys.value.find(key => key.defaultKey && key.enabled) || null)
const assistantLabel = computed(() => defaultKey.value
  ? `${defaultKey.value.name} · ${defaultKey.value.provider.toUpperCase()}`
  : 'AI')
const canSend = computed(() => Boolean(defaultKey.value && input.value.trim() && !loading.value))

watch(selectedUserId, async (userId, previousUserId) => {
  if (userId !== previousUserId) {
    clear()
    cancelKeyForm()
    await loadKeys(userId)
  }
})
watch(messages, scrollToBottom, { deep: true })
watch(loading, value => { if (!value) inputRef.value?.focus() })

onMounted(async () => {
  await loadProviders()
  await loadUsers()
})

function newKeyForm(providerName) {
  const fallback = providers.value[0] || { name: 'ollama', defaultBaseUrl: 'http://localhost:11434', defaultModel: 'qwen2.5:7b' }
  const target = providerName
    ? providers.value.find(item => item.name === providerName) || fallback
    : fallback
  return {
    name: '',
    provider: target.name || 'ollama',
    apiKey: '',
    baseUrl: target.defaultBaseUrl || 'http://localhost:11434',
    modelName: target.defaultModel || 'qwen2.5:7b',
    enabled: true
  }
}

async function loadProviders() {
  try {
    providers.value = await api('/api/providers')
  } catch (e) {
    providers.value = []
    configError.value = '加载 Provider 池失败：' + (e.message || String(e))
  }
}

async function api(url, options = {}) {
  const response = await fetch(url, options)
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

async function loadUsers() {
  configError.value = ''
  try {
    const currentId = selectedUserId.value
    users.value = await api('/api/users')
    const nextId = users.value.some(user => user.id === currentId)
      ? currentId
      : users.value[0]?.id ?? null
    selectedUserId.value = nextId
    if (nextId === currentId) {
      await loadKeys(nextId)
    }
  } catch (e) {
    configError.value = '加载用户失败：' + (e.message || String(e))
  }
}

async function loadKeys(userId = selectedUserId.value) {
  if (!userId) {
    keys.value = []
    return
  }
  try {
    const result = await api(`/api/users/${userId}/keys`)
    if (selectedUserId.value === userId) {
      keys.value = result
    }
  } catch (e) {
    keys.value = []
    configError.value = '加载 Key 失败：' + (e.message || String(e))
  }
}

async function createUser() {
  configLoading.value = true
  configError.value = ''
  try {
    const user = await api('/api/users', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(userForm.value)
    })
    userForm.value = { name: '', email: '' }
    await loadUsers()
    selectedUserId.value = user.id
  } catch (e) {
    configError.value = '新增用户失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

async function deleteUser() {
  if (!selectedUser.value || !window.confirm(`确定删除用户“${selectedUser.value.name}”及其全部 Key 吗？`)) return
  configLoading.value = true
  configError.value = ''
  try {
    await api(`/api/users/${selectedUser.value.id}`, { method: 'DELETE' })
    selectedUserId.value = null
    await loadUsers()
  } catch (e) {
    configError.value = '删除用户失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
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
  if (!selectedUserId.value) return
  const keyId = editingKeyId.value
  const wasDefault = keys.value.some(key => key.id === keyId && key.defaultKey)
  configLoading.value = true
  configError.value = ''
  try {
    const url = keyId
      ? `/api/users/${selectedUserId.value}/keys/${keyId}`
      : `/api/users/${selectedUserId.value}/keys`
    await api(url, {
      method: keyId ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(keyForm.value)
    })
    cancelKeyForm()
    await loadUsers()
    if (wasDefault) clear()
  } catch (e) {
    configError.value = '保存 Key 失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

async function setDefaultKey(key) {
  configLoading.value = true
  configError.value = ''
  try {
    await api(`/api/users/${selectedUserId.value}/keys/${key.id}/default`, { method: 'PUT' })
    clear()
    await loadUsers()
  } catch (e) {
    configError.value = '设置默认 Key 失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

async function deleteKey(key) {
  if (!window.confirm(`确定删除 Key“${key.name}”吗？`)) return
  configLoading.value = true
  configError.value = ''
  try {
    await api(`/api/users/${selectedUserId.value}/keys/${key.id}`, { method: 'DELETE' })
    if (key.defaultKey) clear()
    await loadUsers()
  } catch (e) {
    configError.value = '删除 Key 失败：' + (e.message || String(e))
  } finally {
    configLoading.value = false
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value || !defaultKey.value || !selectedUserId.value) return

  error.value = ''
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  try {
    const reply = await api('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: selectedUserId.value,
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

.controls label {
  color: #666;
  font-size: 13px;
}

select,
input,
textarea,
button {
  font: inherit;
}

.controls select,
.secondary,
.compact-form input,
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

.compact-form,
.key-form {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.compact-form input,
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

.danger-link,
.text-button {
  padding: 0;
  border: none;
  color: #2563eb;
  background: transparent;
  font-size: 12px;
}

.danger-link,
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
