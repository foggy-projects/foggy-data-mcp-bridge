@echo off
setlocal enabledelayedexpansion

REM 保存查询功能快速测试脚本 (Windows)
REM 使用方法：test-saved-query.bat

set BASE_URL=http://localhost:8080

echo ==========================================
echo  保存查询功能测试
echo ==========================================
echo.

REM 测试 1: 验证身份解析
echo [测试 1] 验证身份解析
curl -s -X GET "%BASE_URL%/test/identity" -H "Authorization: Bearer manager-token-123"
echo.
echo.

REM 测试 2: 保存查询（个人私有）
echo [测试 2] 保存查询（个人私有）
curl -s -X POST "%BASE_URL%/data-viewer/api/saved-query" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer manager-token-123" ^
  -d "{\"model\":\"FactSalesDemoAuthQueryModel\",\"title\":\"我的销售明细查询\",\"description\":\"查看2024年以来的销售数据\",\"columns\":[\"orderId\",\"orderDate\",\"customer\",\"product\",\"salesAmount\"],\"slice\":[{\"field\":\"orderDate\",\"op\":\">=\",\"value\":\"2024-01-01\"}],\"orderBy\":[{\"field\":\"orderDate\",\"order\":\"desc\"}],\"visibility\":\"PRIVATE\"}"
echo.
echo.

REM 测试 3: 保存查询（部门共享）
echo [测试 3] 保存查询（部门共享）
curl -s -X POST "%BASE_URL%/data-viewer/api/saved-query" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer manager-token-123" ^
  -d "{\"model\":\"FactSalesDemoAuthQueryModel\",\"title\":\"销售部门共享查询\",\"description\":\"部门内所有人可见\",\"columns\":[\"orderId\",\"orderDate\",\"customer\",\"salesAmount\"],\"visibility\":\"DEPARTMENT\"}"
echo.
echo.

REM 测试 4: 保存查询（租户共享）
echo [测试 4] 保存查询（租户共享）
curl -s -X POST "%BASE_URL%/data-viewer/api/saved-query" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer admin-token-789" ^
  -d "{\"model\":\"FactSalesDemoAuthQueryModel\",\"title\":\"全公司可见查询\",\"description\":\"租户内所有人可见\",\"columns\":[\"orderId\",\"orderDate\",\"salesAmount\"],\"visibility\":\"TENANT\"}"
echo.
echo.

REM 测试 5: 列出可见查询（Manager 视角）
echo [测试 5] 列出可见查询（Manager 视角）
echo 应看到: 个人私有 + 销售部门 + 租户查询
curl -s -X GET "%BASE_URL%/data-viewer/api/saved-query/list/FactSalesDemoAuthQueryModel" ^
  -H "Authorization: Bearer manager-token-123"
echo.
echo.

REM 测试 6: 列出可见查询（Analyst 视角）
echo [测试 6] 列出可见查询（Analyst 视角）
echo 应看到: 租户查询（不应看到 Manager 的个人查询和销售部门查询）
curl -s -X GET "%BASE_URL%/data-viewer/api/saved-query/list/FactSalesDemoAuthQueryModel" ^
  -H "Authorization: Bearer analyst-token-456"
echo.
echo.

echo ==========================================
echo  测试完成！
echo ==========================================
pause
