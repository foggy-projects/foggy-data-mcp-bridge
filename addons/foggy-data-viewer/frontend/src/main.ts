import { createApp } from 'vue'
import VXETable from 'vxe-table'
import 'vxe-table/lib/style.css'
import App from './App.vue'

const app = createApp(App)

app.use(VXETable)

app.mount('#app')
