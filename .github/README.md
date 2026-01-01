# GitHub Workflows

This directory contains GitHub Actions workflows for automating project tasks.

## Workflows

### `deploy-docs.yml` - VitePress Documentation Deployment

Automatically builds and deploys the VitePress documentation site to GitHub Pages.

**Triggers:**
- Push to `main` branch (when `docs-site/**` files change)
- Manual trigger via GitHub Actions UI

**What it does:**
1. Checks out the repository
2. Sets up Node.js 20
3. Installs dependencies from `docs-site/`
4. Builds the VitePress site
5. Deploys to GitHub Pages

**Deployment URL:**
```
https://foggy-projects.github.io/foggy-data-mcp-bridge/
```

## Initial Setup Required

After merging this workflow, you need to enable GitHub Pages:

1. Go to repository **Settings** → **Pages**
2. Under **Source**, select **GitHub Actions**
3. Save the settings

The workflow will automatically deploy on the next push to `main`.

## Manual Deployment

You can manually trigger the deployment:
1. Go to **Actions** tab
2. Select **Deploy VitePress Docs to GitHub Pages**
3. Click **Run workflow**
4. Select the branch and run

## Local Testing

To test the docs locally before deployment:

```bash
cd docs-site
npm install
npm run dev    # Development server
npm run build  # Build for production
npm run preview # Preview production build
```
