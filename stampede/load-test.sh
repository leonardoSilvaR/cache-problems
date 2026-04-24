#!/bin/bash
set -e

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

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ -z "$STRATEGY" ]]; then
  echo "Iniciando load test sem proteção..."
  k6 run "$SCRIPT_DIR/k6-load/load-test.js"
else
  echo "Iniciando load test com estratégia: $STRATEGY"
  k6 run -e STRATEGY=$STRATEGY "$SCRIPT_DIR/k6-load/load-test.js"
fi
