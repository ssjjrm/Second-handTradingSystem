import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { imgFallback } from './directives/imgFallback'
import './styles/main.css'

const app = createApp(App)

// 全局指令：图片加载失败自动换占位图（见 directives/imgFallback.js）
app.directive('img-fallback', imgFallback)

app.use(router).mount('#app')
