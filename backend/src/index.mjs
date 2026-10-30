import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { ApiError, requireValue } from './domain.mjs';
import { FirebaseStore } from './firebase-store.mjs';
import { KitchenService } from './service.mjs';
import { VertexRecipes } from './vertex.mjs';

const firebase = JSON.parse(process.env.FIREBASE_WEB_CONFIG || '{}');
for (const field of ['apiKey', 'authDomain', 'projectId', 'appId'])
  if (typeof firebase[field] !== 'string' || !firebase[field]) throw new Error(`FIREBASE_WEB_CONFIG.${field} fehlt`);
const origin = new URL(process.env.PUBLIC_ORIGIN || '');
if (origin.protocol !== 'https:' || origin.pathname !== '/' || origin.search || origin.hash || origin.username || origin.password)
  throw new Error('PUBLIC_ORIGIN muss eine HTTPS-Origin ohne Pfad sein');
const providers = (process.env.AUTH_PROVIDERS || '').split(',').filter(Boolean);
if (!providers.length || providers.some(p => !['google', 'microsoft', 'facebook', 'apple', 'email'].includes(p)))
  throw new Error('AUTH_PROVIDERS explizit auf tatsächlich freigeschaltete Anbieter setzen');
const credential = applicationDefault();
initializeApp({ credential, projectId: firebase.projectId });
const service = new KitchenService({ store: new FirebaseStore(getFirestore()), auth: getAuth(),
  model: new VertexRecipes(credential, { project: firebase.projectId, location: process.env.VERTEX_LOCATION,
    model: process.env.VERTEX_MODEL }), config: { firebase } });
const staticFiles = new Map([
  ['/auth/start', ['auth.html', 'text/html; charset=utf-8']],
  ['/auth/complete', ['auth.html', 'text/html; charset=utf-8']],
  ['/assets/auth.js', ['auth.js', 'text/javascript; charset=utf-8']],
  ['/assets/auth.css', ['auth.css', 'text/css; charset=utf-8']]
]);

// This coarse single-instance guard supplements authenticated transactional daily quotas.
// Do not trust X-Forwarded-For from arbitrary clients; use a verified gateway for production.
const rates = new Map();
function rate(req) {
  const minute = Math.floor(Date.now() / 60000), key = req.socket.remoteAddress;
  if (rates.size > 10000) rates.clear();
  const entry = rates.get(key);
  if (!entry || entry.minute !== minute) rates.set(key, { minute, count: 1 });
  else if (++entry.count > 120) throw new ApiError(429, 'Zu viele Anfragen');
}
async function body(req) {
  requireValue((req.headers['content-type'] || '').split(';')[0] === 'application/json', 'JSON erforderlich', 415);
  let size = 0; const chunks = [];
  for await (const chunk of req) {
    size += chunk.length; requireValue(size <= 524288, 'Anfrage ist zu groß', 413); chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new ApiError(400, 'Ungültiges JSON'); }
}
const server = http.createServer(async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'self' https://apis.google.com https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; style-src 'self'; connect-src 'self' https://*.googleapis.com https://*.firebaseapp.com https://*.firebaseio.com; frame-src https://*.firebaseapp.com https://*.web.app https://accounts.google.com https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
  res.setHeader('Cross-Origin-Opener-Policy', 'same-origin-allow-popups');
  const send = value => { res.setHeader('Content-Type', 'application/json; charset=utf-8'); res.end(JSON.stringify(value)); };
  try {
    rate(req);
    const path = new URL(req.url, origin).pathname;
    if (req.method === 'GET' && staticFiles.has(path)) {
      const [file, type] = staticFiles.get(path); res.setHeader('Content-Type', type);
      res.end(await readFile(new URL(`../public/${file}`, import.meta.url))); return;
    }
    if (req.method === 'GET' && path === '/v1/config') { send({ firebase, providers, origin: origin.origin }); return; }
    requireValue(req.method === 'POST', 'Nicht gefunden', 404);
    // Native clients omit Origin. Browser requests must come from this exact origin.
    requireValue(!req.headers.origin || req.headers.origin === origin.origin, 'Fremde Origin', 403);
    const token = /^Bearer (\S+)$/.exec(req.headers.authorization || '')?.[1];
    const input = await body(req);
    switch (path) {
      case '/v1/auth/code': send(await service.issueCode(token, input)); break;
      case '/v1/auth/exchange': send(await service.exchange(input)); break;
      case '/v1/auth/device/start': send(await service.startDevice(input)); break;
      case '/v1/auth/device/approve': send(await service.approveDevice(token,input)); break;
      case '/v1/auth/device/poll': send(await service.pollDevice(input)); break;
      case '/v1/recipes/generate': send(await service.generate(token, input)); break;
      case '/v1/recipes/contribute': send(await service.contribute(token, input)); break;
      case '/v1/recipes/contribute-kalomatik': send(await service.contributeKalomatik(token, input)); break;
      case '/v1/recipes/validate': send(await service.validate(token, input)); break;
      case '/v1/recipes/catalog': send(await service.catalog(token)); break;
      case '/v1/recipes/drafts': send(await service.drafts(token)); break;
      default: throw new ApiError(404, 'Nicht gefunden');
    }
  } catch (error) {
    res.statusCode = error instanceof ApiError ? error.status : 500;
    // Never send/log provider tokens, prompts, private kitchen data or raw upstream exceptions.
    send({ error: error instanceof ApiError ? error.message : 'Dienst vorübergehend nicht verfügbar', code: error instanceof ApiError ? error.code : 'unavailable' });
  }
});
server.requestTimeout = 50000;
server.headersTimeout = 10000;
server.listen(Number(process.env.PORT || 8080), '0.0.0.0');
