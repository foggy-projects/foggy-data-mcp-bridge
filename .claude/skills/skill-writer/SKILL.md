---
name: skill-writer
description: 快速生成简单的 SKILL.md 文件（纯指令型技能）。当用户要求"快速创建skill"、"生成SKILL.md模板"、将提示词转换为技能时使用。对于需要脚本/资源/打包工具的复杂技能，使用 skill-creator。
---

# Skill Writer

快速为用户创建符合规范的 Claude Code 技能文件（SKILL.md）。

**适用场景：** 创建纯指令型技能，无需脚本、参考文档或资产文件。

**复杂技能请使用：** `skill-creator` - 提供完整的技能创建框架、工具脚本和设计模式参考。

## 核心原则

**技能是给 AI 执行的指令，不是给人类阅读的文档。**

生成技能时必须：
- 使用指令式语言，不使用教程式解释
- 提供明确的执行步骤和输出格式
- 包含决策规则（if-then 逻辑）
- 省略对 AI 无用的内容（故障排除、最佳实践建议等）

## 执行流程

1. **收集必要信息**（如用户未提供则询问）：
   - 技能名称
   - 核心功能（一句话）
   - 触发场景（何时使用）
   - 存放位置：个人 `~/.claude/skills/` 或项目 `.claude/skills/`

2. **生成 SKILL.md 文件**（见输出模板）

3. **如需要，创建辅助文件**：
   - `reference.md` — 详细参考信息
   - `examples.md` — 扩展示例
   - `scripts/` — 辅助脚本
   - `templates/` — 文件模板

## 约束条件

| 字段 | 规则 |
|------|------|
| name | 正则 `^[a-z0-9-]{1,64}$`，必须与目录名一致 |
| description | ≤1024 字符，格式：`功能描述 + 使用时机/触发词` |
| allowed-tools | 可选，逗号分隔的工具列表，用于限制技能可用工具 |

目录结构：
```
{skill-name}/
├── SKILL.md        # 必需
├── reference.md    # 可选，详细参考
├── examples.md     # 可选，更多示例
└── scripts/        # 可选，辅助脚本
```

## 决策规则

- 用户未指定位置 → 询问是个人技能还是项目技能
- 技能仅需读取文件 → 添加 `allowed-tools: Read, Grep, Glob`
- 技能需要网络请求 → 添加 `allowed-tools` 包含 `WebFetch`
- 技能涉及敏感操作 → 在执行步骤中加入确认环节
- 用户提供现有提示词 → 提取核心逻辑，转换为结构化技能格式
- 技能内容超过 200 行 → 拆分到 reference.md 或 examples.md

## 输出模板

```markdown
---
name: {技能名称}
description: {功能描述}。当用户{触发场景}时使用。
---

# {技能标题}

{一句话概述技能功能}

## 使用场景

当用户需要以下操作时使用：
- {场景1}
- {场景2}

## 执行流程

1. {步骤1：具体操作}
2. {步骤2：具体操作}
3. {步骤3：输出结果}

## 输入要求

用户需提供：
- {必需输入1}
- {可选输入2}（如未提供则{默认行为}）

## 输出格式

{明确的输出结构或模板}

## 约束条件

- {约束1}
- {约束2}

## 决策规则

- 如果{条件1} → {操作1}
- 如果{条件2} → {操作2}
```

## 技能内容编写规范

### 必须包含

1. **执行流程**：编号的具体步骤，AI 按顺序执行
2. **输出格式**：明确的输出结构，避免模糊描述
3. **约束条件**：硬性规则，AI 必须遵守

### 建议包含

4. **使用场景**：帮助 AI 判断何时激活技能
5. **输入要求**：需要从用户获取的信息
6. **决策规则**：if-then 格式的条件判断

### 避免包含

- 教程式解释（"首先，让我们理解..."）
- 给人类的故障排除指南
- 冗长的背景介绍
- Checkbox 格式的清单
- "最佳实践建议"等软性指导

## 示例

### 输入
用户：帮我创建一个生成 git commit message 的技能

### 输出

```markdown
---
name: commit-message
description: 根据 staged changes 生成规范的 git commit message。当用户要求生成 commit message、提交代码、或使用 /commit 时使用。
---

# Commit Message Generator

根据当前 staged changes 生成符合 Conventional Commits 规范的提交信息。

## 执行流程

1. 运行 `git diff --cached` 获取 staged changes
2. 分析变更内容，确定 commit 类型（feat/fix/refactor/docs/chore）
3. 生成 commit message，格式见输出格式
4. 展示给用户确认，用户确认后执行 `git commit`

## 输出格式

\`\`\`
{type}({scope}): {简短描述}

{详细说明，如有必要}

Co-Authored-By: Claude <noreply@anthropic.com>
\`\`\`

## 约束条件

- type 必须是: feat, fix, refactor, docs, chore, test, style
- 简短描述 ≤50 字符，使用祈使语气
- 不自动执行 commit，必须等用户确认

## 决策规则

- 如果无 staged changes → 提示用户先 `git add`
- 如果变更涉及多个不相关功能 → 建议用户拆分为多次提交
- 如果检测到敏感文件（.env, credentials）→ 警告用户
```

---

## 何时使用 skill-creator

当技能需要以下功能时，请使用 `skill-creator` 而不是此技能：

### 需要捆绑资源
- **Scripts** (`scripts/`) - 可执行脚本（Python/Bash等）
  - 示例：PDF处理、图片编辑、数据转换脚本
- **References** (`references/`) - 参考文档（需要时加载到上下文）
  - 示例：数据库架构、API文档、公司政策
- **Assets** (`assets/`) - 输出中使用的文件
  - 示例：模板文件、品牌资产、样板代码

### 需要工具支持
- 自动初始化技能目录 (`init_skill.py`)
- 打包和验证工具 (`package_skill.py`)
- 快速验证脚本 (`quick_validate.py`)

### 需要设计模式参考
- 渐进式披露设计原则
- 工作流模式（顺序/条件工作流）
- 输出模式（模板/示例模式）
- 完整的6步创建流程

### 使用方式
直接调用 `skill-creator` 技能，或查看其文档：
- 英文版：`.claude/skills/skill-creator/SKILL.md`
- 中文版：`.claude/skills/skill-creator/SKILL.zh.md`
- 参考文档：`.claude/skills/skill-creator/references/`
