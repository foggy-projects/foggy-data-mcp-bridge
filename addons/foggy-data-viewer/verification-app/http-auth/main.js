import * as sdk from 'foggy-data-viewer'

// Static vendor import is evaluated before this host application configuration.
let token
let notifications = []
const options = { headers: { 'X-NS': 'tms-biz' } }
const query = { columns: ['id'], start: 0, limit: 10 }
const scope = { userId: 'u', model: 'Orders' }
window.httpAuth = {
  configure() {
    sdk.configureDataViewerHttp({
      getHeaders: () => token ? { Authorization: `Bearer ${token}` } : {},
      onUnauthorized: context => { notifications.push(context) }
    })
  },
  setToken(value) { token = value },
  notifications() { return notifications },
  async run() {
    const calls = [
      () => sdk.createQuery({ model: 'Orders', payload: { columns: ['id'], slice: [] } }, options),
      () => sdk.fetchQueryMeta('Orders', 'q', options),
      () => sdk.fetchQueryData('Orders', 'q', query, options),
      () => sdk.fetchQueryDataDirect('Orders', query, options),
      () => sdk.fetchFilterOptions('Orders', 'q', 'id', options),
      () => sdk.fetchQmSchema('Orders', options),
      () => sdk.fetchFrontendMeta('Orders', options),
      () => sdk.fetchMemberOptions({ queryModel: 'Orders', fieldName: 'id' }, options),
      () => sdk.listPresets(scope, options),
      () => sdk.getDefaultListPreset(scope, options),
      () => sdk.createListPreset(scope, { title: 'x', columns: ['id'] }, options),
      () => sdk.getListPreset('u', 'p', options),
      () => sdk.updateListPreset('u', 'p', { title: 'y' }, options),
      () => sdk.deleteListPreset('u', 'p', options),
      () => sdk.setDefaultListPreset('u', 'p', options),
      () => sdk.clearDefaultListPreset(scope, options),
      () => sdk.getTableDefaultQueryConfig({ queryModel: 'Orders' }, options)
    ]
    return Promise.all(calls.map(async call => {
      try { await call(); return 'ok' } catch (error) { return error.response?.status ?? 'error' }
    }))
  }
}
