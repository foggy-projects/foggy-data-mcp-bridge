import type {
  BeforeQueryHookFn,
  FetchDataParams,
  FetchDataParamsWithExtensions,
  SearchHookUpdate
} from '../src/types'

type Equal<Left, Right> =
  (<T>() => T extends Left ? 1 : 2) extends
  (<T>() => T extends Right ? 1 : 2) ? true : false

type Expect<T extends true> = T

type PlanningQueryRecipe = Omit<FetchDataParams, 'columns'> & {
  columns?: string[]
  extData?: { source: string }
}

type PageRemainsNumber = Expect<Equal<PlanningQueryRecipe['page'], number>>
type PageSizeRemainsNumber = Expect<Equal<PlanningQueryRecipe['pageSize'], number>>
type ColumnsRemainOptionalStrings = Expect<Equal<PlanningQueryRecipe['columns'], string[] | undefined>>

type ExtendedParams = FetchDataParamsWithExtensions<{
  slots?: { business: string }
  tenantRule?: { field: string; value: string }
}>

declare const params: ExtendedParams
const page: number = params.page
const slots: { business: string } | undefined = params.slots
const tenantRule: { field: string; value: string } | undefined = params.tenantRule

void page
void slots
void tenantRule

const standardParams: FetchDataParams = {
  page: 1,
  pageSize: 20,
  columns: ['id'],
  slice: [],
  orderBy: []
}
const standardBeforeHook: BeforeQueryHookFn = () => standardParams
const standardSearchUpdate: SearchHookUpdate = { params: standardParams }

void standardBeforeHook
void standardSearchUpdate

// Keep these aliases in the type-check input so the assertions are not elided
// by a future compiler configuration change.
type _Assertions = [PageRemainsNumber, PageSizeRemainsNumber, ColumnsRemainOptionalStrings]
export type TypeCompatibilityAssertions = _Assertions
