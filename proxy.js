// proxy.js
const express = require('express');
const path = require('path');
const fs = require('fs');
const yaml = require('js-yaml');

const app = express();
app.use(express.json());

app.use(express.static(path.join(__dirname, 'src', 'main', 'resources', 'static')));

const APP_YAML_PATH = path.join(__dirname, 'src', 'main', 'resources', 'application.yaml');

let cachedModel = null;
function loadModelName() {
  try {
    const raw = fs.readFileSync(APP_YAML_PATH, 'utf8');
    const doc = yaml.load(raw);
    const model =
      doc?.spring?.ai?.ollama?.chat?.options?.model ||
      doc?.spring?.ai?.ollama?.model ||
      doc?.spring?.ai?.model ||
      'unknown';
    cachedModel = model;
    return model;
  } catch (e) {
    console.error('Error reading application.yaml:', e.message);
    return 'unknown';
  }
}

// Proxy POST /api/search/semantic to Spring through SSH tunnel
app.post('/api/search/semantic', async (req, res) => {
  const start = Date.now();
  try {
    const backendUrl = 'http://127.0.0.1:3000/api/search/semantic';
    console.log('[SEARCH] Starting search...');
    console.log('[SEARCH] Query:', req.body.query);
    console.log('[SEARCH] Filters:', {
      logbooks: req.body.logbooks,
      tags: req.body.tags,
      dateRange: `${req.body.createdDateFrom || 'any'} to ${req.body.createdDateTo || 'any'}`
    });

    const response = await fetch(backendUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req.body),
    });

    const text = await response.text();
    const duration = ((Date.now() - start) / 1000).toFixed(2);
    
    console.log(`[SEARCH] Completed in ${duration}s`);
    
    let payload = text;
    try { 
      payload = JSON.parse(text);
      console.log(`[SEARCH] Found ${payload.hits?.length || 0} results`);
    } catch {}

    res.status(response.status).send(payload);
  } catch (e) {
    const duration = ((Date.now() - start) / 1000).toFixed(2);
    console.error(`[SEARCH] Error after ${duration}s:`, e.message);
    res.status(500).json({ error: String(e) });
  }
});

// Proxy POST /api/search/analyze
app.post('/api/search/analyze', async (req, res) => {
  const start = Date.now();
  try {
    const backendUrl = 'http://127.0.0.1:3000/api/search/analyze';
    console.log('[ANALYSIS] Starting analysis...');

    const response = await fetch(backendUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req.body),
    });

    const text = await response.text();
    const duration = ((Date.now() - start) / 1000).toFixed(2);
    
    console.log(`[ANALYSIS] Completed in ${duration}s`);
    
    let payload = text;
    try { payload = JSON.parse(text); } catch {}

    res.status(response.status).send(payload);
  } catch (e) {
    const duration = ((Date.now() - start) / 1000).toFixed(2);
    console.error(`[ANALYSIS] Error after ${duration}s:`, e.message);
    res.status(500).json({ error: String(e) });
  }
});

// Start proxy server
app.listen(9000, () => {
  const modelName = loadModelName();
  console.log('Now running @ http://localhost:9000/index.html');
  console.log(`LLM Model: ${modelName}`);
});