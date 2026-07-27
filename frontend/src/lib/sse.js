/**
 * SSE 客户端（ADR 0003）。
 *
 * <p>浏览器原生 EventSource 不支持自定义请求头（Bearer JWT）与 POST body，
 * 这里用 fetch + ReadableStream + TextDecoder 手动解析 {@code text/event-stream} 帧。
 *
 * <p>协议（与后端 {@code MessageService.streamReply} 对齐）：
 * <pre>
 *   event: token
 *   data: {"text": "你"}
 *
 *   event: done
 *   data: {"messageId": 123, "conversationId": 7}
 *
 *   event: error
 *   data: {"code": 4035, "message": "..."}
 * </pre>
 *
 * <p>每行 {@code field: value} 用 \n 分隔，帧之间用空行 \n\n 分隔；
 * 未指定 {@code event} 时默认走 "message" 事件（这里我们服务端都显式带 event）。
 */

/**
 * 消费一次 SSE 流。
 *
 * @param {string} url
 * @param {object} opts 同 fetch 选项，可含 body / headers / signal
 * @param {object} handlers
 * @param {(token: string) => void} handlers.onToken
 * @param {(payload: {messageId: number, conversationId: number}) => void} [handlers.onDone]
 * @param {(payload: {code: number, message: string}) => void} [handlers.onError]
 * @returns {Promise<void>} 流正常结束时 resolve；服务端发 error 事件不 reject，调用方看 onError。
 */
export async function consumeSSE(url, opts = {}, handlers = {}) {
  const { onToken, onDone, onError } = handlers
  const response = await fetch(url, opts)
  if (!response.ok || !response.body) {
    if (onError) {
      onError({ code: response.status || 5000, message: `HTTP ${response.status}` })
    }
    return
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      handleFrame(frame, { onToken, onDone, onError })
    }
  }
  // 连接关闭后再处理残余 buffer（服务端正常 close 也会带最后 \n\n，但防御性处理）
  if (buffer.trim().length > 0) {
    handleFrame(buffer, { onToken, onDone, onError })
  }
}

/**
 * 解析单帧：每行 {@code field: value}。空行已被调用方切走。
 */
function handleFrame(frame, { onToken, onDone, onError }) {
  let event = 'message'
  const dataLines = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  const dataStr = dataLines.join('\n')
  if (!dataStr) return
  let payload
  try {
    payload = JSON.parse(dataStr)
  } catch {
    payload = { text: dataStr }
  }
  switch (event) {
    case 'token':
      if (onToken && typeof payload.text === 'string') onToken(payload.text)
      break
    case 'done':
      if (onDone) onDone(payload)
      break
    case 'error':
      if (onError) onError(payload)
      break
    default:
      // 未知事件忽略
      break
  }
}