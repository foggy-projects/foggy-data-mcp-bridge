import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useRuntimeSession } from '@/stores/session'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/components/ConsoleShell.vue'),
    children: [
      { path: '', redirect: '/overview' },
      { path: 'overview', name: 'overview', component: () => import('@/pages/OverviewPage.vue') },
      { path: 'datasources', name: 'datasources', component: () => import('@/pages/DatasourcesPage.vue') },
      { path: 'namespaces/:workspace?', name: 'namespaces', component: () => import('@/pages/NamespacesPage.vue') },
      {
        path: 'bundles',
        redirect: () => ({
          name: 'namespaces',
          params: { workspace: 'bundles' },
          query: { ns: useRuntimeSession().namespace.value }
        })
      },
      {
        path: 'models',
        redirect: () => ({
          name: 'namespaces',
          params: { workspace: 'models' },
          query: { ns: useRuntimeSession().namespace.value }
        })
      },
      { path: 'query', name: 'query', component: () => import('@/pages/QueryPage.vue') },
      { path: 'tables', name: 'tables', component: () => import('@/pages/TablesPage.vue') },
      { path: 'compose', name: 'compose', component: () => import('@/pages/ComposePage.vue') },
      { path: 'fsscript', name: 'fsscript', component: () => import('@/pages/FsscriptPage.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' }
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

let initialValidation: Promise<boolean> | null = null

router.beforeEach(async to => {
  const session = useRuntimeSession()
  if (to.meta.public) {
    if (to.name === 'login' && session.authenticated.value) {
      return { name: 'overview' }
    }
    return true
  }

  if (session.authenticated.value) {
    return true
  }
  initialValidation ||= session.revalidate()
  const valid = await initialValidation
  initialValidation = null
  return valid ? true : { name: 'login', query: { redirect: to.fullPath } }
})
