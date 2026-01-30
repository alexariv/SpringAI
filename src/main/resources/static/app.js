//app.js

// Available filters
const LOGBOOKS = [
  "Acc Control Software", "Controls Commissioning", "Diagnositics",
  "Electronics Maintenance", "Fault Reports", "gbassi", "LOTO",
  "Machine Physics", "Mechanical Technicians", "Operations"
];

const TAGS = [
  "Active Interlock", "Alarm", "ARMs", "Authorization", "Beam Available",
  "Beam Dump", "Beamline", "Call In/Called", "Checklists", "Controls",
  "Cryo", "Diagnostics", "EPS/PPS", "Fault", "Feedback", "FLOCO",
  "Injection", "Interlock Tests", "Maintenance", "MASAR", "Power Supplies",
  "RCT", "Reference", "RF Systems", "SoftIOC", "Start Shift", "Studies",
  "Summary", "Testing", "Timely Order", "Timing Systems", "Utilities",
  "Vacuum", "Work Permits"
];

// State
let selectedLogbooks = [];
let selectedTags = [];
let currentResults = [];
let currentAnalysis = null;
let selectedResultIndex = null;

// Initialize filters
function initFilters() {
  const logbooksContainer = document.getElementById('logbooks');
  const tagsContainer = document.getElementById('tags');

  LOGBOOKS.forEach(lb => {
    const div = document.createElement('div');
    div.className = 'filter-item';
    div.textContent = lb;
    div.onclick = () => toggleSelection('logbook', lb, div);
    logbooksContainer.appendChild(div);
  });

  TAGS.forEach(tag => {
    const div = document.createElement('div');
    div.className = 'filter-item';
    div.textContent = tag;
    div.onclick = () => toggleSelection('tag', tag, div);
    tagsContainer.appendChild(div);
  });
}

function toggleFilter(id) {
  const element = document.getElementById(id);
  element.classList.toggle('collapsed');
}

function toggleSelection(type, value, element) {
  if (type === 'logbook') {
    const idx = selectedLogbooks.indexOf(value);
    if (idx === -1) {
      selectedLogbooks.push(value);
      element.classList.add('selected');
    } else {
      selectedLogbooks.splice(idx, 1);
      element.classList.remove('selected');
    }
  } else if (type === 'tag') {
    const idx = selectedTags.indexOf(value);
    if (idx === -1) {
      selectedTags.push(value);
      element.classList.add('selected');
    } else {
      selectedTags.splice(idx, 1);
      element.classList.remove('selected');
    }
  }
}

function clearDates() {
  document.getElementById('dateFrom').value = '';
  document.getElementById('dateTo').value = '';
}

// Build request payload
function buildPayload() {
  const query = document.getElementById('searchInput').value.trim();
  const dateFrom = document.getElementById('dateFrom').value || null;
  const dateTo = document.getElementById('dateTo').value || null;

  return {
    query,
    createdDateFrom: dateFrom,
    createdDateTo: dateTo,
    logbooks: selectedLogbooks.length > 0 ? selectedLogbooks : null,
    tags: selectedTags.length > 0 ? selectedTags : null
  };
}

// Simple Search
async function doSimpleSearch() {
  const payload = buildPayload();
  if (!payload.query) {
    alert('Please enter a search query');
    return;
  }

  disableButtons(true);
  showLoading('resultsList', 'Searching...');
  clearDetail();
  currentAnalysis = null;

  try {
    const res = await fetch('/api/search/semantic', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const data = await res.json();

    if (!res.ok) {
      showError('resultsList', data.message || 'Search failed');
      return;
    }

    currentResults = data.hits || [];
    displayResults(currentResults);

  } catch (err) {
    showError('resultsList', err.message);
  } finally {
    disableButtons(false);
  }
}

// Advanced Search (LLM analysis)
async function doAdvancedSearch() {
  const payload = buildPayload();
  if (!payload.query) {
    alert('Please enter a search query');
    return;
  }

  disableButtons(true);
  showLoading('resultsList', 'Searching...');
  clearDetail();
  currentAnalysis = null;

  try {
    // Get search results
    const searchRes = await fetch('/api/search/semantic', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const searchData = await searchRes.json();

    if (!searchRes.ok) {
      showError('resultsList', searchData.message || 'Search failed');
      disableButtons(false);
      return;
    }

    currentResults = searchData.hits || [];
    displayResults(currentResults);

    // Get analysis
    showAnalysisLoading();

    const analysisRes = await fetch('/api/search/analyze', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        query: payload.query,
        hits: currentResults
      })
    });

    const analysisData = await analysisRes.json();

    if (analysisRes.ok) {
      currentAnalysis = analysisData.analysis;
      displayAnalysis(currentAnalysis);
    } else {
      showError('detailPanel', 'Analysis failed: ' + (analysisData.message || 'Unknown error'));
    }

  } catch (err) {
    showError('resultsList', err.message);
  } finally {
    disableButtons(false);
  }
}

