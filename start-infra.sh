#!/bin/bash
# 一键启动 cpms 基础设施：Neo4j + Elasticsearch
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "===== 启动 cpms 基础设施 ====="

# ---- Neo4j ----
echo ""
echo "[1/2] 启动 Neo4j (cpms-neo4j)..."
if docker ps --format '{{.Names}}' | grep -q '^cpms-neo4j$'; then
    echo "  Neo4j 已在运行中"
else
    docker rm cpms-neo4j 2>/dev/null || true
    cd "$SCRIPT_DIR/docker/neo4j" && docker compose up -d
    echo "  Neo4j 启动成功"
fi

# ---- Elasticsearch ----
echo ""
echo "[2/2] 启动 Elasticsearch (es-server)..."
if docker ps --format '{{.Names}}' | grep -q '^es-server$'; then
    echo "  Elasticsearch 已在运行中"
elif docker ps -a --format '{{.Names}}' | grep -q '^es-server$'; then
    docker start es-server
    echo "  Elasticsearch 已启动"
else
    docker run -d --name es-server \
        -p 9200:9200 -p 9300:9300 \
        -e "discovery.type=single-node" \
        -e "xpack.security.enabled=false" \
        elasticsearch:7.17.9
    echo "  Elasticsearch 创建并启动成功"
fi

# ---- 状态 ----
echo ""
echo "===== 当前状态 ====="
docker ps --filter "name=cpms-neo4j" --filter "name=es-server" \
    --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || \
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
