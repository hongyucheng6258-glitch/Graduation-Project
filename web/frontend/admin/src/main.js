import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// —— 梧桐校园设计系统 ——
import './styles/tokens.css'
import './styles/base.css'
import './styles/element-theme.css'
import App from './App.vue'
import router from './router'

// 管理后台入口
const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
