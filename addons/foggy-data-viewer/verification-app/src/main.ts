import { createApp } from 'vue'
import './style.css'
import App from './App.vue'

// 引入 vxe-table v4.7+ 需要同时引入 vxe-pc-ui
import VxeUI from 'vxe-pc-ui'
import VXETable from 'vxe-table'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

// 引入 foggy-data-viewer 组件（会自动引入所有依赖样式）
import 'foggy-data-viewer/style.css'

const app = createApp(App)

// 注册组件库（VxeUI 必须在 VXETable 之前注册）
app.use(VxeUI)
app.use(VXETable)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
