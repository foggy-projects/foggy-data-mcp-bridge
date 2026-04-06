/**
 * FactOrderQueryModel - 自动生成的 API 封装
 * ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改
 */
import {
  fetchQueryData,
  fetchMemberOptions,
  fetchFrontendMeta,
} from 'foggy-data-viewer'
import type {
  FetchDataParams,
  FetchDataResult,
  MemberQueryRequest,
  MemberQueryResponse,
  FrontendMeta,
} from 'foggy-data-viewer'
import type { FactOrderRow } from './FactOrder.types'
import { FACT_ORDER_QM_MODEL } from './FactOrder.types'

/** 查询订单数据 */
export async function queryOrders(
  params: FetchDataParams
): Promise<FetchDataResult<FactOrderRow>> {
  return fetchQueryData(FACT_ORDER_QM_MODEL, params)
}

/** 查询订单维度成员（客户、门店、渠道等） */
export async function queryOrderMembers(
  request: Omit<MemberQueryRequest, 'qmModel'>
): Promise<MemberQueryResponse> {
  return fetchMemberOptions({ ...request, qmModel: FACT_ORDER_QM_MODEL })
}

/** 获取订单模型前端元数据 */
export async function getOrderMeta(): Promise<FrontendMeta> {
  return fetchFrontendMeta(FACT_ORDER_QM_MODEL)
}
