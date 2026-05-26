/* Nodebase Dashboard — vanilla JS SPA */

const API = '/dashboard/api';
let wsConn = null;

// ─── Auth helpers ──────────────────────────────────────────────────────────

function getToken() { return sessionStorage.getItem('nb_dash_token'); }
function setToken(t) { sessionStorage.setItem('nb_dash_token', t); }
function clearToken() { sessionStorage.removeItem('nb_dash_token'); }

async function apiFetch(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  const token = getToken();
  if (token) headers['X-Dashboard-Token'] = token;
  const res = await fetch(API + path, { ...opts, headers });
  if (res.status === 401) { logout(); return null; }
  return res;
}

// ─── Toast ─────────────────────────────────────────────────────────────────

function toast(msg, type = 'success') {
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.textContent = msg;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 3000);
}

// ─── Copy helper ───────────────────────────────────────────────────────────

function copyCode(btn) {
  const pre = btn.parentElement;
  const text = pre.textContent.replace('Copy', '').trim();
  navigator.clipboard.writeText(text).then(() => {
    btn.textContent = 'Copied!';
    setTimeout(() => btn.textContent = 'Copy', 1500);
  });
}

// ─── Auth flow ─────────────────────────────────────────────────────────────

function showLogin() {
  document.getElementById('login-screen').classList.remove('hidden');
  document.getElementById('app-shell').classList.add('hidden');
}

function showApp() {
  document.getElementById('login-screen').classList.add('hidden');
  document.getElementById('app-shell').classList.remove('hidden');
}

function logout() {
  clearToken();
  if (wsConn) { wsConn.close(); wsConn = null; }
  showLogin();
}

document.getElementById('login-form').addEventListener('submit', async e => {
  e.preventDefault();
  const key = document.getElementById('master-key-input').value.trim();
  const errEl = document.getElementById('login-error');
  errEl.classList.add('hidden');

  try {
    const res = await fetch(API + '/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ masterKey: key })
    });
    const data = await res.json();
    if (!res.ok) { errEl.textContent = data.error || 'Login failed'; errEl.classList.remove('hidden'); return; }
    setToken(data.token);
    showApp();
    activateTab('overview');
  } catch (err) {
    errEl.textContent = 'Cannot reach server';
    errEl.classList.remove('hidden');
  }
});

document.getElementById('logout-btn').addEventListener('click', logout);

// ─── Tab routing ───────────────────────────────────────────────────────────

function activateTab(name) {
  document.querySelectorAll('.nav-item').forEach(a => a.classList.remove('active'));
  document.querySelectorAll('.tab-content').forEach(d => d.classList.add('hidden'));
  document.querySelector(`[data-tab="${name}"]`)?.classList.add('active');
  const el = document.getElementById('tab-' + name);
  if (el) { el.classList.remove('hidden'); renderTab(name, el); }
}

document.querySelectorAll('.nav-item').forEach(a => {
  a.addEventListener('click', e => { e.preventDefault(); activateTab(a.dataset.tab); });
});

// ─── Tab renderers ─────────────────────────────────────────────────────────

function renderTab(name, el) {
  switch (name) {
    case 'overview':  renderOverview(el); break;
    case 'database':  renderDatabase(el); break;
    case 'realtime':  renderRealtime(el); break;
    case 'settings':  renderSettings(el); break;
    case 'guide':     renderGuide(el); break;
  }
}

// ─── Overview ──────────────────────────────────────────────────────────────

let statsInterval = null;

async function renderOverview(el) {
  el.innerHTML = `
    <div class="page-header">
      <h2>Overview</h2>
      <p>Live metrics — refreshes every 5 seconds</p>
    </div>
    <div class="grid grid-cols-3 gap-5 mb-8" id="stat-cards">
      <div class="stat-card"><div class="label">Users</div><div class="value" id="stat-users">—</div></div>
      <div class="stat-card"><div class="label">Collections</div><div class="value" id="stat-collections">—</div></div>
      <div class="stat-card"><div class="label">Storage</div><div class="value text-2xl" id="stat-storage">—</div></div>
    </div>
    <div class="bg-white rounded-2xl border border-slate-100 shadow-sm">
      <div class="px-6 py-4 border-b border-slate-100">
        <h3 class="font-semibold text-sm text-gray-700">Collections</h3>
      </div>
      <table class="nb-table">
        <thead><tr><th>Collection</th><th>Documents</th></tr></thead>
        <tbody id="collections-body"><tr><td colspan="2" class="text-center text-gray-400 py-8">Loading…</td></tr></tbody>
      </table>
    </div>
  `;

  if (statsInterval) clearInterval(statsInterval);
  await loadStats();
  statsInterval = setInterval(loadStats, 5000);
}

