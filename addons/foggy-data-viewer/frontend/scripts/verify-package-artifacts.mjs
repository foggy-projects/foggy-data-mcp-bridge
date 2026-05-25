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

if (missing.length > 0) {
  console.error('Package artifact verification failed:')
  for (const item of missing) {
    console.error(`- ${item}`)
  }
  process.exit(1)
}

console.log('Package artifacts verified.')
