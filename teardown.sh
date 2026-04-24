#!/bin/bash

echo "Derrubando ambiente"
docker compose down -v
docker rmi cache-app:1.0.0
echo "Ambiente limpo!"