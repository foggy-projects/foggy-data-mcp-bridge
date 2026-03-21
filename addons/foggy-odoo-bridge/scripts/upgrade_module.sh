#!/bin/bash
# -*- coding: utf-8 -*-
#
# Odoo Module Upgrade Script
# Restarts Odoo container and upgrades a specified module
#
# Usage:
#   ./upgrade_module.sh [module_name] [database_name] [container_name]
#
# Examples:
#   ./upgrade_module.sh                    # Upgrade foggy_mcp in odoo database
#   ./upgrade_module.sh foggy_mcp          # Upgrade foggy_mcp in odoo database
#   ./upgrade_module.sh foggy_mcp odoo     # Upgrade foggy_mcp in odoo database
#   ./upgrade_module.sh sale odoo odoo-17  # Upgrade sale in odoo database, custom container
#

set -e

# Default values
MODULE_NAME="${1:-foggy_mcp}"
DATABASE_NAME="${2:-odoo}"
CONTAINER_NAME="${3:-foggy-odoo}"
WAIT_SECONDS="${WAIT_SECONDS:-5}"

echo "=========================================="
echo "Odoo Module Upgrade Script"
echo "=========================================="
echo "Container:  ${CONTAINER_NAME}"
echo "Database:   ${DATABASE_NAME}"
echo "Module:     ${MODULE_NAME}"
echo "=========================================="

# Check if container exists
if ! docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "Error: Container '${CONTAINER_NAME}' not found"
    echo "Available containers:"
    docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"
    exit 1
fi

# Restart container
echo ""
echo "[1/3] Restarting container '${CONTAINER_NAME}'..."
docker restart "${CONTAINER_NAME}"

echo ""
echo "[2/3] Waiting ${WAIT_SECONDS} seconds for container to be ready..."
sleep "${WAIT_SECONDS}"

# Upgrade module
echo ""
echo "[3/3] Upgrading module '${MODULE_NAME}' in database '${DATABASE_NAME}'..."
docker exec "${CONTAINER_NAME}" bash -c "odoo -d ${DATABASE_NAME} -u ${MODULE_NAME} --stop-after-init"

echo ""
echo "=========================================="
echo "Module upgrade completed successfully!"
echo "=========================================="