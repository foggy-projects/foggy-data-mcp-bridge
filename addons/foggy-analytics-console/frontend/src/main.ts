import { createApp } from 'vue'
import App from './App.vue'
import { initializeAnalyticsTheme } from './theme'
import './styles.css'

initializeAnalyticsTheme()
createApp(App).mount('#app')
