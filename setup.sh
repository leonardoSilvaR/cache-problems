#!/bin/bash
set -e

APP_NAME="cache-app"
APP_VERSION="1.0.0"
DOCKERFILE_PATH="."

echo "Iniciando setup do ambiente..."

# 1. Verifica dependências
echo "Verificando dependências"
command -v docker >/dev/null 2>&1 || { echo "Docker não encontrado"; exit 1; }

# 2. Build do projeto
echo "Buildando aplicação Spring Boot"
./gradlew clean build -x test

# 3. Gera imagem Docker da app
echo "Gerando imagem Docker da aplicação"
docker build -t $APP_NAME:$APP_VERSION $DOCKERFILE_PATH

# 4. Sobe toda infraestrutura
echo "Subindo infraestrutura (Postgres, Redis, Prometheus, Grafana)"
docker compose up -d cache database prometheus grafana

# 5. Aguarda serviços iniciarem
echo "Aguardando serviços iniciarem"
sleep 20

# 6. Sobe a aplicação
echo "Subindo aplicação"
docker compose up -d app

# 7. Valida se tudo subiu
echo "Validando serviços"
docker compose ps

echo ""
echo "Ambiente pronto!"
echo "-----------------------------------"
echo "  App:        http://localhost:8080"
echo "  Prometheus: http://localhost:9090"
echo "  Grafana:    http://localhost:3000  (admin/admin)"
echo "-----------------------------------"

# Load Test
read -p "Gostaria de executar o Load Test agora? (s/n): " CONFIRM
if [[ "$CONFIRM" != "s" && "$CONFIRM" != "S" ]]; then
  echo "Cancelado."
  exit 0
fi

command -v k6 >/dev/null 2>&1 || { echo "k6 não encontrado. Instale em https://grafana.com/docs/k6/latest/set-up/install-k6/"; exit 1; }

echo ""
echo "Selecione a estratégia de prevenção ao cache stampede:"
echo "  0) Sem proteção  — stampede exposto"
echo "  1) Mutex         — lock JVM com double-check"
echo "  2) DLOCK         — lock distribuído via Redisson"
echo "  3) Jitter        — TTL aleatório na escrita"
echo "  4) SWR           — Stale-While-Revalidate"
echo "  5) PEE           — Probabilistic Early Expiration"
echo ""
read -p "Opção [0-5]: " OPTION

case $OPTION in
  0) STRATEGY="" ;;
  1) STRATEGY="mutex" ;;
  2) STRATEGY="dlock" ;;
  3) STRATEGY="jitter" ;;
  4) STRATEGY="swr" ;;
  5) STRATEGY="pee" ;;
  *)
    echo "Opção inválida."
    exit 1
    ;;
esac

echo "Aguardando aplicação estabilizar"
sleep 20

if [[ -z "$STRATEGY" ]]; then
  echo "Iniciando load test sem proteção..."
  k6 run ./k6-load/load-test.js
else
  echo "Iniciando load test com estratégia: $STRATEGY"
  k6 run -e STRATEGY=$STRATEGY ./k6-load/load-test.js
fi
