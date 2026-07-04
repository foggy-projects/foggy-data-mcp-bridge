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

function resetApiClientMock() {
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
}

describe('fetchQmSchema', () => {
  beforeEach(resetApiClientMock)

  it('preserves QM field group metadata', async () => {
    axiosMock.get.mockResolvedValue({
      data: {
        code: 200,
        data: {
          fields: {
            orderNo: {
              name: '订单号',
              type: 'TEXT',
              group: { key: 'order', title: '订单信息', order: 1 },
              category: 'dimension-caption'
            },
            customerName: {
              name: '客户',
              type: 'TEXT',
              groupKey: 'customer',
              groupTitle: '客户信息',
              groupOrder: 2
            }
          }
        }
      }
    })

    const { fetchQmSchema } = await import('./viewer')

    await expect(fetchQmSchema('FactOrderQueryModel')).resolves.toEqual([
      expect.objectContaining({
        name: 'orderNo',
        groupKey: 'order',
        groupTitle: '订单信息',
        groupOrder: 1,
        category: 'dimension-caption'
      }),
      expect.objectContaining({
        name: 'customerName',
        groupKey: 'customer',
        groupTitle: '客户信息',
        groupOrder: 2
      })
    ])
  })
})

describe('fetchQueryDataDirect', () => {
  beforeEach(resetApiClientMock)

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
