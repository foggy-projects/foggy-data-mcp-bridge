import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');
const configPath = path.join(rootDir, '.vitepress', 'downloads-config.json');
const publicDir = path.join(rootDir, 'public', 'downloads');

try {
  const config = JSON.parse(fs.readFileSync(configPath, 'utf-8'));
  
  if (!fs.existsSync(publicDir)) {
    fs.mkdirSync(publicDir, { recursive: true });
  }

  config.downloads.forEach(item => {
    const sourcePath = path.join(rootDir, item.source);
    const destPath = path.join(publicDir, item.filename);

    if (fs.existsSync(sourcePath)) {
      fs.copyFileSync(sourcePath, destPath);
      console.log(`✓ Copied: ${item.filename}`);
    } else {
      console.warn(`✗ Not found: ${item.source}`);
    }
  });

  console.log('\nDownload files copied successfully!');
} catch (error) {
  console.error('Error copying download files:', error.message);
  process.exit(1);
}
