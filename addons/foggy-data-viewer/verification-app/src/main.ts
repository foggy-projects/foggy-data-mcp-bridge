import { createApp } from 'vue'
import './style.css'
import App from './App.vue'

// 引入 vxe-table
import VxeUI from 'vxe-pc-ui'
import 'vxe-pc-ui/lib/style.css'
import VxeTable from 'vxe-table'
import 'vxe-table/lib/style.css'

const app = createApp(App)

// 注册 vxe-table
app.use(VxeUI)
app.use(VxeTable)

app.mount('#app')
