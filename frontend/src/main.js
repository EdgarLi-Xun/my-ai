import { createApp } from 'vue'
import './style.css'
// highlight.js 主题（atom-one-light）
import 'highlight.js/styles/atom-one-light.css'
// katex 行内 / 块级数学公式样式
import 'katex/dist/katex.min.css'
import App from './App.vue'

createApp(App).mount('#app')