async function loadStats() {
  const res = await apiFetch('/stats');
  if (!res) return;
  const d = await res.json();
  const u = document.getElementById('stat-users');
  const c = document.getElementById('stat-collections');
  const s = document.getElementById('stat-storage');
  if (u) u.textContent = d.users;
  if (c) c.textContent = (d.collections || []).length;
  if (s) s.textContent = d.storageTotalMb + ' MB';

  const tbody = document.getElementById('collections-body');
  if (!tbody) return;
  if (!d.collections?.length) {
    tbody.innerHTML = '<tr><td colspan="2" class="text-center text-gray-400 py-8">No collections yet — use the Database tab to seed some data.</td></tr>';
    return;
  }
  tbody.innerHTML = d.collections.map(c =>
    `<tr><td class="font-medium">${esc(c.collection)}</td><td>${c.count}</td></tr>`
  ).join('');
}

// ─── Database (Seeder) ─────────────────────────────────────────────────────

function renderDatabase(el) {
  el.innerHTML = `
    <div class="page-header">
      <h2>Database</h2>
      <p>Seed collections with test data or clear existing data.</p>
    </div>

    <div class="grid grid-cols-2 gap-6">
      <!-- Custom seed -->
      <div class="bg-white rounded-2xl border border-slate-100 shadow-sm p-6">
        <h3 class="font-semibold text-sm text-gray-700 mb-4">Custom Seed</h3>
        <label class="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">Collection name</label>
        <input id="seed-collection" type="text" placeholder="e.g. items" class="nb-input mb-3">
        <label class="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">
          JSON template
          <span class="font-normal normal-case text-gray-400 ml-1">Use {{index}}, {{uuid}}, {{now}}</span>
        </label>
        <textarea id="seed-template" rows="5" class="nb-input font-mono text-xs mb-3"
          placeholder='{"name": "Item {{index}}", "id": "{{uuid}}", "ts": "{{now}}"}'></textarea>
        <label class="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">Count (max 500)</label>
        <input id="seed-count" type="number" value="10" min="1" max="500" class="nb-input mb-4">
        <button class="nb-btn nb-btn-primary w-full" onclick="doSeed()">Seed Collection</button>
        <div id="seed-result" class="mt-3 text-sm text-gray-500 text-center"></div>
      </div>

      <!-- Demo data + clear -->
      <div class="flex flex-col gap-4">
        <div class="bg-white rounded-2xl border border-slate-100 shadow-sm p-6">
          <h3 class="font-semibold text-sm text-gray-700 mb-2">Demo Data</h3>
          <p class="text-xs text-gray-500 mb-4">Inserts 5 users, 5 posts, and 3 products into their respective collections.</p>
          <button class="nb-btn nb-btn-secondary w-full" onclick="doSeedDemo()">Load Demo Data</button>
        </div>

        <div class="bg-white rounded-2xl border border-slate-100 shadow-sm p-6">
          <h3 class="font-semibold text-sm text-gray-700 mb-2">Clear Collection</h3>
          <p class="text-xs text-gray-500 mb-3">Permanently deletes all documents in the specified collection.</p>
          <input id="clear-collection" type="text" placeholder="Collection name" class="nb-input mb-3">
          <button class="nb-btn nb-btn-danger w-full" onclick="doClear()">Clear Collection</button>
        </div>
      </div>
    </div>
  `;
}

async function doSeed() {
  const collection = document.getElementById('seed-collection').value.trim();
  const templateRaw = document.getElementById('seed-template').value.trim();
  const count = parseInt(document.getElementById('seed-count').value) || 10;
  const result = document.getElementById('seed-result');

  if (!collection) { toast('Collection name is required', 'error'); return; }

  let template = {};
  if (templateRaw) {
    try { template = JSON.parse(templateRaw); } catch { toast('Invalid JSON template', 'error'); return; }
  }

  result.textContent = 'Seeding…';
  const res = await apiFetch('/seed', { method: 'POST', body: JSON.stringify({ collection, template, count }) });
  if (!res) return;
  const d = await res.json();
  if (res.ok) {
    result.textContent = `✓ Inserted ${d.inserted} documents into "${d.collection}"`;
    toast(`Inserted ${d.inserted} documents`);
  } else {
    result.textContent = d.error || 'Seed failed';
    toast(d.error || 'Seed failed', 'error');
  }
}

async function doSeedDemo() {
  const res = await apiFetch('/seed/demo', { method: 'POST' });
  if (!res) return;
  const d = await res.json();
  if (res.ok) toast(`Demo data loaded: ${d.inserted} documents across ${d.collections.join(', ')}`);
  else toast(d.error || 'Failed', 'error');
}

