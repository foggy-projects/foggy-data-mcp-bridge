# E2E Integration Tests

端到端集成测试，验证完整链路：`AI Client → Odoo MCP Gateway → Foggy MCP Server → PostgreSQL`

## 前置条件

1. Docker Compose 环境运行中：
   ```bash
   cd ../docker && docker-compose up -d
   ```

2. 或本地安装：
   - Odoo 17 + foggy_mcp 插件已安装
   - Foggy MCP Server 运行中（lite 模式）
   - PostgreSQL 已初始化闭包表

## 初始化闭包表

```bash
# Docker 环境
docker exec -i foggy-odoo-postgres psql -U odoo -d odoo < ../sql/refresh_closure_tables.sql
docker exec -i foggy-odoo-postgres psql -U odoo -d odoo -c "SELECT refresh_all_closures();"

# 本地环境
psql -U odoo -d odoo < ../sql/refresh_closure_tables.sql
psql -U odoo -d odoo -c "SELECT refresh_all_closures();"
```

## 运行测试

```bash
# 设置环境变量（可选，默认值适用于 Docker 环境）
export FOGGY_MCP_URL=http://localhost:7108
export ODOO_MCP_URL=http://localhost:8069

# 运行
python -m pytest e2e/ -v
```
