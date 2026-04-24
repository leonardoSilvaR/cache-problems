# Cache Problems

Projeto multi-módulo demonstrando problemas clássicos de cache e estratégias de solução, implementados em Spring Boot 4 com Redis e PostgreSQL. Cada módulo é uma aplicação independente com carga simulada via k6 e observabilidade com Prometheus + Grafana.

## Módulos

| Módulo | Problema | Status |
|---|---|---|
| [stampede](./stampede) | Cache Stampede — múltiplos threads reconstruindo o cache simultaneamente | ✅ disponível |

## Pré-requisitos

- Java 21
- Docker
- k6 ([instalação](https://grafana.com/docs/k6/latest/set-up/install-k6/))

## Estrutura

```
cache-problems/
├── stampede/               # módulo Cache Stampede
│   ├── src/
│   ├── k6-load/            # load test k6
│   ├── load-test.sh        # script interativo de load test
│   ├── Dockerfile
│   └── build.gradle
├── grafana/
│   ├── dashboards/         # JSONs dos dashboards (um por módulo)
│   └── provisioning/       # datasource e provider auto-carregados
├── prometheus.yml          # scrape configs (um job por módulo)
├── compose.yaml
├── setup.sh
├── teardown.sh
└── restart.sh
```

## Setup

### 1. Clone o repositório

```bash
git clone https://github.com/LeonardoSilvaR/cache-problems.git
cd cache-problems
```

### 2. Conceda permissão aos scripts

```bash
chmod +x setup.sh teardown.sh restart.sh stampede/load-test.sh
```

### 3. Execute o setup do módulo desejado

```bash
./setup.sh stampede
```

O script irá:
1. Buildar o JAR do módulo
2. Gerar a imagem Docker
3. Subir a infraestrutura compartilhada (Redis, PostgreSQL, Prometheus, Grafana)
4. Subir a aplicação do módulo
5. Perguntar se deseja executar o load test imediatamente

Ao final, os serviços estarão disponíveis em:

| Serviço | URL |
|---|---|
| Aplicação | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |

## Teardown

```bash
# Derruba apenas o módulo (mantém a infra rodando)
./teardown.sh stampede

# Derruba tudo — infra, aplicação, volumes e imagens
./teardown.sh
```

## Restart

Para rebuildar e restartar apenas o container do módulo sem derrubar a infra:

```bash
./restart.sh stampede
```

## Observabilidade

O Grafana provisiona automaticamente datasource e dashboards ao subir — não é necessário importar nada manualmente.

- Acesse **http://localhost:3000** (admin/admin)
- O dashboard do módulo correspondente já estará disponível em **Dashboards**

---

## Módulo: stampede

Demonstração do fenômeno de **cache stampede**: quando uma chave expira sob alta carga, múltiplos threads vão ao banco simultaneamente, saturando as conexões e gerando pico de latência.

### Estratégias implementadas

| Estratégia | Tipo | Consistência |
|---|---|---|
| Mutex | Reativa | Forte |
| Distributed Lock (Redisson) | Reativa | Forte |
| TTL Jitter | Proativa | Forte |
| Stale-While-Revalidate | Proativa | Eventual |
| Probabilistic Early Expiration | Proativa | Forte |

### Endpoints

```bash
# Criar usuário
curl -X POST http://localhost:8080/user/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"name": "Alice", "age": 30, "email": "alice@example.com"}'

# Listar usuários
curl http://localhost:8080/user/v1/users

# Buscar email (com cache)
curl http://localhost:8080/user/v1/users/{id}/email

# Invalidar cache manualmente
curl -X DELETE http://localhost:8080/user/v1/users/{id}/cache:invalidate
```

### Load test manual

```bash
# Sem proteção — stampede exposto
k6 run stampede/k6-load/load-test.js

# Com estratégia de prevenção
k6 run -e STRATEGY=mutex  stampede/k6-load/load-test.js
k6 run -e STRATEGY=dlock  stampede/k6-load/load-test.js
k6 run -e STRATEGY=jitter stampede/k6-load/load-test.js
k6 run -e STRATEGY=swr    stampede/k6-load/load-test.js
k6 run -e STRATEGY=pee    stampede/k6-load/load-test.js
```
