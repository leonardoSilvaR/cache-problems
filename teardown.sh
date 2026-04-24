#!/bin/bash

MODULE=$1

if [[ -n "$MODULE" ]]; then
  echo "Derrubando módulo: $MODULE"
  docker compose --profile $MODULE down
  docker rmi "cache-$MODULE:1.0.0" 2>/dev/null || true
else
  echo "Derrubando ambiente completo"
  COMPOSE_PROFILES=* docker compose down -v
  docker images --format '{{.Repository}}:{{.Tag}}' | grep '^cache-' | xargs -r docker rmi
fi

echo "Ambiente limpo!"
