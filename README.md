# Cache Stampede — Simulação e Estratégias de Prevenção

Projeto demonstrando o fenômeno de **cache stampede** e cinco estratégias de prevenção implementadas em Spring Boot 4 com Redis e PostgreSQL. Inclui carga simulada via k6 e observabilidade com Prometheus + Grafana.

## Pré-requisitos

- Java 21
- Docker
- k6 ([instalação](https://grafana.com/docs/k6/latest/set-up/install-k6/))

## Executando o projeto

### 1. Clone o repositório

```bash
git clone https://github.com/LeonardoSilvaR/Caching.git
cd Caching
```

### 2. Conceda permissão aos scripts

```bash
chmod +x setup.sh teardown.sh restart.sh
```

### 3. Execute o setup

```bash
./setup.sh
```

O script irá:
- Buildar a aplicação e gerar a imagem Docker
- Subir toda a infraestrutura (Redis, PostgreSQL, Prometheus, Grafana)
- Subir a aplicação
- Perguntar se deseja executar o load test imediatamente e, se sim, apresentar um menu para escolher a estratégia:

```
Selecione a estratégia de prevenção ao cache stampede:
  0) Sem proteção  — stampede exposto
  1) Mutex         — lock JVM com double-check
  2) DLOCK         — lock distribuído via Redisson
  3) Jitter        — TTL aleatório na escrita
  4) SWR           — Stale-While-Revalidate
  5) PEE           — Probabilistic Early Expiration
```

Ao final, os serviços estarão disponíveis em:

| Serviço | URL |
|---|---|
| Aplicação | http://localhost:8080 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |

## Visualizando as métricas no Grafana

1. Acesse o Grafana em **http://localhost:3000** (usuário: `admin`, senha: `admin`)
2. No menu lateral, vá em **Dashboards → Import**
3. Clique em **Upload dashboard JSON file**
4. Selecione o arquivo `grafana/dashboards/cache-stampede.json`
5. Selecione o datasource **Prometheus** e clique em **Import**

## Executando o load test manualmente

O load test pode ser executado com ou sem estratégia de prevenção:

```bash
# Sem proteção — stampede exposto
k6 run k6-load/load-test.js

# Com estratégia
k6 run -e STRATEGY=mutex  k6-load/load-test.js
k6 run -e STRATEGY=dlock  k6-load/load-test.js
k6 run -e STRATEGY=jitter k6-load/load-test.js
k6 run -e STRATEGY=swr    k6-load/load-test.js
k6 run -e STRATEGY=pee    k6-load/load-test.js
```

## Outros scripts

```bash
# Rebuilda e reinicia apenas a aplicação (sem derrubar a infra)
./restart.sh

# Derruba todo o ambiente e remove volumes e imagens
./teardown.sh
```

## Estratégias implementadas

| Estratégia | Tipo | Consistência |
|---|---|---|
| Mutex | Reativa | Forte |
| Distributed Lock (Redisson) | Reativa | Forte |
| TTL Jitter | Proativa | Forte |
| Stale-While-Revalidate | Proativa | Eventual |
| Probabilistic Early Expiration | Proativa | Forte |

## Endpoints

```bash
# Criar usuário
curl -X POST http://localhost:8080/user/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"name": "Alice", "age": 30, "email": "alice@example.com"}'

# Listar usuários
curl http://localhost:8080/user/v1/users

# Buscar email (com cache)
curl http://localhost:8080/user/v1/users/{id}/email

# Invalidar cache
curl -X DELETE http://localhost:8080/user/v1/users/{id}/cache:invalidate
```
