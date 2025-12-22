import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Foggy Data MCP Bridge',
  description: 'Embedded semantic layer framework for AI-driven data analysis',

  head: [
    ['link', { rel: 'icon', href: '/logo.svg' }]
  ],

  locales: {
    zh: {
      label: '简体中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: [
          { text: '快速开始', link: '/zh/quick-start/introduction' },
          {
            text: '技术文档',
            items: [
              { text: 'FSScript 脚本引擎', link: '/zh/fsscript/guide/introduction' },
              { text: '数据查询', link: '/zh/dataset-query/guide/introduction' },
              { text: '数据建模', link: '/zh/dataset-model/guide/introduction' },
              { text: 'MCP 服务', link: '/zh/mcp/guide/introduction' }
            ]
          },
          { text: 'GitHub', link: 'https://github.com/foggy-projects/foggy-data-mcp-bridge' }
        ],
        sidebar: {
          '/zh/quick-start/': [
            {
              text: '快速开始',
              items: [
                { text: '什么是 Foggy MCP', link: '/zh/quick-start/introduction' },
                { text: 'Docker 快速部署', link: '/zh/quick-start/docker-setup' },
                { text: '配置 Claude Desktop', link: '/zh/quick-start/claude-desktop' },
                { text: '配置 Cursor', link: '/zh/quick-start/cursor' },
                { text: '第一次查询', link: '/zh/quick-start/first-query' }
              ]
            }
          ],
          '/zh/fsscript/': [
            {
              text: '开始',
              items: [
                { text: '简介', link: '/zh/fsscript/guide/introduction' },
                { text: '快速开始', link: '/zh/fsscript/guide/quick-start' },
                { text: '为什么用 FSScript', link: '/zh/fsscript/guide/why-fsscript' }
              ]
            },
            {
              text: '语法指南',
              items: [
                { text: '变量与类型', link: '/zh/fsscript/syntax/variables' },
                { text: '函数与闭包', link: '/zh/fsscript/syntax/functions' },
                { text: '数组与对象', link: '/zh/fsscript/syntax/arrays-objects' },
                { text: '控制流', link: '/zh/fsscript/syntax/control-flow' },
                { text: '模板字符串', link: '/zh/fsscript/syntax/template-strings' },
                { text: '模块系统', link: '/zh/fsscript/syntax/modules' }
              ]
            },
            {
              text: '内置功能',
              items: [
                { text: '内置函数', link: '/zh/fsscript/syntax/builtin-functions' },
                { text: '运算符', link: '/zh/fsscript/syntax/operators' }
              ]
            },
            {
              text: 'Java 集成',
              items: [
                { text: '快速开始', link: '/zh/fsscript/java/quick-start' },
                { text: 'Spring Boot 集成', link: '/zh/fsscript/java/spring-boot' },
                { text: 'JSR-223 接口', link: '/zh/fsscript/java/jsr223' },
                { text: 'API 参考', link: '/zh/fsscript/java/api-reference' }
              ]
            }
          ],
          '/zh/dataset-query/': [
            {
              text: '开始',
              items: [
                { text: '简介', link: '/zh/dataset-query/guide/introduction' },
                { text: '快速开始', link: '/zh/dataset-query/guide/quick-start' },
                { text: '多数据库支持', link: '/zh/dataset-query/guide/multi-database' }
              ]
            },
            {
              text: 'API 参考',
              items: [
                { text: '查询 API', link: '/zh/dataset-query/api/query-api' },
                { text: '方言扩展', link: '/zh/dataset-query/api/dialect' }
              ]
            }
          ],
          '/zh/dataset-model/': [
            {
              text: '开始',
              items: [
                { text: '简介', link: '/zh/dataset-model/guide/introduction' },
                { text: '快速开始', link: '/zh/dataset-model/guide/quick-start' },
                { text: '核心概念', link: '/zh/dataset-model/guide/concepts' }
              ]
            },
            {
              text: 'JM/QM 建模',
              items: [
                { text: 'JM 语法手册', link: '/zh/dataset-model/jm-qm/jm-syntax' },
                { text: 'QM 语法手册', link: '/zh/dataset-model/jm-qm/qm-syntax' },
                { text: '计算字段', link: '/zh/dataset-model/jm-qm/calculated-fields' },
                { text: '父子维度', link: '/zh/dataset-model/jm-qm/parent-child' }
              ]
            },
            {
              text: 'API 参考',
              items: [
                { text: '查询 API', link: '/zh/dataset-model/api/query-api' },
                { text: '权限控制', link: '/zh/dataset-model/api/authorization' }
              ]
            }
          ],
          '/zh/mcp/': [
            {
              text: '开始',
              items: [
                { text: '简介', link: '/zh/mcp/guide/introduction' },
                { text: '快速开始', link: '/zh/mcp/guide/quick-start' },
                { text: '架构概述', link: '/zh/mcp/guide/architecture' }
              ]
            },
            {
              text: 'MCP 工具',
              items: [
                { text: '工具列表', link: '/zh/mcp/tools/overview' },
                { text: '元数据工具', link: '/zh/mcp/tools/metadata' },
                { text: '查询工具', link: '/zh/mcp/tools/query' },
                { text: '自然语言查询', link: '/zh/mcp/tools/nl-query' }
              ]
            },
            {
              text: '集成指南',
              items: [
                { text: 'Claude Desktop', link: '/zh/mcp/integration/claude-desktop' },
                { text: 'Cursor', link: '/zh/mcp/integration/cursor' },
                { text: 'API 调用', link: '/zh/mcp/integration/api' }
              ]
            }
          ]
        },
        outline: {
          label: '页面导航'
        },
        docFooter: {
          prev: '上一页',
          next: '下一页'
        }
      }
    },
    en: {
      label: 'English',
      lang: 'en-US',
      link: '/en/',
      themeConfig: {
        nav: [
          {
            text: 'Docs',
            items: [
              { text: 'FSScript Engine', link: '/en/fsscript/guide/introduction' },
              { text: 'Dataset Modeling', link: '/en/dataset/guide/introduction' },
              { text: 'MCP Service', link: '/en/mcp/guide/introduction' }
            ]
          },
          { text: 'GitHub', link: 'https://github.com/foggy-projects/foggy-data-mcp-bridge' }
        ],
        sidebar: {
          '/en/fsscript/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'Introduction', link: '/en/fsscript/guide/introduction' },
                { text: 'Quick Start', link: '/en/fsscript/guide/quick-start' },
                { text: 'Why FSScript', link: '/en/fsscript/guide/why-fsscript' }
              ]
            },
            {
              text: 'Syntax Guide',
              items: [
                { text: 'Variables & Types', link: '/en/fsscript/syntax/variables' },
                { text: 'Functions & Closures', link: '/en/fsscript/syntax/functions' },
                { text: 'Arrays & Objects', link: '/en/fsscript/syntax/arrays-objects' },
                { text: 'Control Flow', link: '/en/fsscript/syntax/control-flow' },
                { text: 'Template Strings', link: '/en/fsscript/syntax/template-strings' },
                { text: 'Modules', link: '/en/fsscript/syntax/modules' }
              ]
            },
            {
              text: 'Built-in Features',
              items: [
                { text: 'Built-in Functions', link: '/en/fsscript/syntax/builtin-functions' },
                { text: 'Operators', link: '/en/fsscript/syntax/operators' }
              ]
            },
            {
              text: 'Java Integration',
              items: [
                { text: 'Quick Start', link: '/en/fsscript/java/quick-start' },
                { text: 'Spring Boot', link: '/en/fsscript/java/spring-boot' },
                { text: 'JSR-223 Interface', link: '/en/fsscript/java/jsr223' },
                { text: 'API Reference', link: '/en/fsscript/java/api-reference' }
              ]
            }
          ],
          '/en/dataset/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'Introduction', link: '/en/dataset/guide/introduction' },
                { text: 'Quick Start', link: '/en/dataset/guide/quick-start' },
                { text: 'Core Concepts', link: '/en/dataset/guide/concepts' }
              ]
            },
            {
              text: 'JM/QM Modeling',
              items: [
                { text: 'JM Syntax Manual', link: '/en/dataset/jm-qm/jm-syntax' },
                { text: 'QM Syntax Manual', link: '/en/dataset/jm-qm/qm-syntax' },
                { text: 'Calculated Fields', link: '/en/dataset/jm-qm/calculated-fields' },
                { text: 'Parent-Child Dimension', link: '/en/dataset/jm-qm/parent-child' }
              ]
            },
            {
              text: 'API Reference',
              items: [
                { text: 'Query API', link: '/en/dataset/api/query-api' },
                { text: 'Authorization', link: '/en/dataset/api/authorization' }
              ]
            }
          ],
          '/en/mcp/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'Introduction', link: '/en/mcp/guide/introduction' },
                { text: 'Quick Start', link: '/en/mcp/guide/quick-start' },
                { text: 'Architecture', link: '/en/mcp/guide/architecture' }
              ]
            },
            {
              text: 'MCP Tools',
              items: [
                { text: 'Tools Overview', link: '/en/mcp/tools/overview' },
                { text: 'Metadata Tool', link: '/en/mcp/tools/metadata' },
                { text: 'Query Tool', link: '/en/mcp/tools/query' },
                { text: 'NL Query', link: '/en/mcp/tools/nl-query' }
              ]
            },
            {
              text: 'Integration',
              items: [
                { text: 'Claude Desktop', link: '/en/mcp/integration/claude-desktop' },
                { text: 'Cursor', link: '/en/mcp/integration/cursor' },
                { text: 'API Usage', link: '/en/mcp/integration/api' }
              ]
            }
          ]
        }
      }
    }
  },

  themeConfig: {
    logo: '/logo.svg',

    socialLinks: [
      { icon: 'github', link: 'https://github.com/foggy-projects/foggy-data-mcp-bridge' }
    ],

    search: {
      provider: 'local'
    }
  }
})
