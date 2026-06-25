#!/usr/bin/env node
/**
 * 初始化测试数据库
 *
 * 用法:
 *   node setup_test_db.js [options]
 *
 * 选项:
 *   --db-type <type>     数据库类型 (sqlite|postgres|mysql) [默认: sqlite]
 *   --db-name <name>     数据库名称 [默认: test_db]
 *   --seed               是否填充种子数据
 *   --reset              重置数据库（删除现有数据）
 *   --help               显示此帮助信息
 *
 * 示例:
 *   node setup_test_db.js
 *   node setup_test_db.js --seed
 *   node setup_test_db.js --db-type postgres --reset --seed
 *
 * 环境变量:
 *   TEST_DB_TYPE         数据库类型
 *   TEST_DB_NAME         数据库名称
 *   TEST_DB_HOST         数据库主机 (postgres/mysql)
 *   TEST_DB_PORT         数据库端口
 *   TEST_DB_USER         数据库用户
 *   TEST_DB_PASSWORD     数据库密码
 */

const fs = require('fs')
const path = require('path')

// 解析命令行参数
function parseArgs() {
  const args = process.argv.slice(2)
  const options = {
    dbType: process.env.TEST_DB_TYPE || 'sqlite',
    dbName: process.env.TEST_DB_NAME || 'test_db',
    seed: false,
    reset: false,
    help: false
  }

  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case '--db-type':
        options.dbType = args[++i]
        break
      case '--db-name':
        options.dbName = args[++i]
        break
      case '--seed':
        options.seed = true
        break
      case '--reset':
        options.reset = true
        break
      case '--help':
        options.help = true
        break
      default:
        console.error(`未知选项: ${args[i]}`)
        process.exit(1)
    }
  }

  return options
}

// 显示帮助信息
function showHelp() {
  const helpText = fs.readFileSync(__filename, 'utf8')
    .split('\n')
    .filter(line => line.trim().startsWith('*'))
    .map(line => line.replace(/^\s*\*\s?/, ''))
    .join('\n')
  console.log(helpText)
}

// SQLite 数据库设置
async function setupSQLite(options) {
  try {
    // 尝试导入 better-sqlite3
    const Database = require('better-sqlite3')

    const dbPath = path.join(process.cwd(), `${options.dbName}.sqlite`)

    if (options.reset && fs.existsSync(dbPath)) {
      console.log('🗑️  删除现有数据库...')
      fs.unlinkSync(dbPath)
    }

    console.log(`📁 创建 SQLite 数据库: ${dbPath}`)
    const db = new Database(dbPath)

    // 创建基础表结构
    console.log('📊 创建表结构...')
    db.exec(`
      CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        email TEXT UNIQUE NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS posts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        title TEXT NOT NULL,
        content TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      );
    `)

    if (options.seed) {
      console.log('🌱 填充种子数据...')
      const insertUser = db.prepare('INSERT INTO users (name, email) VALUES (?, ?)')
      const insertPost = db.prepare('INSERT INTO posts (user_id, title, content) VALUES (?, ?, ?)')

      insertUser.run('测试用户1', 'user1@test.com')
      insertUser.run('测试用户2', 'user2@test.com')
      insertPost.run(1, '第一篇文章', '这是测试内容')
      insertPost.run(1, '第二篇文章', '这也是测试内容')
      insertPost.run(2, '另一篇文章', '来自用户2的内容')
    }

    db.close()
    console.log('✅ SQLite 数据库初始化完成')
    return dbPath

  } catch (error) {
    if (error.code === 'MODULE_NOT_FOUND') {
      console.error('❌ 未找到 better-sqlite3，请先安装: npm install -D better-sqlite3')
    } else {
      console.error('❌ 数据库初始化失败:', error.message)
    }
    process.exit(1)
  }
}

// PostgreSQL 数据库设置
async function setupPostgres(options) {
  console.log('🐘 PostgreSQL 数据库设置')
  console.log('提示: 这是一个示例实现，需要根据实际项目调整')

  const config = {
    host: process.env.TEST_DB_HOST || 'localhost',
    port: process.env.TEST_DB_PORT || 5432,
    user: process.env.TEST_DB_USER || 'postgres',
    password: process.env.TEST_DB_PASSWORD || 'postgres',
    database: options.dbName
  }

  console.log(`📡 连接信息: ${config.user}@${config.host}:${config.port}/${config.database}`)
  console.log('⚠️  请确保已安装 pg: npm install -D pg')

  // 实际实现需要导入 pg 并执行 SQL
  console.log('ℹ️  请参考项目文档完成 PostgreSQL 配置')
}

// MySQL 数据库设置
async function setupMySQL(options) {
  console.log('🐬 MySQL 数据库设置')
  console.log('提示: 这是一个示例实现，需要根据实际项目调整')

  const config = {
    host: process.env.TEST_DB_HOST || 'localhost',
    port: process.env.TEST_DB_PORT || 3306,
    user: process.env.TEST_DB_USER || 'root',
    password: process.env.TEST_DB_PASSWORD || '',
    database: options.dbName
  }

  console.log(`📡 连接信息: ${config.user}@${config.host}:${config.port}/${config.database}`)
  console.log('⚠️  请确保已安装 mysql2: npm install -D mysql2')

  console.log('ℹ️  请参考项目文档完成 MySQL 配置')
}

// 主函数
async function main() {
  const options = parseArgs()

  if (options.help) {
    showHelp()
    return
  }

  console.log('🚀 开始初始化测试数据库...')
  console.log(`📝 数据库类型: ${options.dbType}`)
  console.log(`📝 数据库名称: ${options.dbName}`)
  console.log(`📝 填充种子: ${options.seed ? '是' : '否'}`)
  console.log(`📝 重置数据库: ${options.reset ? '是' : '否'}`)
  console.log('')

  switch (options.dbType.toLowerCase()) {
    case 'sqlite':
      await setupSQLite(options)
      break
    case 'postgres':
    case 'postgresql':
      await setupPostgres(options)
      break
    case 'mysql':
      await setupMySQL(options)
      break
    default:
      console.error(`❌ 不支持的数据库类型: ${options.dbType}`)
      console.log('支持的类型: sqlite, postgres, mysql')
      process.exit(1)
  }
}

// 导出函数供测试使用
if (require.main === module) {
  main().catch(error => {
    console.error('❌ 发生错误:', error)
    process.exit(1)
  })
} else {
  module.exports = { setupSQLite, setupPostgres, setupMySQL }
}
