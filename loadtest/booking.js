// Load test for POST /api/bookings/users/{userId}
//
// Run one scenario at a time (see README):
//   SCENARIO=no_contention  every request books a different seat, spread across 100 events; all should succeed
//   SCENARIO=hot_event      every request books a different seat, but concurrent requests hit the same event
//                           (one show selling out seat by seat); all should succeed
//   SCENARIO=contention     5,000 requests compete for 350 seats; exactly 350 should succeed
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'no_contention';
const SEATS_PER_EVENT = 70; // the app creates A1..G10 for every MOVIE event
const ROWS = ['A', 'B', 'C', 'D', 'E', 'F', 'G'];
const USERS = 100;

const SCENARIOS = {
  no_contention: { events: 100, vus: 50, iterations: 100 * SEATS_PER_EVENT },
  hot_event:     { events: 100, vus: 50, iterations: 100 * SEATS_PER_EVENT },
  contention:    { events: 5,   vus: 100, iterations: 5000 },
};
const config = SCENARIOS[SCENARIO];

const booked = new Counter('bookings_succeeded');      // 200
const conflicts = new Counter('bookings_conflict');    // 409: seat already taken (expected under contention)
const unexpected = new Counter('bookings_unexpected'); // anything else: always a bug

export const options = {
  setupTimeout: '10m',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    [SCENARIO]: { executor: 'shared-iterations', vus: config.vus, iterations: config.iterations, maxDuration: '10m' },
  },
  thresholds: {
    bookings_unexpected: ['count==0'],
    // Listed so the summary reports latency and failures for booking requests only (not setup)
    'http_req_duration{name:book}': ['p(99)<10000'],
    'http_req_failed{name:book}': ['rate<0.01'],
  },
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

function seatId(index) {
  return ROWS[Math.floor(index / 10)] + ((index % 10) + 1);
}

// Registers an account and logs it in; returns { id, token }
function registerAndLogin(name, email, role) {
  const password = 'loadtest-pw';
  const registered = http.post(`${BASE_URL}/api/users/register`,
    JSON.stringify({ name, email, password, role }), JSON_HEADERS);
  const login = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password }), JSON_HEADERS);
  return { id: registered.json('id'), token: login.json('token') };
}

function authHeaders(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

export function setup() {
  const run = Date.now();
  const users = [];
  for (let i = 0; i < USERS; i++) {
    users.push(registerAndLogin(`Load User ${i}`, `load-${run}-${i}@example.test`, 'USER'));
  }
  const organizer = registerAndLogin('Load Organizer', `load-${run}-organizer@example.test`, 'ORGANIZER');
  const events = [];
  for (let i = 0; i < config.events; i++) {
    const res = http.post(`${BASE_URL}/api/events`, JSON.stringify({
      name: `Load ${SCENARIO} ${run} #${i}`, description: 'load test', eventDateTime: '2030-01-01T20:00:00',
      venue: 'Load Hall', totalSeats: SEATS_PER_EVENT, price: 20, category: 'MOVIE',
    }), { headers: authHeaders(organizer.token) });
    events.push(res.json('id'));
  }
  return { users, events, bookingPhaseStart: Date.now() };
}

// Throughput over the booking phase only (k6's own rates also count setup time)
export function teardown({ bookingPhaseStart }) {
  const seconds = (Date.now() - bookingPhaseStart) / 1000;
  console.log(`booking phase: ${config.iterations} requests in ${seconds.toFixed(2)}s = ${(config.iterations / seconds).toFixed(1)} req/s`);
}

export default function ({ users, events }) {
  const i = exec.scenario.iterationInTest;
  let eventId, seat;
  if (SCENARIO === 'no_contention') {
    // Iteration i gets its own seat; consecutive iterations go to different events
    eventId = events[i % events.length];
    seat = seatId(Math.floor(i / events.length));
  } else if (SCENARIO === 'hot_event') {
    // Iteration i gets its own seat; consecutive iterations fill the same event
    eventId = events[Math.floor(i / SEATS_PER_EVENT)];
    seat = seatId(i % SEATS_PER_EVENT);
  } else {
    // Random seat among only 350, so most requests collide
    eventId = events[Math.floor(Math.random() * events.length)];
    seat = seatId(Math.floor(Math.random() * SEATS_PER_EVENT));
  }

  const user = users[i % users.length];
  const res = http.post(`${BASE_URL}/api/bookings/users/${user.id}`,
    JSON.stringify({ eventId, seatNumbers: [seat], numberOfTickets: 1 }),
    { headers: authHeaders(user.token), tags: { name: 'book' }, responseCallback: http.expectedStatuses(200, 409) });

  if (res.status === 200) booked.add(1);
  else if (res.status === 409) conflicts.add(1);
  else unexpected.add(1);
}
