import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');
const configPath = path.join(rootDir, '.vitepress', 'downloads-config.json');
const publicDir = path.join(rootDir, 'public', 'downloads');
const checkOnly = process.argv.includes('--check');

function filesAreEqual(sourcePath, destPath) {
  if (!fs.existsSync(destPath)) {
    return false;
  }

  const sourceStat = fs.statSync(sourcePath);
  const destStat = fs.statSync(destPath);

  if (sourceStat.size !== destStat.size) {
    return false;
  }

  return fs.readFileSync(sourcePath).equals(fs.readFileSync(destPath));
}

try {
  const config = JSON.parse(fs.readFileSync(configPath, 'utf-8'));
  let missingCount = 0;
  let outdatedCount = 0;
  let copiedCount = 0;
  let unchangedCount = 0;
  
  if (!checkOnly && !fs.existsSync(publicDir)) {
    fs.mkdirSync(publicDir, { recursive: true });
  }

  config.downloads.forEach(item => {
    const sourcePath = path.join(rootDir, item.source);
    const destPath = path.join(publicDir, item.filename);

    if (!fs.existsSync(sourcePath)) {
      missingCount += 1;
      console.warn(`✗ Source not found: ${item.source}`);
      return;
    }

    if (filesAreEqual(sourcePath, destPath)) {
      unchangedCount += 1;
      console.log(`- Unchanged: ${item.filename}`);
      return;
    }

    outdatedCount += 1;

    if (checkOnly) {
      console.warn(`✗ Out of date: ${item.filename}`);
    } else {
      fs.copyFileSync(sourcePath, destPath);
      copiedCount += 1;
      console.log(`✓ Copied: ${item.filename}`);
    }
  });

  if (checkOnly && (missingCount > 0 || outdatedCount > 0)) {
    console.error(`\nDownload check failed: ${missingCount} missing source(s), ${outdatedCount} out-of-date file(s).`);
    process.exit(1);
  }

  if (checkOnly) {
    console.log(`\nDownload check passed: ${unchangedCount} file(s) up to date.`);
  } else {
    console.log(`\nDownload files ready: ${copiedCount} copied, ${unchangedCount} unchanged, ${missingCount} missing source(s).`);
  }
} catch (error) {
  console.error('Error copying download files:', error.message);
  process.exit(1);
}
