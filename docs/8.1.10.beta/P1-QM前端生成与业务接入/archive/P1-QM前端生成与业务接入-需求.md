> **⚠️ SUPERSEDED** — 本文档已被 [P1-QM前端组件体系-技术规范](../../P1-QM前端组件体系-技术规范.md) 及其子规范替代。保留仅供讨论历史回溯。

---

# P1-QM前端生成与业务接入-需求

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`讨论中`

## 背景
当前 `QM -> 前端` 的讨论已经不再只是“生成几个组件文件”，而是逐步收敛为一组连续能力：

- 开发期：后端输出标准元数据，前端生成组件与类型代码
- 接入期：业务系统如何消费生成产物、如何扩展、如何避免改坏 generated 文件
- 运行期：组件在业务系统里需要的远程能力如何由 `data-viewer` 提供适配

因此，这组需求从 `8.1.10.beta` 起统一归档到本目录，作为一个系列进行设计、评审和实现跟踪。

## 系列目标
- 建立面向前端的 QM 标准元数据与代码生成能力
- 明确生成产物如何在业务系统中接入、扩展和维护
- 明确运行时依赖能力由哪一层提供，避免前端直接依赖内部 model 能力
- 让“可生成”“可使用”“可扩展”三个层面形成闭环

## 系列边界
- 本系列关注前端消费链路，不替代 `foggy-dataset-model` 的底层能力设计
- 本系列可以引用内部能力文档，但不把内部实现细节直接暴露为前端契约
- 本系列优先收敛 `8.1.10.beta` 可落地的 MVP，不追求一次覆盖全部 lookup/权限/分析型能力

## 文档结构
- [P1-QM前端代码生成-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端代码生成-需求.md)
  作用：定义 QM 前端代码生成的目标、元数据原料、输出产物和生成边界
- [P1-QM业务系统使用规范-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM业务系统使用规范-需求.md)
  作用：定义业务系统如何接入生成产物，以及组件必须预留哪些扩展位
- [P1-QM前端下拉组件生成-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端下拉组件生成-需求.md)
  作用：定义字典下拉组件和 QM 查询下拉组件的生成边界
- [P1-QM查询条件区与列筛选并存-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM查询条件区与列筛选并存-需求.md)
  作用：定义传统查询区和列筛选并存时的 query schema 与状态模型
- [P1-DataViewer维度成员实时过滤-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-DataViewer维度成员实时过滤-需求.md)
  作用：定义运行期维度成员远程过滤的 adapter 需求
- [P1-DataViewer维度成员实时过滤-设计收口.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-DataViewer维度成员实时过滤-设计收口.md)
  作用：锁定维度成员运行期适配的接口、schema 映射和实施顺序
- [qm-metadata-samples](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/qm-metadata-samples)
  作用：归档当前 QM 元数据样本，作为前端元数据契约设计输入

## 与内部能力文档的关系
以下文档属于底层能力基线，不纳入本系列目录，但作为重要参考输入保留：

- [P1-维度成员内部QM映射-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-维度成员内部QM映射-需求.md)
- [P1-维度成员内部QM映射-使用方式.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-维度成员内部QM映射/P1-维度成员内部QM映射-使用方式.md)

## 当前设计主线
`8.1.10.beta` 当前按以下主线推进：

1. 先确定面向前端的标准元数据与生成物边界
2. 再确定业务系统如何接入和扩展生成产物
3. 再明确除表格外的 lookup / 下拉组件生成边界
4. 再收口传统查询区与列筛选并存的 query 模型
5. 最后补齐业务系统运行时所需的 `data-viewer` adapter 能力

这样设计的原因很直接：

- 只谈生成、不谈接入，组件容易做成“能生成但不好用”
- 只谈表格、不谈 lookup 组件，生成能力会天然偏科
- 只把查询条件绑在列上，业务系统迟早会要求回到传统查询区
- 只谈运行时 adapter、不谈业务系统使用方式，组件扩展点会设计错位
- 把接入规范提前拉进讨论，才能倒逼组件和生成器预留稳定扩展位

## 后续版本预留
以下能力不纳入 `8.1.10.beta`，统一放入 `8.1.11.beta` 讨论：

- 查询字段分组
- 高级查询

## 后续拆分方向
若本系列继续扩展，优先按以下主题单独建档：

- `P1-QM前端元数据JSON契约-需求.md`
- `P1-QM统一字典接口-需求.md`
- `P2-QM业务系统定制参数合并机制-需求.md`
- `P2-QM生成产物校验与防覆盖-需求.md`

## 跟踪说明
- 后续围绕本系列的新讨论、新拆分和实现设计，优先落到本目录下。
- 若某子能力已形成独立协议，先补文档，再进入实现。
