# Foggy Odoo Bridge — 用户安装自动化方案

> **注：此文档已过时。新架构使用动态 DataSource API 配置，TM/QM 模型内置于 Docker 镜像。**
>
> **请参考 `README.md` 和 Setup Wizard（`foggy_setup_wizard.py`）了解最新安装流程。**

## ~~问题分析~~（已废弃）

~~用户从 Odoo Apps 安装 `foggy_mcp` 模块后，只获得了 Python 插件部分。~~
~~要完整使用，还需要：~~

| ~~缺失组件~~ | ~~说明~~ | ~~难度~~ |
|---|---|---|
| ~~**Foggy MCP Server**~~ | ~~Java 服务（JAR 或 Docker）~~ | ~~高 — 用户可能不会部署 Java~~ |
| ~~**TM/QM 模型文件**~~ | ~~8 个 .tm + 8 个 .qm + 2 个 .fsscript~~ | ~~中 — 需要放对位置~~ |
| ~~**闭包表 SQL**~~ | ~~4 张闭包表 + 刷新函数~~ | ~~中 — 需要在 Odoo 的 PostgreSQL 上执行~~ |
| ~~**Odoo 配置**~~ | ~~foggy_mcp.server_url 指向 Foggy~~ | ~~低 — 但需要知道正确地址~~ |
| ~~**API Key**~~ | ~~每用户创建~~ | ~~低 — UI 已有~~ |

**~~核心矛盾~~**：~~Odoo Apps 只允许上传一个 Python 模块 ZIP。我们需要让这个 ZIP 能够引导用户完成 Java 侧的安装。~~

---

## 当前架构（2024更新）

### 简化安装流程

1. **Docker 镜像内置模型** — TM/QM 模型已打包进 `foggysource/foggy-odoo-mcp` 镜像
2. **动态 DataSource API** — Java 侧用 SQLite 启动，Odoo 通过 API 注册自己的 PostgreSQL 数据源
3. **Setup Wizard** — 向导生成一键 Docker 命令，引导完成配置

### 安装流程（用户视角）

```
Step 1: Odoo Apps 安装 foggy_mcp 模块
              ↓
Step 2: Settings → Foggy MCP → 🧙 Setup Wizard（按钮）
              ↓
Step 3: 向导 Step 1 — Welcome
              ↓
Step 4: 向导 Step 2 — 生成 Docker 命令（自动检测平台）
        - 复制一键 docker run 命令
        - 在终端执行启动 Foggy MCP Server
              ↓
Step 5: 向导 Step 3 — 测试连接
        - [Test Connection] 按钮：调用 Foggy /actuator/health
              ↓
Step 6: 向导 Step 4 — 配置数据源
        - 自动填充 Odoo 数据库连接信息
        - [Configure Data Source] 按钮：通过 API 注册数据源
              ↓
Step 7: 向导 Step 5 — 初始化闭包表
        - [一键执行] 按钮：直接在 Odoo 的 PostgreSQL 上运行 SQL
              ↓
Step 8: 创建 API Key → 配置 AI 客户端 → 开始使用
```

---

## ~~推荐方案：Setup Wizard + 内置资源 + Docker 一键部署~~（已废弃）

~~### 方案架构~~

```
~~foggy_mcp/                      ← Odoo 模块（上传到 Odoo Apps）~~
~~├── __manifest__.py~~
~~├── models/ controllers/ ...    ← 已有的 Python 代码~~
~~├── setup/                      ← 新增：内置安装资源~~
~~│   ├── docker-compose.yml      ← 一键启动 Foggy MCP 的 compose~~
~~│   ├── foggy-models/           ← TM/QM 模型文件（随模块分发）~~
~~│   │   ├── odoo17.fsscript~~
~~│   │   ├── dicts.fsscript~~
~~│   │   ├── model/*.tm~~
~~│   │   └── query/*.qm~~
~~│   └── sql/~~
~~│       └── refresh_closure_tables.sql~~
~~├── wizard/                     ← 新增：安装向导~~
~~│   ├── __init__.py~~
~~│   ├── foggy_setup_wizard.py   ← 向导模型~~
~~│   └── foggy_setup_wizard_views.xml~~
~~└── static/description/         ← 市场页面~~
```

### 安装流程（用户视角）

```
Step 1: Odoo Apps 安装 foggy_mcp 模块
              ↓
Step 2: Settings → Foggy MCP → 🧙 Setup Wizard（按钮）
              ↓
Step 3: 向导 Step 1 — 选择部署方式
        ┌─ [Docker] 一键 docker-compose（推荐）
        └─ [Manual] 手动下载 JAR + 配置
              ↓
Step 4: 向导 Step 2 — 自动检测 Odoo DB 连接信息
        - 读取 odoo.conf 中的 db_host, db_port, db_user, db_password
        - 生成预填充的 docker-compose.yml
        - 显示一键复制的命令
              ↓
Step 5: 向导 Step 3 — 初始化闭包表
        - [一键执行] 按钮：直接在 Odoo 的 PostgreSQL 上运行 SQL
        - 或显示手动 SQL 供复制
              ↓
Step 6: 向导 Step 4 — 连接测试
        - [Test Connection] 按钮：调用 Foggy /actuator/health
        - 成功后自动保存 server_url
        - 提示创建 API Key
              ↓
Step 7: 创建 API Key → 配置 AI 客户端 → 开始使用
```