async function doClear() {
  const name = document.getElementById('clear-collection').value.trim();
  if (!name) { toast('Collection name required', 'error'); return; }
  if (!confirm(`Delete all documents in "${name}"? This cannot be undone.`)) return;
  const res = await apiFetch('/collections/' + encodeURIComponent(name), { method: 'DELETE' });
  if (!res) return;
  const d = await res.json();
  if (res.ok) toast(`Collection "${d.dropped}" cleared`);
  else toast(d.error || 'Failed', 'error');
}

// ─── Realtime Playground ───────────────────────────────────────────────────

function renderRealtime(el) {
  el.innerHTML = `
    <div class="page-header">
      <h2>Realtime Playground</h2>
      <p>Connect via WebSocket and watch live events from any collection.</p>
    </div>
    <div class="grid grid-cols-2 gap-6">
      <div class="bg-white rounded-2xl border border-slate-100 shadow-sm p-6">
        <h3 class="font-semibold text-sm text-gray-700 mb-4">Connection</h3>
        <label class="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">User JWT</label>
        <input id="rt-token" type="text" placeholder="Paste a user JWT token here" class="nb-input mb-3 font-mono text-xs">
        <label class="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">Collection</label>
        <input id="rt-collection" type="text" placeholder="e.g. posts" value="posts" class="nb-input mb-4">
        <div class="flex gap-2">
          <button id="rt-connect-btn" class="nb-btn nb-btn-primary flex-1" onclick="rtConnect()">Connect</button>
          <button class="nb-btn nb-btn-secondary" onclick="rtDisconnect()">Disconnect</button>
        </div>
        <div class="flex items-center gap-2 mt-3">
          <div id="rt-dot" class="dot dot-red"></div>
          <span id="rt-status" class="text-xs text-gray-500">Disconnected</span>
        </div>

        <div class="border-t border-slate-100 mt-5 pt-5">
          <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-3">Fire Test Event</h4>
          <p class="text-xs text-gray-400 mb-3">Creates a document in the collection and you'll see the CREATED event appear in the log.</p>
          <button class="nb-btn nb-btn-secondary w-full" onclick="rtFireTest()">Create Test Document</button>
        </div>
      </div>

      <div class="bg-white rounded-2xl border border-slate-100 shadow-sm p-6 flex flex-col">
        <div class="flex items-center justify-between mb-3">
          <h3 class="font-semibold text-sm text-gray-700">Event Log</h3>
          <button class="text-xs text-gray-400 hover:text-gray-600" onclick="document.getElementById('event-log').textContent=''">Clear</button>
        </div>
        <div id="event-log" class="flex-1">Waiting for connection…</div>
      </div>
    </div>
  `;
}

function rtLog(msg, type = 'system') {
  const log = document.getElementById('event-log');
  if (!log) return;
  const line = document.createElement('div');
  line.className = 'event-' + type;
  line.textContent = new Date().toLocaleTimeString() + '  ' + msg;
  log.appendChild(line);
  log.scrollTop = log.scrollHeight;
}

function rtConnect() {
  const token = document.getElementById('rt-token').value.trim();
  const collection = document.getElementById('rt-collection').value.trim();
  if (!token) { toast('Paste a JWT token first', 'error'); return; }
  if (!collection) { toast('Enter a collection name', 'error'); return; }
  if (wsConn) wsConn.close();

  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const url = proto + '//' + location.host + '/realtime?token=' + encodeURIComponent(token);

  rtLog('Connecting to ' + url + ' …');
  wsConn = new WebSocket(url);

  wsConn.onopen = () => {
    document.getElementById('rt-dot').className = 'dot dot-green';
    document.getElementById('rt-status').textContent = 'Connected';
    rtLog('Connected — subscribing to db/' + collection, 'system');
    wsConn.send(JSON.stringify({ type: 'subscribe', channel: 'db/' + collection }));
  };

  wsConn.onmessage = e => {
    try {
      const msg = JSON.parse(e.data);
      if (msg.type === 'subscribed') {
        rtLog('Subscribed to ' + msg.channel, 'system');
      } else if (msg.type === 'event') {
        const evtType = (msg.event?.type || 'event').toLowerCase();
        const cls = evtType === 'created' ? 'created' : evtType === 'updated' ? 'updated' : evtType === 'deleted' ? 'deleted' : 'system';
        rtLog(JSON.stringify(msg.event, null, 0), cls);
      } else if (msg.type === 'pong') {
        rtLog('pong', 'system');
      } else {
        rtLog(e.data, 'system');
      }
    } catch { rtLog(e.data, 'system'); }
  };

  wsConn.onclose = () => {
    document.getElementById('rt-dot').className = 'dot dot-red';
    document.getElementById('rt-status').textContent = 'Disconnected';
    rtLog('Connection closed', 'system');
    wsConn = null;
  };

  wsConn.onerror = () => rtLog('WebSocket error', 'deleted');
}

