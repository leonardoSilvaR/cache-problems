#!/bin/bash
set -e

APP_NAME="cache-app"
APP_VERSION="1.0.0"

echo "Recarregando aplicação"

# 1. Rebuilda o jar
echo "Rebuild do Spring Boot"
./gradlew clean build -x test

# 2. Rebuilda a imagem
echo "Rebuild da imagem Docker"
docker build -t $APP_NAME:$APP_VERSION .

# 3. Reinicia apenas o container da app
echo "Reiniciando container da app"
docker compose up -d --force-recreate app

# 4. Acompanha os logs pra confirmar que subiu
echo "Logs da aplicação"
docker compose logs -f app --tail=30