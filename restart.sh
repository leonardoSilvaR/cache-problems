#!/bin/bash
set -e

MODULE=${1:?"Uso: $0 <module>  (ex: $0 stampede)"}
IMAGE="cache-$MODULE:1.0.0"

echo "Recarregando módulo: $MODULE"

# 1. Rebuilda o jar
echo "Rebuild do Spring Boot"
./gradlew :$MODULE:bootJar -x test

# 2. Rebuilda a imagem
echo "Rebuild da imagem Docker"
docker build -t $IMAGE ./$MODULE

# 3. Reinicia apenas o container do módulo
echo "Reiniciando container"
docker compose --profile $MODULE up -d --force-recreate $MODULE

# 4. Acompanha os logs
echo "Logs da aplicação"
docker compose logs -f $MODULE --tail=30
