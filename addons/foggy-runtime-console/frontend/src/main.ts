import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import VxeUI from 'vxe-pc-ui'
import 'vxe-pc-ui/lib/style.css'
import VxeTable from 'vxe-table'
import 'vxe-table/lib/style.css'
import 'foggy-data-viewer/style.css'
import App from './App.vue'
import { router } from './router'
import './styles.css'

createApp(App)
  .use(VxeUI)
  .use(VxeTable)
  .use(ElementPlus, { locale: zhCn })
  .use(router)
  .mount('#app')
