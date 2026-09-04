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

  it('derives field groups when V3 schema omits explicit group metadata', async () => {
    axiosMock.get.mockResolvedValue({
      data: {
        code: 200,
        data: {
          fields: {
            orderStatus: {
              name: '订单状态',
              type: 'STRING',
              filterable: true
            },
            'product$caption': {
              name: '商品(名称)',
              type: 'TEXT',
              filterType: 'dimension'
            },
            salesAmount: {
              name: '销售金额',
              type: 'MONEY',
              measure: true,
              aggregatable: true
            }
          }
        }
      }
    })

    const { fetchQmSchema } = await import('./viewer')

    await expect(fetchQmSchema('FactSalesDemoAuthQueryModel')).resolves.toEqual([
      expect.objectContaining({
        name: 'orderStatus',
        category: 'attribute',
        groupKey: 'attribute',
        groupTitle: '基础属性'
      }),
      expect.objectContaining({
        name: 'product$caption',
        category: 'dimension-caption',
        groupKey: 'dimension:product',
        groupTitle: '商品'
      }),
      expect.objectContaining({
        name: 'salesAmount',
        category: 'measure',
        groupKey: 'measure',
        groupTitle: '指标'
      })
    ])
  })

  it('passes only extData.viewer into the DataViewer field definition', async () => {
    axiosMock.get.mockResolvedValue({
      data: {
        code: 200,
        data: {
          fields: {
            amount: {
              name: '金额',
              type: 'MONEY',
              extData: {
                viewer: {
                  format: 'money',
                  rawUnit: 'minor',
                  displayUnit: 'CNY',
                  scaleFactor: 100,
                  precision: 2
                },
                internalOnly: 'hidden'
              }
            }
          }
        }
      }
    })

    const { fetchQmSchema } = await import('./viewer')
    const [field] = await fetchQmSchema('TmsBizQuery')

    expect(field.extData).toEqual({
      viewer: {
        format: 'money',
        rawUnit: 'minor',
        displayUnit: 'CNY',
        scaleFactor: 100,
        precision: 2
      }
    })
    expect(field.extData).not.toHaveProperty('internalOnly')
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
