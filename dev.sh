#!/bin/bash
# StreamTide Development Environment - One Command Startup
# Usage: ./dev.sh

set -e

echo "=========================================="
echo "  StreamTide Dev Environment"
echo "=========================================="

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check prerequisites
command -v docker >/dev/null 2>&1 || { echo "Docker is required but not installed. Aborting."; exit 1; }
command -v bb >/dev/null 2>&1 || { echo "Babashka (bb) is required but not installed. Aborting."; exit 1; }
command -v node >/dev/null 2>&1 || { echo "Node.js is required but not installed. Aborting."; exit 1; }

# Start Docker services (PostgreSQL + Ganache)
echo -e "${GREEN}[1/5] Starting Docker services (PostgreSQL + Ganache)...${NC}"
docker compose up -d db ganache 2>/dev/null || docker-compose up -d db ganache

# Wait for services to be healthy
echo -e "${GREEN}[2/5] Waiting for services to be ready...${NC}"
sleep 3

# Check if contracts need to be deployed
# We check if there's code at the streamtide contract address
# If Ganache was restarted, the contracts won't exist and we need to redeploy
echo -e "${GREEN}[3/5] Checking smart contracts...${NC}"
STREAMTIDE_ADDR=$(grep -o '0x[a-fA-F0-9]\{40\}' "./src/streamtide/shared/smart_contracts_dev.cljs" 2>/dev/null | head -1)

# Try to get code at the address - if empty, contracts need to be deployed
NEEDS_DEPLOY=false
if [ -z "$STREAMTIDE_ADDR" ]; then
    NEEDS_DEPLOY=true
else
    CODE=$(curl -s -X POST -H "Content-Type: application/json" --data "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getCode\",\"params\":[\"$STREAMTIDE_ADDR\", \"latest\"],\"id\":1}" http://127.0.0.1:8545 | grep -o '"result":"[^"]*"' | cut -d'"' -f4)
    if [ "$CODE" = "0x" ] || [ -z "$CODE" ]; then
        NEEDS_DEPLOY=true
    fi
fi

if [ "$NEEDS_DEPLOY" = true ]; then
    echo -e "${YELLOW}    Deploying smart contracts (fresh Ganache detected)...${NC}"
    STREAMTIDE_ENV=dev npx truffle migrate --network ganache --reset
else
    echo -e "${GREEN}    Smart contracts already deployed, skipping...${NC}"
fi

# Install deps if needed
if [ ! -d "node_modules" ]; then
    echo -e "${GREEN}[4/5] Installing dependencies...${NC}"
    npm install --legacy-peer-deps --ignore-scripts
    npm rebuild sqlite3
fi

# Compile CSS
echo -e "${GREEN}[4/5] Compiling CSS...${NC}"
bb compile-css

# Start dev servers
echo -e "${GREEN}[5/5] Starting development servers...${NC}"
echo ""
echo "=========================================="
echo -e "${GREEN}  StreamTide is starting up!${NC}"
echo "=========================================="
echo ""
echo "  UI:          http://localhost:4598"
echo "  GraphQL:     http://localhost:6300/graphql"
echo "  Ganache:     http://localhost:8545"
echo "  PostgreSQL:  localhost:5433"
echo ""
echo "  Press Ctrl+C to stop all services"
echo "=========================================="
echo ""

# Trap to cleanup on exit
cleanup() {
    echo ""
    echo "Shutting down..."
    pkill -f "streamtide_server.js" 2>/dev/null || true
    pkill -f "shadow-cljs" 2>/dev/null || true
    echo "Done. Docker services still running (use 'docker compose down' to stop)"
}
trap cleanup EXIT

# Start shadow-cljs watch in background
bb watch-ui-server &
SHADOW_PID=$!

# Wait for initial build
echo "Waiting for initial build..."
sleep 20

# Start the server
echo "Starting API server..."
STREAMTIDE_ENV=dev node server/streamtide_server.js &
SERVER_PID=$!

# Wait for all background processes
wait $SHADOW_PID $SERVER_PID
