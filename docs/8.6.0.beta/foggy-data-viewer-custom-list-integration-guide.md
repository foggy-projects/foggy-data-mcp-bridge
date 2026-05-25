# foggy-data-viewer 自定义列表最小接入指南

## 目标

接入方在 `DataTableWithSearch` 中开启 `listPreset` 后，用户可以保存自己的列表方案，包含列显隐、列顺序、列宽、固定列、当前筛选、排序和分页大小。设置默认方案后，下次进入同一个 `model + businessKey` 页面会自动应用。

## 前端最小接入

```vue
<script setup lang="ts">
import { DataTableWithSearch } from 'foggy-data-viewer'

const currentUser = { id: 'u_001' }
</script>

<template>
  <DataTableWithSearch
    :schema="tableSchema"
    :fetch-data="fetchRows"
    :list-preset="{
      enabled: true,
      model: 'TicketQueryModel',
      userId: currentUser.id,
      businessKey: 'ticket-list',
      autoLoadDefault: true,
      placement: 'toolbar-right'
    }"
  />
</template>
```

必要参数：

| 参数 | 是否必填 | 说明 |
| --- | --- | --- |
| `enabled` | 是 | 是否启用自定义列表入口。 |
| `model` | 是 | QM 模型名，后端按该值隔离配置。 |
| `userId` | 是 | v1 由前端显式传入，用作配置命名空间。 |
| `businessKey` | 建议填写 | 同一 QM 在不同页面的业务隔离 key。 |
| `autoLoadDefault` | 建议开启 | 首次加载时先应用默认方案，再执行查询。 |
| `placement` | 可选 | `toolbar-left`、`toolbar-right`、`external`。 |

`userId` 当前不是安全边界，只用于配置隔离。真实数据权限仍由已有查询链路控制；后续如果要从登录态、网关 header 或 session 解析用户，由接入方二次开发后端身份解析。

## 后端最小配置

默认配置即可工作：

```yaml
foggy:
  data-viewer:
    list-preset:
      storage: AUTO
      file-base-dir: data-viewer/list-presets
```

存储策略：

| `storage` | 行为 |
| --- | --- |
| `AUTO` | 默认值。配置了 `spring.data.mongodb.uri` 时使用 Mongo；没有 Mongo URI 时使用文件系统。 |
| `MONGO` | Mongo 优先；运行时不可用时退化到文件系统。 |
| `FILE` | 只使用文件系统。 |

文件系统目录按清洗后的 `userId/model/businessKey` 建立：

```text
{file-base-dir}/{safeUserId}/{safeModel}/{safeBusinessKey}/
  presets/{presetId}.json
  default.json
```

## API 约定

前端会访问以下路径：

```text
GET    /data-viewer/api/list-preset/users/{userId}/models/{model}?businessKey=
GET    /data-viewer/api/list-preset/users/{userId}/models/{model}/default?businessKey=
POST   /data-viewer/api/list-preset/users/{userId}/models/{model}
PUT    /data-viewer/api/list-preset/users/{userId}/presets/{id}
DELETE /data-viewer/api/list-preset/users/{userId}/presets/{id}
POST   /data-viewer/api/list-preset/users/{userId}/presets/{id}/default
```

同一个 `userId + model + businessKey` 下只允许一个默认方案。

## 可选字段校验

组件库默认只做基础结构校验。接入方如需按 QM schema 或字段权限限制保存内容，可以注册 `ListPresetFieldValidator`：

```java
import com.foggyframework.dataviewer.service.listpreset.ListPresetFieldValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataViewerListPresetConfig {

    @Bean
    public ListPresetFieldValidator listPresetFieldValidator() {
        return (userId, model, businessKey, request) -> {
            // 校验 request.getColumns(), request.getColumnSettings(),
            // request.getQuery().getSlice(), request.getQuery().getOrderBy()。
            // 不允许保存时抛 IllegalArgumentException。
        };
    }
}
```

## 当前边界

- 第一版不提供完整 DSL 编辑器，只保存组件当前能表达的列、筛选、排序和分页状态。
- 保存时至少需要一个可见字段。
- 应用方案时，如果当前 schema 已缺失某些字段，前端会提示失效字段并以当前 schema 为准。
- 共享查询、组织范围权限、后台自动解析用户身份暂缓。

## 验证命令

```bash
cd addons/foggy-data-viewer/frontend
npm test -- --run
npm run build:lib
```

```bash
mvn test -pl addons/foggy-data-viewer
```

```bash
cd addons/foggy-data-viewer/verification-app
npm run test:e2e -- --project=chromium
```

真实 Mongo 读写测试默认跳过。需要显式开启时：

```bash
set FOGGY_DATA_VIEWER_MONGO_IT=true
set FOGGY_DATA_VIEWER_MONGO_URI=mongodb://localhost:27017/foggy_data_viewer_it
mvn test -pl addons/foggy-data-viewer -Dtest=MongoListPresetStoreIntegrationTest
```