function rtDisconnect() {
  if (wsConn) { wsConn.close(); wsConn = null; }
}

async function rtFireTest() {
  const token = document.getElementById('rt-token').value.trim();
  const collection = document.getElementById('rt-collection').value.trim() || 'test';
  if (!token) { toast('Need a JWT token to fire a test event', 'error'); return; }

  const res = await fetch('/db/' + encodeURIComponent(collection), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
    body: JSON.stringify({ message: 'Hello from Nodebase dashboard!', timestamp: Date.now() })
  });
  if (res.ok) toast('Test document created — check the event log');
  else { const d = await res.json(); toast(d.error || 'Failed to create document', 'error'); }
}

// ─── Settings ──────────────────────────────────────────────────────────────

async function renderSettings(el) {
  el.innerHTML = `
    <div class="page-header">
      <h2>Settings</h2>
      <p>View and update server configuration. Changes to rate limits take effect after restart.</p>
    </div>
    <div class="bg-white rounded-2xl border border-slate-100 shadow-sm p-6 max-w-xl" id="settings-form-wrap">
      <p class="text-center text-gray-400 py-8">Loading config…</p>
    </div>
  `;

  const res = await apiFetch('/config');
  if (!res) return;
  const cfg = await res.json();

  document.getElementById('settings-form-wrap').innerHTML = `
    <form id="settings-form" class="space-y-4">
      ${settingField('port',                'Port',               cfg.port,               'number', 'read-only — change via restart')}
      ${settingField('host',                'Host',               cfg.host,               'text',   'read-only — change via restart')}
      ${settingField('cors.allowedOrigins', 'CORS Allowed Origins', cfg['cors.allowedOrigins'], 'text', 'comma-separated origins, * for all')}
      ${settingField('maxUploadMb',         'Max Upload (MB)',    cfg.maxUploadMb,        'number')}
      ${settingField('rateLimitPerIp',      'Rate Limit / IP',   cfg.rateLimitPerIp,     'number', 'requests/min — takes effect after restart')}
      ${settingField('rateLimitPerKey',     'Rate Limit / Key',  cfg.rateLimitPerKey,    'number', 'requests/min — takes effect after restart')}
      <div class="pt-2">
        <button type="submit" class="nb-btn nb-btn-primary">Save Configuration</button>
      </div>
    </form>
  `;

  document.getElementById('settings-form').addEventListener('submit', async e => {
    e.preventDefault();
    const updates = {};
    ['cors.allowedOrigins', 'maxUploadMb', 'rateLimitPerIp', 'rateLimitPerKey'].forEach(k => {
      const el = document.getElementById('setting-' + k.replace('.', '-'));
      if (el) updates[k] = el.value;
    });
    const res = await apiFetch('/config', { method: 'POST', body: JSON.stringify(updates) });
    if (!res) return;
    const d = await res.json();
    if (res.ok) toast(d.message || 'Config saved');
    else toast(d.error || 'Save failed', 'error');
  });
}

function settingField(key, label, value, type, hint = '') {
  const id = 'setting-' + key.replace('.', '-');
  const readonly = (type === 'number' && (key === 'port')) || key === 'host';
  return `
    <div>
      <label for="${id}" class="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">${label}</label>
      <input id="${id}" type="${type}" value="${esc(String(value))}" ${readonly ? 'readonly class="nb-input bg-gray-50 text-gray-400 cursor-not-allowed"' : 'class="nb-input"'}>
      ${hint ? `<p class="text-xs text-gray-400 mt-1">${hint}</p>` : ''}
    </div>
  `;
}

// ─── Integration Guide ─────────────────────────────────────────────────────

function renderGuide(el) {
  const host = location.host;
  const wsProto = location.protocol === 'https:' ? 'wss:' : 'ws:';

  el.innerHTML = `
    <div class="page-header">
      <h2>Integration Guide</h2>
      <p>Copy-paste snippets to connect your frontend to Nodebase.</p>
    </div>

    <div class="max-w-3xl space-y-8">

      <section>
        <h3 class="text-base font-semibold text-gray-800 mb-1">1 — Register & Login</h3>
        <p class="text-sm text-gray-500 mb-3">Get a JWT token that you'll use for all subsequent requests.</p>
        ${codeBlock(`// Register a new user
const res = await fetch('http://${host}/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'you@example.com', password: 'secret123' })
});
const { token } = await res.json();

