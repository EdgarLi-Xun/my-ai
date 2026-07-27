/**
 * Markdown 渲染（ADR 0003）：markdown-it + highlight.js + katex → DOMPurify 净化。
 *
 * <p>三层处理顺序：
 * <ol>
 *   <li>markdown-it 解析为 HTML（开启 linkify + html）</li>
 *   <li>highlight.js 处理代码块（markdown-it-highlightjs 钩子）</li>
 *   <li>katex 替换行内 / 块级公式为安全 HTML（@vscode/markdown-it-katex）</li>
 *   <li>DOMPurify 过滤危险标签 / 属性（XSS 防御）</li>
 * </ol>
 *
 * <p>v-html 来源仅限服务端 message.content（已被 service 层 BizException 校验过非空），
 * 配合 DOMPurify 默认 + 显式黑名单，等价于"信任边界内"。
 */
import MarkdownIt from 'markdown-it'
import highlightjs from 'markdown-it-highlightjs'
import DOMPurify from 'dompurify'
// @vscode/markdown-it-katex 依赖 katex；先 import katex 让其注册到全局 window
import 'katex'
import markdownItKatex from '@vscode/markdown-it-katex'

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: false,
  breaks: true
})
  .use(highlightjs, {
    inline: true,
    auto: true,
    code: true
  })
  .use(markdownItKatex, {
    throwOnError: false,
    output: 'html'
  })

const FORBID_TAGS = ['style', 'form', 'input', 'iframe', 'script', 'object', 'embed']
const FORBID_ATTR = ['style', 'onerror', 'onload', 'onclick', 'onmouseover']

/**
 * 把 Markdown 源文本渲染为已净化的 HTML 字符串。
 * @param {string} source
 * @returns {string} 净化后的 HTML
 */
export function renderMarkdown(source) {
  if (!source) return ''
  const raw = md.render(source)
  return DOMPurify.sanitize(raw, {
    FORBID_TAGS,
    FORBID_ATTR,
    ADD_ATTR: ['target']
  })
}