import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { scenario } from 'k6/execution';

const cacheHits         = new Counter('cache_hits');
const cacheMisses       = new Counter('cache_misses');
const hitRate           = new Rate('cache_hit_rate');
const stampedeHits      = new Counter('stampede_cache_hits');
const stampedeMisses    = new Counter('stampede_cache_misses');
const stampedeHitRate   = new Rate('stampede_cache_hit_rate');
const latency           = new Trend('request_latency', true);

const BASE_URL    = 'http://localhost:8080/user/v1/users';
const TOTAL_USERS = 5;

// Estratégia de prevenção ao stampede:
//   k6 run                       load-test.js   → sem proteção
//   k6 run -e STRATEGY=mutex     load-test.js
//   k6 run -e STRATEGY=dlock     load-test.js
//   k6 run -e STRATEGY=jitter    load-test.js
//   k6 run -e STRATEGY=pee       load-test.js
const STRATEGY = __ENV.STRATEGY || '';

// ---------------------------------------------------------------------------
// Setup: cria os usuários que serão usados durante o teste
// ---------------------------------------------------------------------------
export function setup() {
  const names = ['Alice', 'Bob', 'Carol', 'Dave', 'Eve'];
  const ids   = [];

  for (let i = 0; i < TOTAL_USERS; i++) {
    const res = http.post(BASE_URL, JSON.stringify({
      name:  names[i],
      age:   25 + i,
      email: `user${i}@stampede.test`,
    }), { headers: { 'Content-Type': 'application/json' } });

    check(res, { 'user created': (r) => r.status === 201 });

    const id = (res.headers['Location'] || '').split('/').pop();
    if (id) ids.push(id);
  }

  if (ids.length === 0) throw new Error('Setup falhou: nenhum usuário criado');
  console.log(`Setup OK — ${ids.length} usuários: ${ids.join(', ')}`);
  return { ids };
}

// ---------------------------------------------------------------------------
// Timeline
//
//   DB latency simulada: 2000ms
//   Fórmula: VUs_necessários = RPS × latência_s
//
//   0s ─── ramp_up ────────────────────────────── 30s
//          10 → 100 RPS gradual
//          Propósito: aquecer o cache para todos os usuários
//          VUs: 100 × 0.002s (hit) = 0.2 → preAllocated: 5 max: 20
//
//  30s ─── peak ───────────────────────────────── 80s  (50s)
//          100 RPS steady (todos hits de cache → 100 × 0.002s = 0.2 VUs)
//          Encerra em 80s — 5s de silêncio antes da invalidação
//
//  85s ─── invalidateCache (1x)
//          Deleta ids[0] do Redis — cache está frio
//
//  87s ─── stampede ──────────────────────────── 107s  (20s)
//          100 RPS concentrados em ids[0] (a hot key invalidada)
//          DB latency = 2000ms
//          Sem proteção: 100 × 2s = 200 threads simultâneas no DB
//                        → satura Tomcat (200 threads) → filas, erros, p99 explode
//          Com mutex   : 1 thread no DB por 2s, resto aguarda lock
//                        → 1 DB hit, p95 ≈ 2000ms (lock wait), sem erros
//
// 107s ─── recovery ──────────────────────────── 127s  (20s)
//          30 RPS espalhados nos 5 usuários — mostra estabilização
// ---------------------------------------------------------------------------

export const options = {
  scenarios: {

    ramp_up: {
      executor:        'ramping-arrival-rate',
      startRate:       10,
      timeUnit:        '1s',
      preAllocatedVUs: 5,
      maxVUs:          20,
      stages: [
        { target: 50,  duration: '15s' },
        { target: 100, duration: '15s' },
      ],
      exec: 'hotKeyRequest',
    },

    peak: {
      executor:        'constant-arrival-rate',
      rate:            100,
      timeUnit:        '1s',
      duration:        '50s',
      preAllocatedVUs: 5,
      maxVUs:          20,
      startTime:       '30s',
      exec:            'hotKeyRequest',
    },

    invalidateCache: {
      executor:   'per-vu-iterations',
      vus:        1,
      iterations: 1,
      startTime:  '85s',
      exec:       'invalidateCacheForStampede',
    },

    stampede: {
      executor:        'constant-arrival-rate',
      rate:            100,
      timeUnit:        '1s',
      duration:        '20s',
      preAllocatedVUs: 220,
      maxVUs:          400,
      startTime:       '87s',
      exec:            'stampedeRequest',
    },

    recovery: {
      executor:        'constant-arrival-rate',
      rate:            30,
      timeUnit:        '1s',
      duration:        '20s',
      preAllocatedVUs: 5,
      maxVUs:          20,
      startTime:       '107s',
      exec:            'hotKeyRequest',
    },
  },

  summaryTrendStats: ['avg', 'p(90)', 'p(95)', 'p(99)', 'max'],

  thresholds: {
    // Sem proteção: esperamos erros durante o stampede → threshold relaxado
    'http_req_failed': ['rate<0.50'],

    // Latência por fase
    'request_latency{phase:ramp_up}':  ['p(95)<500'],
    'request_latency{phase:peak}':     ['p(95)<100'],
    'request_latency{phase:stampede}': ['p(95)<30000'],
    'request_latency{phase:recovery}': ['p(95)<100'],
  },
};

// ---------------------------------------------------------------------------
// Funções de execução
// ---------------------------------------------------------------------------

function fetchEmail(userId, phase) {
  const res = http.get(`${BASE_URL}/${userId}/email`, {
    tags:    { phase },
    headers: { 'phase': phase, 'X-Strategy': STRATEGY },
    timeout: '30s',
  });

  const hit = res.headers['X-Cache'] === 'hit';
  hit ? cacheHits.add(1) : cacheMisses.add(1);
  hitRate.add(hit);
  if (phase === 'stampede') {
    hit ? stampedeHits.add(1) : stampedeMisses.add(1);
    stampedeHitRate.add(hit);
  }
  latency.add(res.timings.duration, { phase });

  check(res, { 'status 200': (r) => r.status === 200 });
}

// Tráfego distribuído entre todos os usuários (ramp, peak, recovery)
export function hotKeyRequest(data) {
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];
  fetchEmail(id, scenario.name);
}

// Concentra 100% do tráfego no usuário ids[0] (a hot key invalidada)
export function stampedeRequest(data) {
  fetchEmail(data.ids[0], 'stampede');
}

// Invalida apenas ids[0] — o alvo do stampede
// Sleep de 2s para garantir que qualquer request em voo com DB miss
// (2000ms delay) já completou antes do burst começar.
export function invalidateCacheForStampede(data) {
  const res = http.del(`${BASE_URL}/${data.ids[0]}/cache:invalidate`);
  check(res, { 'cache invalidado (204)': (r) => r.status === 204 });
  sleep(2);
}
