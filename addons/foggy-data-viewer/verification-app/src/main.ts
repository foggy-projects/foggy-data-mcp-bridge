import { createApp } from 'vue'
import './style.css'
import App from './App.vue'

// 引入 vxe-table 和 element-plus（用于全局注册）
import VXETable from 'vxe-table'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

// 引入 foggy-data-viewer 组件（会自动引入所有依赖样式）
import 'foggy-data-viewer/style.css'

const app = createApp(App)

// 注册组件库
app.use(VXETable)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
