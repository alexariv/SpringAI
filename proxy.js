// proxy.js
const express = require('express');
const path = require('path');
const fs = require('fs');
const yaml = require('js-yaml');
const fetch = require('node-fetch');

const app = express();
app.use(express.json());

app.use(express.static(__dirname));

const APP_YAML_PATH = path.join(__dirname, 'src', 'main', 'resources', 'application.yaml');

let cachedModel = null;
app.get('/api/model', (req, res) => {
  try {
    const raw = fs.readFileSync(APP_YAML_PATH, 'utf8');
    const doc = yaml.load(raw);
    const model =
      doc?.spring?.ai?.ollama?.chat?.options?.model ||
      doc?.spring?.ai?.ollama?.model ||
      doc?.spring?.ai?.model ||
      'unknown';
    cachedModel = model;
    res.json({ model });
  } catch (e) {
    console.error('Error reading application.yml:', e);
    res.json({ model: cachedModel || 'unknown' });
  }
});

// Proxy POST /api/search/semantic to Spring through SSH tunnel
app.post('/api/search/semantic', async (req, res) => {
  const start = process.hrtime.bigint();
  try {
    const backendUrl = 'http://127.0.0.1:3000/api/search/semantic';

    console.log('Proxy POST to:', backendUrl, 'body:', req.body);

    const response = await fetch(backendUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req.body),
    });

    const text = await response.text();
    const end = process.hrtime.bigint();
    const upstreamSec = Number(end - start) / 1e9;

    let payload = text;
    try { payload = JSON.parse(text); } catch {}

    res.set('X-Upstream-Duration-s', upstreamSec.toFixed(2));
    res.set('Access-Control-Allow-Origin', '*');
    res.set('Access-Control-Expose-Headers', 'X-Upstream-Duration-s');

    res.status(response.status).send(payload);
  } catch (e) {
    const end = process.hrtime.bigint();
    const upstreamSec = Number(end - start) / 1e9;
    console.error('Error in proxy /api/search/semantic:', e);

    res.set('X-Upstream-Duration-s', upstreamSec.toFixed(2));
    res.status(500).json({ error: String(e) });
  }
});

// Start proxy server
app.listen(9000, () => {
  console.log('Now running @ http://localhost:9000/index.html');
});