import { onBeforeUnmount, ref, watch, type Ref } from 'vue'

export interface DeferredVisibilityOptions {
  delayMs?: number
  minVisibleMs?: number
  fadeMs?: number
}

export interface DeferredVisibilityState {
  shouldRender: Ref<boolean>
  visible: Ref<boolean>
}

const DEFAULT_DELAY_MS = 150
const DEFAULT_MIN_VISIBLE_MS = 250
const DEFAULT_FADE_MS = 160

export function useDeferredVisibility(
  source: Ref<boolean>,
  options: DeferredVisibilityOptions = {}
): DeferredVisibilityState {
  const delayMs = options.delayMs ?? DEFAULT_DELAY_MS
  const minVisibleMs = options.minVisibleMs ?? DEFAULT_MIN_VISIBLE_MS
  const fadeMs = options.fadeMs ?? DEFAULT_FADE_MS

  const shouldRender = ref(false)
  const visible = ref(false)
  let visibleSince = 0
  let showTimer: ReturnType<typeof setTimeout> | undefined
  let visibleTimer: ReturnType<typeof setTimeout> | undefined
  let hideTimer: ReturnType<typeof setTimeout> | undefined
  let removeTimer: ReturnType<typeof setTimeout> | undefined

  function clearTimer(timer: ReturnType<typeof setTimeout> | undefined): void {
    if (timer !== undefined) {
      clearTimeout(timer)
    }
  }

  function clearShowTimers(): void {
    clearTimer(showTimer)
    clearTimer(visibleTimer)
    showTimer = undefined
    visibleTimer = undefined
  }

  function clearHideTimers(): void {
    clearTimer(hideTimer)
    clearTimer(removeTimer)
    hideTimer = undefined
    removeTimer = undefined
  }

  function show(): void {
    clearHideTimers()
    if (visible.value) return

    if (!shouldRender.value) {
      shouldRender.value = true
    }

    visibleTimer = setTimeout(() => {
      visible.value = true
      visibleSince = Date.now()
      visibleTimer = undefined
    }, 0)
  }

  function hide(): void {
    clearShowTimers()

    if (!shouldRender.value) {
      visible.value = false
      return
    }

    const elapsed = visibleSince > 0 ? Date.now() - visibleSince : 0
    const waitMs = Math.max(0, minVisibleMs - elapsed)

    hideTimer = setTimeout(() => {
      visible.value = false
      visibleSince = 0
      hideTimer = undefined

      removeTimer = setTimeout(() => {
        shouldRender.value = false
        removeTimer = undefined
      }, fadeMs)
    }, waitMs)
  }

  watch(
    source,
    value => {
      if (value) {
        clearShowTimers()
        clearHideTimers()
        showTimer = setTimeout(() => {
          show()
          showTimer = undefined
        }, delayMs)
      } else {
        hide()
      }
    },
    { immediate: true }
  )

  onBeforeUnmount(() => {
    clearShowTimers()
    clearHideTimers()
  })

  return {
    shouldRender,
    visible
  }
}
