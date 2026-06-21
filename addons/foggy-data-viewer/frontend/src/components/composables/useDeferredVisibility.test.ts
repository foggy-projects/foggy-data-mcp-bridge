import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useDeferredVisibility } from './useDeferredVisibility'

function mountHarness(options = { delayMs: 150, minVisibleMs: 250, fadeMs: 160 }) {
  let setBusy!: (value: boolean) => void

  const wrapper = mount(defineComponent({
    setup() {
      const busy = ref(false)
      const state = useDeferredVisibility(busy, options)
      setBusy = (value: boolean) => {
        busy.value = value
      }
      return {
        busy,
        shouldRender: state.shouldRender,
        visible: state.visible
      }
    },
    template: '<div />'
  }))

  return { wrapper, setBusy }
}

describe('useDeferredVisibility', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('does not render for fast operations that finish before the delay', async () => {
    vi.useFakeTimers()
    const { wrapper, setBusy } = mountHarness()

    setBusy(true)
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(100)
    setBusy(false)
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(300)

    expect(wrapper.vm.shouldRender).toBe(false)
    expect(wrapper.vm.visible).toBe(false)
  })

  it('renders after delay and then becomes visible', async () => {
    vi.useFakeTimers()
    const { wrapper, setBusy } = mountHarness()

    setBusy(true)
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(150)

    expect(wrapper.vm.shouldRender).toBe(true)
    expect(wrapper.vm.visible).toBe(false)

    await vi.advanceTimersByTimeAsync(1)
    expect(wrapper.vm.visible).toBe(true)
  })

  it('keeps visible for the minimum duration before fading out', async () => {
    vi.useFakeTimers()
    const { wrapper, setBusy } = mountHarness({ delayMs: 100, minVisibleMs: 250, fadeMs: 160 })

    setBusy(true)
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(100)
    await vi.advanceTimersByTimeAsync(1)
    expect(wrapper.vm.visible).toBe(true)

    await vi.advanceTimersByTimeAsync(80)
    setBusy(false)
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(169)
    expect(wrapper.vm.visible).toBe(true)

    await vi.advanceTimersByTimeAsync(1)
    expect(wrapper.vm.visible).toBe(false)
    expect(wrapper.vm.shouldRender).toBe(true)

    await vi.advanceTimersByTimeAsync(160)
    expect(wrapper.vm.shouldRender).toBe(false)
  })

  it('clears timers on unmount', async () => {
    vi.useFakeTimers()
    const { wrapper, setBusy } = mountHarness()

    setBusy(true)
    await wrapper.vm.$nextTick()
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(500)

    expect(wrapper.exists()).toBe(false)
  })
})
