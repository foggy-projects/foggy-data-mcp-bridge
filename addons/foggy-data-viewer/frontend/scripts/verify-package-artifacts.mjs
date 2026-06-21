import { existsSync, statSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
const packageJsonPath = resolve(root, 'package.json')
const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf8'))

const requiredPaths = new Map([
  ['main', packageJson.main],
  ['module', packageJson.module],
  ['types', packageJson.types],
  ['exports["."].import', packageJson.exports?.['.']?.import],
  ['exports["."].default', packageJson.exports?.['.']?.default],
  ['exports["."].types', packageJson.exports?.['.']?.types],
  ['exports["./style.css"]', packageJson.exports?.['./style.css']]
])

const missing = []

for (const [label, relativePath] of requiredPaths) {
  if (!relativePath) {
    missing.push(`${label} is not declared`)
    continue
  }

  const target = resolve(root, relativePath)
  if (!existsSync(target)) {
    missing.push(`${label} -> ${relativePath} does not exist`)
    continue
  }

  if (statSync(target).size === 0) {
    missing.push(`${label} -> ${relativePath} is empty`)
  }
}

const stylePath = packageJson.exports?.['./style.css']
if (stylePath) {
  const styleCss = readFileSync(resolve(root, stylePath), 'utf8')
  const requiredStyleContracts = new Map([
    ['DataTable root layout', '.data-table{'],
    ['DataTable table wrapper layout', '.data-table .table-wrapper{'],
    ['DataTable vxe grid height bridge', '.data-table .table-wrapper>.vxe-grid,.data-table .table-wrapper>.vxe-table{'],
    ['DataTable header slot wrapper', '.data-table .column-header-wrapper{'],
    ['DataTable header title', '.data-table .column-title{'],
    ['DataTable help icon', '.data-table .column-help-icon{'],
    ['DataTable sort icon', '.data-table .sort-icon{'],
    ['DataTable column filter input', '.data-table .column-filter input:not(.el-range-input):not(.el-input__inner),.data-table .column-filter select{'],
    ['DataTable copyable cell', '.data-table .data-table-copyable-cell{'],
    ['DataTable copy button', '.data-table .cell-copy-button{'],
    ['DataTableWithSearch root layout', '.data-table-with-search{'],
    ['DataTableWithSearch table wrapper layout', '.data-table-with-search>.data-table-wrapper{'],
    ['DataTableWithSearch child table layout', '.data-table-with-search>.data-table-wrapper>.data-table{']
  ])

  for (const [label, selector] of requiredStyleContracts) {
    if (!styleCss.includes(selector)) {
      missing.push(`${label} style contract is missing from ${stylePath}`)
    }
  }
}

if (missing.length > 0) {
  console.error('Package artifact verification failed:')
  for (const item of missing) {
    console.error(`- ${item}`)
  }
  process.exit(1)
}

console.log('Package artifacts verified.')