// Display
function displayResults(hits) {
  const container = document.getElementById('resultsList');
  container.innerHTML = '';

  if (hits.length === 0) {
    container.innerHTML = '<div class="placeholder">No results found</div>';
    return;
  }

  hits.forEach((hit, index) => {
    const div = document.createElement('div');
    div.className = 'result-item';
    
    const title = document.createElement('div');
    title.className = 'result-title';
    title.textContent = hit.content || 'No description';
    
    const meta = document.createElement('div');
    meta.className = 'result-meta';
    const owner = hit.metadata?.owner || 'Unknown';
    const date = hit.metadata?.createdDate || '';
    meta.textContent = `${owner} • ${date}`;
    
    div.appendChild(title);
    div.appendChild(meta);
    div.onclick = () => selectResult(index, div);
    
    container.appendChild(div);
  });
}

function selectResult(index, element) {
  document.querySelectorAll('.result-item').forEach(el => el.classList.remove('selected'));
  element.classList.add('selected');
  selectedResultIndex = index;
  
  displayDetail(currentResults[index]);
}

function displayDetail(hit) {
  const panel = document.getElementById('detailPanel');
  
  let html = '';
  
  // Display analysis (advanced search)
  if (currentAnalysis) {
    html += `
      <div class="analysis-section">
        <h3>Analysis</h3>
         <div>${marked.parse(currentAnalysis)}</div>
      </div>
    `;
  }
  
  // Show details of selected hits
  html += `
    <div class="detail-view">
      <h3>Log Entry Detail</h3>
      <div class="detail-content">${hit.content || 'No description'}</div>
      <div class="metadata-grid">
  `;
  
  if (hit.metadata) {
    if (hit.metadata.owner) {
      html += `<div class="metadata-label">Owner:</div><div class="metadata-value">${hit.metadata.owner}</div>`;
    }
    if (hit.metadata.createdDate) {
      html += `<div class="metadata-label">Created:</div><div class="metadata-value">${hit.metadata.createdDate}</div>`;
    }
    if (hit.metadata.level) {
      html += `<div class="metadata-label">Level:</div><div class="metadata-value">${hit.metadata.level}</div>`;
    }
    if (hit.metadata.state) {
      html += `<div class="metadata-label">State:</div><div class="metadata-value">${hit.metadata.state}</div>`;
    }
    if (hit.metadata.logbooks_name) {
      html += `<div class="metadata-label">Logbook:</div><div class="metadata-value">${hit.metadata.logbooks_name}</div>`;
    }
    if (hit.metadata.tags_name) {
      const tags = Array.isArray(hit.metadata.tags_name) ? hit.metadata.tags_name.join(', ') : hit.metadata.tags_name;
      html += `<div class="metadata-label">Tags:</div><div class="metadata-value">${tags}</div>`;
    }
  }
  
  html += `
      </div>
    </div>
  `;
  
  panel.innerHTML = html;
}

function displayAnalysis(analysis) {
  if (selectedResultIndex !== null) {
    // If a result is already selected, redisplay it with analysis
    displayDetail(currentResults[selectedResultIndex]);
  } else {
    // Just show analysis
    const panel = document.getElementById('detailPanel');
    panel.innerHTML = `
      <div class="analysis-section">
        <h3>Analysis</h3>
        <div>${analysis}</div>
        <div>${marked.parse(analysis)}</div>
      </div>
    `;
  }
}

function showAnalysisLoading() {
  const panel = document.getElementById('detailPanel');
  panel.innerHTML = `
    <div class="analysis-section">
      <h3>Analysis of Results Found</h3>
      <div class="loading">Analyzing logs...</div>
    </div>
  `;
}

function clearDetail() {
  const panel = document.getElementById('detailPanel');
  panel.innerHTML = '<div class="placeholder">Select an entry to view details</div>';
  selectedResultIndex = null;
}

function showLoading(containerId, message) {
  document.getElementById(containerId).innerHTML = `<div class="placeholder loading">${message}</div>`;
}

function showError(containerId, message) {
  document.getElementById(containerId).innerHTML = `<div class="error">Error: ${message}</div>`;
}

function disableButtons(disabled) {
  document.getElementById('simpleBtn').disabled = disabled;
  document.getElementById('advancedBtn').disabled = disabled;
}

document.getElementById('searchInput').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    doSimpleSearch();
  }
});

// Initialize on load
initFilters();