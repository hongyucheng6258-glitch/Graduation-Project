import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// —— 梧桐校园设计系统：在 element-plus 之后引入，确保主题覆盖生效 ——
import './styles/tokens.css'
import './styles/base.css'
import './styles/element-theme.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'

// 应用入口：注册 Pinia / Router / Element Plus
const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
