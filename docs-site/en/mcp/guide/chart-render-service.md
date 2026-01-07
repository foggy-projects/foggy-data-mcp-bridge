# Chart Render Service

The chart render service (chart-render-service) is a standalone Node.js microservice for rendering query data into visual charts.

## Features

- Multiple chart types: line, bar, pie, scatter, area
- Multi-series charts (year-over-year comparison, multi-dimension comparison)
- Responsive layout, auto-adapts to different sizes
- PNG/SVG output formats
- Auto-upload to cloud storage with URL return

## Installation

### Option 1: Docker Hub (Recommended)

Use the official pre-built image, no local build required:

```bash
# Create docker-compose.yml
cat > docker-compose.yml << 'EOF'
services:
  chart-render:
    image: foggysource/chart-render-service:latest
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
      - RENDER_TOKEN=your-token-here
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000/healthz"]
      interval: 30s
      timeout: 10s
      retries: 3
EOF

# Start service
docker compose up -d
```

### Option 2: Local Build

For customization or development:

```bash
# Enter service directory
cd addons/chart-render-service

# Build and start
docker compose up -d --build

# View logs
docker compose logs -f
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PORT` | Service port | 3000 |
| `NODE_ENV` | Runtime environment | development |
| `RENDER_TOKEN` | Access token | default-render-token |
| `OSS_*` | Cloud storage config | - |

### Cloud Storage (Optional)

To upload charts to cloud storage:

```yaml
environment:
  - OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
  - OSS_ACCESS_KEY_ID=your-access-key
  - OSS_ACCESS_KEY_SECRET=your-secret
  - OSS_BUCKET=your-bucket
  - OSS_REGION=oss-cn-hangzhou
```

## MCP Service Integration

Specify chart render service URL in MCP service configuration:

```yaml
# application.yml
foggy:
  mcp:
    chart:
      render-url: http://localhost:3000
      render-token: your-token-here
```

## Verify Service

```bash
# Health check
curl http://localhost:3000/healthz

# Test render (requires POST chart config)
curl -X POST http://localhost:3000/render/unified \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-token-here" \
  -d '{
    "unified": {
      "type": "line",
      "title": "Test Chart",
      "xField": "month",
      "yField": "value"
    },
    "data": [
      {"month": "Jan", "value": 100},
      {"month": "Feb", "value": 150}
    ]
  }'
```

## Supported Chart Types

| Type | Description | Use Case |
|------|-------------|----------|
| `line` | Line chart | Trend analysis, time series |
| `bar` | Horizontal bar | Category comparison |
| `column` | Vertical bar | Category comparison |
| `pie` | Pie chart | Proportion analysis |
| `doughnut` | Doughnut chart | Proportion analysis |
| `scatter` | Scatter plot | Correlation analysis |
| `area` | Area chart | Trend + cumulative |

## Multi-Series Charts

Use `seriesField` parameter for multi-series charts:

```json
{
  "unified": {
    "type": "line",
    "title": "2024-2025 Monthly Sales Comparison",
    "xField": "month",
    "yField": "amount",
    "seriesField": "year",
    "showLegend": true
  },
  "data": [
    {"year": "2024", "month": "1", "amount": 1000},
    {"year": "2024", "month": "2", "amount": 1200},
    {"year": "2025", "month": "1", "amount": 1100},
    {"year": "2025", "month": "2", "amount": 1400}
  ]
}
```

## Troubleshooting

### Service Won't Start

```bash
# Check port usage
netstat -tlnp | grep 3000

# View detailed logs
docker compose logs chart-render
```

### Chart Render Failed

1. Check if data format is correct
2. Verify field names match configuration
3. Check service logs for detailed error messages

### Chinese Characters Display Issues

Ensure Chinese fonts are installed in container. Official image includes Noto Sans CJK fonts.

## Update Image

```bash
# Pull latest image
docker pull foggysource/chart-render-service:latest

# Restart service
docker compose down
docker compose up -d
```
