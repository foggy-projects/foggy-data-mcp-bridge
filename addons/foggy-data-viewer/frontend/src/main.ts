import { createApp } from 'vue'
import VxeUI from 'vxe-pc-ui'
import 'vxe-pc-ui/lib/style.css'
import VxeTable from 'vxe-table'
import 'vxe-table/lib/style.css'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import App from './App.vue'
import { configureDataViewerHttp } from './api/http'

// Query ID selects a per-view HttpOnly cookie; each tab stays bound to its own view.
const viewMatch = window.location.pathname.match(/\/data-viewer\/view\/[^/]+\/([a-f0-9]{32})$/)
if (viewMatch) configureDataViewerHttp({ getHeaders: () => ({ 'X-Foggy-View': viewMatch[1] }) })

const app = createApp(App)

app.use(VxeUI)
app.use(VxeTable)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
