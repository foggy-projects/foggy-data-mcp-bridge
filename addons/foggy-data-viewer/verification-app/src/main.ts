import { createApp } from 'vue'
import './style.css'
import App from './App.vue'

// 引入 vxe-table（和 frontend 保持一致）
import VXETable from 'vxe-table'
import 'vxe-table/lib/style.css'

// 引入 element-plus（和 frontend 保持一致）
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

// 引入 foggy-data-viewer 组件样式
import 'foggy-data-viewer/style.css'

const app = createApp(App)

// 注册组件库
app.use(VXETable)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