---

## ~~实现细节~~（已废弃）

### ~~1. 内置模型文件（`setup/foggy-models/`）~~

~~将 `foggy-models/` 目录复制到模块内部。用户安装模块后，文件位于 Odoo 的 addons 路径中。~~

~~Docker compose 通过 volume mount 引用这个路径：~~
```yaml
# 已废弃：模型现在内置在 Docker 镜像中
# volumes:
#   - /path/to/odoo/addons/foggy_mcp/setup/foggy-models:/foggy-models:ro
```

### ~~2. 动态 Docker Compose 生成~~

~~向导从 Odoo 数据库配置自动生成 `docker-compose.yml`：~~

```python
# 已废弃：现在使用动态 DataSource API 配置
# def _generate_docker_compose(self):
#     ...
```

### ~~3. 闭包表一键初始化~~

向导直接通过 Odoo 的 `cr` 执行 SQL：

```python
def action_init_closure_tables(self):
    """一键初始化闭包表"""
    sql_path = os.path.join(
        os.path.dirname(__file__), '..', 'setup', 'sql',
        'refresh_closure_tables.sql')
    with open(sql_path) as f:
        sql = f.read()
    self.env.cr.execute(sql)
    # 执行刷新
    self.env.cr.execute("SELECT refresh_all_closures()")
    result = self.env.cr.fetchone()
    return {
        'type': 'ir.actions.client',
        'tag': 'display_notification',
        'params': {
            'title': 'Closure Tables Initialized',
            'message': f'4 closure tables created and populated.',
            'type': 'success',
        }
    }
```

### ~~4. 连接测试~~

```python
def action_test_connection(self):
    """测试 Foggy MCP Server 连通性"""
    import requests
    url = self.foggy_url or 'http://localhost:7108'
    try:
        r = requests.get(f'{url}/actuator/health', timeout=5)
        if r.status_code == 200:
            # 保存配置
            self.env['ir.config_parameter'].sudo().set_param(
                'foggy_mcp.server_url', url)
            return self._notify_success('Foggy MCP Server connected!')
        return self._notify_error(f'HTTP {r.status_code}')
    except requests.ConnectionError:
        return self._notify_error(f'Cannot reach {url}')
```

---

## ~~Docker Compose 模板（最终用户版）~~（已废弃）

```yaml
# 已废弃：使用动态 DataSource API
# 现在只需 docker run，数据源通过 API 配置
# docker run -d \
#   --name foggy-mcp \
#   -p 7108:8080 \
#   -e SPRING_PROFILES_ACTIVE=lite,odoo \
#   -e FOGGY_AUTH_TOKEN=your_token \
#   foggysource/foggy-odoo-mcp:v8.1.8-beta
```

---

## ~~非 Docker 方案（手动 JAR）~~（已废弃）

~~对于不使用 Docker 的用户，向导提供：~~

### ~~下载链接~~
```
~~https://github.com/nicholasgasior/foggy-data-mcp-bridge/releases/latest~~
~~→ foggy-mcp-launcher-x.x.x.jar~~
```

### ~~启动命令（自动生成）~~
```bash
# 已废弃：现在模型内置于 foggy-odoo-bridge-java 模块
# java -jar foggy-mcp-launcher-8.1.8.beta.jar \
#   --spring.profiles.active=lite \
#   ...
```

---

## ~~发布 Docker 镜像~~（已迁移）

Docker 镜像现在从 `foggy-odoo-bridge-java` 模块构建，包含内置的 TM/QM 模型。

---

## ~~实施优先级~~（已完成）

| 步骤 | 内容 | 状态 |
|---|---|---|
| 1 | ~~将 foggy-models/ 和 sql/ 复制到模块 setup/~~ | 已废弃 — 模型内置于 Docker 镜像 |
| 2 | 创建 Setup Wizard (模型 + 视图) | ✅ 完成 |
| 3 | 闭包表一键初始化（SQL 直接执行） | ✅ 完成 |
| 4 | Docker 命令生成（动态 DataSource） | ✅ 完成 |
| 5 | 连接测试 + 自动保存配置 | ✅ 完成 |
| 6 | 发布 Foggy Docker 镜像（内置模型） | ✅ 完成 |
| 7 | GitHub Release 自动发布 JAR | 配置 CI |
| 8 | 向导中嵌入视频教程链接 | P2 |
