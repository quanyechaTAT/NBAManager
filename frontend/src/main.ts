import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/global.css'
import './styles/components.css'
import './styles/motion.css'
import './styles/polish.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 等待首个路由解析完成再挂载：避免 router-view 在初始导航期间渲染 undefined 组件，
// 使 mode="out-in" 过渡在"注释占位 → 组件"的首次切换上竞态卡死（表现为整页白屏）
router.isReady().then(() => {
  app.mount('#app')
})
