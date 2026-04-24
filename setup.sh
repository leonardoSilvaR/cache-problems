#!/bin/bash
set -e

MODULE=${1:?"Uso: $0 <module>  (ex: $0 stampede)"}
IMAGE="cache-$MODULE:1.0.0"

echo "Iniciando setup do módulo: $MODULE"

# 1. Verifica dependências
command -v docker >/dev/null 2>&1 || { echo "Docker não encontrado"; exit 1; }

# 2. Build do módulo
echo "Buildando módulo $MODULE"
./gradlew clean :$MODULE:bootJar -x test

# 3. Gera imagem Docker
echo "Gerando imagem Docker: $IMAGE"
docker build -t $IMAGE ./$MODULE

# 4. Sobe infraestrutura + módulo
echo "Subindo infraestrutura e aplicação"
docker compose --profile $MODULE up -d

# 5. Aguarda serviços iniciarem
echo "Aguardando serviços iniciarem"
sleep 20

# 6. Valida se tudo subiu
echo "Validando serviços"
docker compose ps

echo ""
echo "Ambiente pronto!"
echo "-----------------------------------"
echo "  App:        http://localhost:8080"
echo "  Prometheus: http://localhost:9090"
echo "  Grafana:    http://localhost:3000  (admin/admin)"
echo "-----------------------------------"

# Load test módulo-específico
LOAD_TEST_SCRIPT="./$MODULE/load-test.sh"
if [[ -f "$LOAD_TEST_SCRIPT" ]]; then
  read -p "Gostaria de executar o Load Test agora? (s/n): " CONFIRM
  if [[ "$CONFIRM" == "s" || "$CONFIRM" == "S" ]]; then
    bash "$LOAD_TEST_SCRIPT"
  fi
fi
