import { beforeEach, describe, expect, it, vi } from 'vitest'
import { watch } from 'vue'

const { checkAccess } = vi.hoisted(() => ({
  checkAccess: vi.fn()
}))

vi.mock('@/api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('@/api/client')>()
  return {
    ...actual,
    checkAccess
  }
})

import { readNamespace, readRuntimeToken } from '@/api/storage'
import { resetSessionStateForTests, useRuntimeSession } from '@/stores/session'

describe('Runtime Console session', () => {
  beforeEach(() => {
    checkAccess.mockReset()
    sessionStorage.clear()
    resetSessionStateForTests()
  })

  it('validates management-all before persisting a token', async () => {
    checkAccess.mockResolvedValue({
      authenticated: true,
      authScope: 'management-all',
      runtimeApiVersion: 'foggy-runtime-api/v1'
    })
    const session = useRuntimeSession()

    await session.login('  accepted-token  ')

    expect(checkAccess).toHaveBeenCalledWith('accepted-token')
    expect(readRuntimeToken()).toBe('accepted-token')
    expect(session.authenticated.value).toBe(true)
  })

  it('rejects weaker auth scope without persisting the candidate token', async () => {
    checkAccess.mockResolvedValue({
      authenticated: true,
      authScope: 'mutations',
      runtimeApiVersion: 'foggy-runtime-api/v1'
    })
    const session = useRuntimeSession()

    await expect(session.login('candidate-token')).rejects.toMatchObject({
      code: 'RUNTIME_MANAGEMENT_ALL_REQUIRED'
    })
    expect(readRuntimeToken()).toBeNull()
  })

  it('revalidates on reload and clears a rejected stored token', async () => {
    sessionStorage.setItem('foggy.runtime-console.token', 'stored-token')
    checkAccess.mockRejectedValue(new Error('unauthorized'))
    const session = useRuntimeSession()

    await expect(session.revalidate()).resolves.toBe(false)
    expect(readRuntimeToken()).toBeNull()
    expect(session.authenticated.value).toBe(false)
  })

  it('publishes namespace storage and revision before observers reload scoped data', () => {
    const session = useRuntimeSession()
    const observed: Array<{ namespace: string; stored: string; revision: number }> = []
    const stop = watch(session.namespace, namespace => {
      observed.push({
        namespace,
        stored: readNamespace(),
        revision: session.namespaceRevision.value
      })
    }, { flush: 'sync' })

    session.setNamespace('  finance  ')
    session.setNamespace('finance')
    session.setNamespace('')

    expect(observed).toEqual([
      { namespace: 'finance', stored: 'finance', revision: 1 },
      { namespace: '', stored: '', revision: 2 }
    ])
    stop()
  })
})