// Or login an existing user
const login = await fetch('http://${host}/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'you@example.com', password: 'secret123' })
});
const { token } = await login.json();`)}
      </section>

      <section>
        <h3 class="text-base font-semibold text-gray-800 mb-1">2 — Database (REST)</h3>
        <p class="text-sm text-gray-500 mb-3">Full CRUD on any collection. Documents are arbitrary JSON.</p>
        ${codeBlock(`const headers = {
  'Content-Type': 'application/json',
  'Authorization': 'Bearer ' + token
};

// Create a document
const doc = await fetch('http://${host}/db/posts', {
  method: 'POST', headers,
  body: JSON.stringify({ title: 'Hello world', views: 0 })
}).then(r => r.json());

// List all documents in a collection
const list = await fetch('http://${host}/db/posts', { headers })
  .then(r => r.json());

// Get a single document
const post = await fetch('http://${host}/db/posts/' + doc.id, { headers })
  .then(r => r.json());

// Update (merge/patch)
await fetch('http://${host}/db/posts/' + doc.id, {
  method: 'PATCH', headers,
  body: JSON.stringify({ views: 1 })
});

// Delete
await fetch('http://${host}/db/posts/' + doc.id, { method: 'DELETE', headers });`)}
      </section>

      <section>
        <h3 class="text-base font-semibold text-gray-800 mb-1">3 — Realtime (WebSocket)</h3>
        <p class="text-sm text-gray-500 mb-3">Subscribe to a collection and receive live events as documents are created, updated, or deleted.</p>
        ${codeBlock(`const ws = new WebSocket('${wsProto}//${host}/realtime?token=' + encodeURIComponent(token));

ws.onopen = () => {
  // Subscribe to a collection
  ws.send(JSON.stringify({ type: 'subscribe', channel: 'db/posts' }));

  // Or subscribe to a single document
  // ws.send(JSON.stringify({ type: 'subscribe', channel: 'db/posts/' + docId }));
};

ws.onmessage = e => {
  const msg = JSON.parse(e.data);
  if (msg.type === 'subscribed') {
    console.log('Subscribed to', msg.channel);
  } else if (msg.type === 'event') {
    const { type, collection, documentId, data } = msg.event;
    // type is one of: CREATED | UPDATED | DELETED
    console.log(type, collection, documentId, data);
  }
};

// Unsubscribe
ws.send(JSON.stringify({ type: 'unsubscribe', channel: 'db/posts' }));`)}
      </section>

      <section>
        <h3 class="text-base font-semibold text-gray-800 mb-1">4 — File Storage</h3>
        <p class="text-sm text-gray-500 mb-3">Upload and serve files in named buckets.</p>
        ${codeBlock(`// Upload a file (multipart/form-data style via raw PUT)
const fileBlob = new Blob(['hello world'], { type: 'text/plain' });
await fetch('http://${host}/storage/uploads/hello.txt', {
  method: 'PUT',
  headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'text/plain' },
  body: fileBlob
});

// Download a file
const file = await fetch('http://${host}/storage/uploads/hello.txt', {
  headers: { 'Authorization': 'Bearer ' + token }
});
const text = await file.text();

// List files in a bucket
const files = await fetch('http://${host}/storage/uploads', {
  headers: { 'Authorization': 'Bearer ' + token }
}).then(r => r.json());`)}
      </section>

      <section>
        <h3 class="text-base font-semibold text-gray-800 mb-1">5 — Java SDK</h3>
        <p class="text-sm text-gray-500 mb-3">Add the SDK to your Java project to interact with Nodebase programmatically.</p>
        ${codeBlock(`NodebaseClient client = new NodebaseClient("http://${host}");
client.auth().login("you@example.com", "secret123");

// Database
client.database().collection("posts")
    .add(Map.of("title", "Hello", "views", 0));

List<Document> posts = client.database().collection("posts").list();

// Storage
client.storage().upload("avatars", "profile.png",
    Files.newInputStream(path), "image/png", Files.size(path));`)}
      </section>

    </div>
  `;
}

function codeBlock(code) {
  return `<div style="position:relative">
    <pre class="code-block">${esc(code)}<button class="copy-btn" onclick="copyCode(this)">Copy</button></pre>
  </div>`;
}

// ─── Utilities ─────────────────────────────────────────────────────────────

function esc(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ─── Boot ──────────────────────────────────────────────────────────────────

(function boot() {
  if (getToken()) {
    showApp();
    activateTab('overview');
  } else {
    showLogin();
  }
})();
