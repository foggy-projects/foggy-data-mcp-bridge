import { beforeEach, describe, expect, it, vi } from 'vitest'

const axiosMock = vi.hoisted(() => ({
  create: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
  responseUse: vi.fn()
}))

vi.mock('axios', () => ({
  default: {
    create: axiosMock.create
  }
}))

describe('fetchQueryDataDirect', () => {
  beforeEach(() => {
    vi.resetModules()
    axiosMock.get.mockReset()
    axiosMock.post.mockReset()
    axiosMock.responseUse.mockReset()
    axiosMock.create.mockReset()
    axiosMock.create.mockReturnValue({
      get: axiosMock.get,
      post: axiosMock.post,
      interceptors: {
        response: {
          use: axiosMock.responseUse
        }
      }
    })
  })

  it('normalizes business columns before posting direct query', async () => {
    axiosMock.post.mockResolvedValue({
      data: {
        code: 200,
        data: { items: [], total: 0 }
      }
    })

    const { fetchQueryDataDirect } = await import('./viewer')

    await fetchQueryDataDirect('FactOrderQueryModel', {
      start: 0,
      limit: 20,
      columns: [' orderNo ', '_actions', '', 'amount', 'orderNo']
    })

    expect(axiosMock.post).toHaveBeenCalledWith(
      '/query/direct/FactOrderQueryModel',
      expect.objectContaining({
        columns: ['orderNo', 'amount']
      })
    )
  })

  it('throws a model-specific error when final direct query columns are empty', async () => {
    const { fetchQueryDataDirect } = await import('./viewer')

    await expect(fetchQueryDataDirect('FactOrderQueryModel', {
      start: 0,
      limit: 20,
      columns: [' ', '_actions']
    })).rejects.toThrow('FactOrderQueryModel direct query requires non-empty business columns')

    expect(axiosMock.post).not.toHaveBeenCalled()
  })
})
