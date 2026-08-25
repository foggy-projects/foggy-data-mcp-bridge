(function () {
  var theme = 'simple'
  try {
    theme = localStorage.getItem('foggy.analytics-console.theme.v1') === 'professional'
      ? 'professional'
      : 'simple'
  } catch (_) {
    // Browser storage can be disabled without blocking the initial render.
  }
  document.documentElement.dataset.theme = theme
})()
