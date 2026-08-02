// =========== Helpers (consolidated) ===========
function $(id) { return document.getElementById(id); }

async function fetchJson(url, options) {
    const res = await fetch(url, options);
    return res.json();
}

async function postJson(url, body) {
    return fetch(url, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body) });
}

async function putJson(url, body) {
    return fetch(url, { method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body) });
}

// Reusable pagination renderer
// cfg: { containerId, currentPage, totalPages, totalItems, pageSize, onPage(p), onPageSize(s), showPageSize }
function renderPaginationControls(cfg) {
    var el = $(cfg.containerId);
    if (!el) return;
    if (cfg.totalItems === 0 || cfg.totalPages <= 0) { el.innerHTML = ''; return; }

    var cp = cfg.currentPage, tp = cfg.totalPages, ps = cfg.pageSize;
    var maxButtons = 5;
    var startPage = Math.max(1, cp - Math.floor(maxButtons / 2));
    var endPage = Math.min(tp, startPage + maxButtons - 1);
    if (endPage - startPage < maxButtons - 1) startPage = Math.max(1, endPage - maxButtons + 1);

    var pageButtons = '';
    for (var i = startPage; i <= endPage; i++) {
        pageButtons += '<button class="page-btn ' + (i === cp ? 'active' : '') + '" onclick="' + cfg.onPage + '(' + i + ')">' + i + '</button>';
    }

    var pageSizeHtml = '';
    if (cfg.showPageSize) {
        pageSizeHtml = '<select class="page-size-select" onchange="' + cfg.onPageSize + '(this.value)">'
            + '<option ' + (ps == 10 ? 'selected' : '') + '>10</option>'
            + '<option ' + (ps == 25 ? 'selected' : '') + '>25</option>'
            + '<option ' + (ps == 50 ? 'selected' : '') + '>50</option>'
            + '</select>';
    }

    var start = (cp - 1) * ps + 1;
    var end = Math.min(cp * ps, cfg.totalItems);

    el.innerHTML = '<span class="page-info">Showing ' + start + '\u2013' + end + ' of ' + cfg.totalItems + (cfg.itemLabel || '') + '</span>'
        + '<div class="page-controls">'
        + pageSizeHtml
        + '<button class="page-btn" onclick="' + cfg.onPage + '(1)" ' + (cp === 1 ? 'disabled' : '') + '>&laquo;</button>'
        + '<button class="page-btn" onclick="' + cfg.onPage + '(' + (cp - 1) + ')" ' + (cp === 1 ? 'disabled' : '') + '>&lsaquo;</button>'
        + pageButtons
        + '<button class="page-btn" onclick="' + cfg.onPage + '(' + (cp + 1) + ')" ' + (cp === tp ? 'disabled' : '') + '>&rsaquo;</button>'
        + '<button class="page-btn" onclick="' + cfg.onPage + '(' + tp + ')" ' + (cp === tp ? 'disabled' : '') + '>&raquo;</button>'
        + '</div>';
}


let stompClient = null;
const tasks = new Map();
const prevStatus = new Map();
const selectedIds = new Set();
let currentPage = 1;
let pageSize = 10;
let sortCol = null;
let sortDir = 'asc';
let statusChart = null;

let autoScroll = false;



// ================= Toast Notifications =================
function showToast(message, type = 'info') {
    const container = $('toastContainer');
    while(container.children.length >= 3) container.firstChild.remove();
    const toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 4000);
}



// ================= Confirm Dialog =================
function showConfirm(msg, onYes) {
    const msgEl = $('confirmMsg');
    msgEl.innerHTML = msg;
    $('confirmDialog').classList.add('active');
    const yesBtn = $('confirmYes');
    if(onYes){
        yesBtn.style.display = '';
        yesBtn.onclick = function(){ closeConfirm(); onYes();};
    }
    else{
        yesBtn.style.display = 'none';
    }
}

function closeConfirm() {
    $('confirmDialog').classList.remove('active');
}

function openPanelModal(id){
    $(id).classList.add('active');
    if(id === 'statusModal' && lastStats) updateChart(lastStats);
    if(id === 'hrModal' && lastStats) renderHashRing(lastStats);
    if(id === 'frModal') loadFailureRates();
}
function closePanelModal(id){
    $(id).classList.remove('active');
}
// ================= Task Detail Modal =================
async function openModal(taskId) {
    const t = tasks.get(taskId);
    if(!t){
        try{
            const res = await fetch('api/tasks/' + taskId);
            if(!res.ok) return;
            t = await res.json();
            tasks.set(t.id,t);

        }
        catch(e){
            return;
        }
    }
    
    const body = $('modalBody');

    const retryBtn = (t.status === 'FAILED' || t.status === 'CANCELLED')
        ? `<button class="btn-retry" onclick="retryTask('${t.id}')">Re-submit Task</button>` : '';

    body.innerHTML = `
        <div class="detail-grid">
            <div class="detail-item"><label>ID</label><div class="val" style="font-family:monospace;color:var(--info)">${t.id}</div></div>
            <div class="detail-item"><label>Name</label><div class="val">${t.name || '-'}</div></div>
            <div class="detail-item"><label>Type</label><div class="val">${t.type}</div></div>
            <div class="detail-item"><label>Priority</label><div class="val"><span class="priority-${t.priority}">${t.priority}</span></div></div>
            <div class="detail-item"><label>Status</label><div class="val"><span class="status-badge status-${t.status}">${t.status}</span></div></div>
            <div class="detail-item"><label>Worker</label><div class="val">${t.assignedWorker || '-'}</div></div>
            <div class="detail-item"><label>Retry Count</label><div class="val">${t.retryCount} / ${t.maxRetries}</div></div>
            <div class="detail-item"><label>Execution Time</label><div class="val">${t.executionTimeMs > 0 ? t.executionTimeMs + 'ms' : '-'}</div></div>
            <div class="detail-item"><label>Created</label><div class="val">${t.createdAt || '-'}</div></div>
            <div class="detail-item"><label>Started</label><div class="val">${t.startedAt || '-'}</div></div>
            <div class="detail-item"><label>Completed</label><div class="val">${t.completedAt || '-'}</div></div>
            <div class="detail-item"><label>Scheduled</label><div class="val">${t.scheduledAt || '-'}</div></div>
            ${t.result ? `<div class="detail-item full"><label>Result</label><pre>${t.result}</pre>${buildOutputButtons(t)}</div>` : ''}
            ${t.errorMessage ? `<div class="detail-item full"><label>Error</label><pre style="color:var(--danger)">${t.errorMessage}</pre></div>` : ''}
            ${t.payload ? `<div class="detail-item full"><label>Payload</label><pre>${t.payload}</pre></div>` : ''}
        </div>
        ${retryBtn}
        <div id="outputPreviewContainer"></div>
        <div id="dagContainer" style="margin-top:12px"><div style="font-size:12px;color:var(--muted)">Loading dependencies...</div></div>
        <div class="timeline" id="timelineContainer"><div class="timeline-title">Execution Timeline</div><div style="font-size:12px;color:var(--muted)">Loading...</div></div>
    `;
    $('taskModal').classList.add('active');

    // Fetch attempt timeline + dag info
    loadDagInfo(taskId, $('dagContainer'));
    try {
        const res = await fetch('/api/tasks/' + taskId + '/attempts');
        if (res.ok) {
            const attempts = await res.json();
            renderTimeline(attempts);
        }
    } catch(e) {}
}

function renderTimeline(attempts) {
    const container = $('timelineContainer');
    if (!attempts || attempts.length === 0) {
        container.innerHTML = '<div class="timeline-title">Execution Timeline</div><div style="font-size:12px;color:var(--muted)">No attempts recorded yet.</div>';
        return;
    }
    container.innerHTML = `<div class="timeline-title">Execution Timeline (${attempts.length} attempt${attempts.length > 1 ? 's' : ''})</div>` +
        attempts.map(a => {
            const cls = a.resultStatus === 'COMPLETED' ? 't-success' : 't-fail';
            return `<div class="timeline-item ${cls}">
                <div class="timeline-info">
                    <strong>Attempt #${a.attemptNumber}</strong> on <strong>${a.workerName}</strong><br>
                    ${a.startedAt || '-'} → ${a.endedAt || '-'} (${a.durationMs}ms)<br>
                    ${a.resultStatus === 'COMPLETED' ? '<span style="color:var(--success)">✓ Completed</span>' : '<span style="color:var(--danger)">✗ Failed</span>'}
                </div>
            </div>`;
        }).join('');
}
// function buildOutputButtons(t) {
//     if (!t.result || typeof t.result !== 'string') return ''; // Safely check for string type
//     const csvMatch = t.result.match(/data[/\\\\](?:output)[/\\\\](?:[\\w._-]+[/\\\\])?[\\w._-]+\.csv/);
    
//     let btns = '';
//     if (csvMatch) {
//         btns += `<div style="margin-top:8px;display:flex;gap:6px">
//             <button class="btn btn-sm btn-primary" onclick="previewCsvOutput('${t.id}')">Preview CSV</button>
//             <a class="btn btn-sm btn-outline" href="/api/tasks/${t.id}/output" download style="text-decoration:none">Download CSV</a>
//         </div>`;
//     }
    
//     return btns;
// }
function buildOutputButtons(t) {
    if (!t.result) return '';
    
    // Safely convert to string in case the backend returns a JSON object
    const resultStr = typeof t.result === 'object' ? JSON.stringify(t.result) : String(t.result);
    
    // Relaxed regex: looks for any filename ending in .csv (case-insensitive)
    const csvMatch = resultStr.match(/([\w./\\-]+\.csv)/i);
    
    let btns = '';
    if (csvMatch) {
        btns += `<div style="margin-top:8px;display:flex;gap:6px">
            <button class="btn btn-sm btn-primary" onclick="previewCsvOutput('${t.id}')">Preview CSV</button>
            <a class="btn btn-sm btn-outline" href="/api/tasks/${t.id}/output" download style="text-decoration:none">Download CSV</a>
        </div>`;
    }
    
    return btns;
}

async function previewCsvOutput(taskId) {
    const container = $('outputPreviewContainer');
    container.innerHTML = '<div style="font-size:12px;color:var(--muted)">Loading preview...</div>';
    try {
        const res = await fetch('/api/tasks/' + taskId + '/output');
        if (!res.ok) { container.innerHTML = '<div style="color:var(--danger)">Failed to load output</div>'; return; }
        const text = await res.text();
        const lines = text.trim().split('\n');
        if (lines.length === 0) { container.innerHTML = ''; return; }
        const headers = lines[0].split(',');
        const rows = lines.slice(1, 21); // Show first 20 rows
        let html = '<div style="margin-top:12px"><strong style="font-size:12px">CSV Preview (' + (lines.length-1) + ' rows, showing first ' + Math.min(20, rows.length) + ')</strong>';
        html += '<div style="overflow-x:auto;margin-top:6px"><table class="task-table" style="font-size:11px"><thead><tr>';
        headers.forEach(h => html += '<th style="padding:4px 8px">' + h.trim() + '</th>');
        html += '</tr></thead><tbody>';
        rows.forEach(r => {
            const cols = r.split(',');
            html += '<tr>' + cols.map(c => '<td style="padding:4px 8px">' + c.trim() + '</td>').join('') + '</tr>';
        });
        html += '</tbody></table></div></div>';
        container.innerHTML = html;
    } catch(e) { container.innerHTML = '<div style="color:var(--danger)">Error: ' + e.message + '</div>'; }
}


async function retryTask(id) {
    const res = await fetch('/api/tasks/' + id + '/retry', { method: 'POST' });
    if (res.ok) {
        showToast('Task reset and re-submitted (same ID)', 'success');
        closeModal();
        refreshTasks();
    } else {
        const err = await res.json().catch(() => ({}));
        showToast(err.error || 'Failed to retry task', 'error');
    }
}

function closeModal() {
    $('taskModal').classList.remove('active');
}

// ================= Collapsible Submit Form =================
function toggleForm(btn) {
    btn.classList.toggle('collapsed');
    btn.closest('.panel').querySelector('.form-grid').classList.toggle('collapsed');
}

// ================= WebSocket =================
function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function() {
        $('statusDot').className = 'status-dot connected';
        $('statusText').textContent = 'Connected';

        stompClient.subscribe('/topic/tasks', function(msg) {
            const task = JSON.parse(msg.body);
            const oldStatus = tasks.has(task.id) ? tasks.get(task.id).status : null;
            if (oldStatus && oldStatus !== task.status) {
                prevStatus.set(task.id, { from: oldStatus, to: task.status, time: Date.now() });
                if (task.status === 'COMPLETED') {
                    showToast(task.name + ' completed', 'success');
                } else if (task.status === 'FAILED') {
                    showToast(task.name + ' failed: ' + (task.errorMessage || ''), 'error');
                } else if (task.status === 'RETRYING') {
                    showToast(task.name + ' retrying (' + task.retryCount + '/' + task.maxRetries + ')', 'warning');
                }
            }
            tasks.set(task.id, task);
            updateCanvasNodeStatus(task.id, task.status);
            updateLastUpdate();
            refreshTasks();
            updateFavicon();
        });

        stompClient.subscribe('/topic/stats', function(msg) {
            updateStats(JSON.parse(msg.body));
            updateLastUpdate();
        });

        stompClient.subscribe('/topic/workers', function(msg) {});
            refreshTasks();
            refreshWorkers();
            refreshStats();
            refreshWorkflows();
            loadFailureRates();
        
    }, function() {
        $('statusDot').className = 'status-dot disconnected';
        $('statusText').textContent = 'Disconnected';
        setTimeout(connect, 3000);
    });
}

// ================= Connection Quality =================
function updateLastUpdate() {
    $('lastUpdate').textContent = 'Updated: ' + new Date().toLocaleTimeString();
}
const NAME_REGEX = /^[a-zA-Z0-9_]+$/;
function isValidName(name) { return !name || NAME_REGEX.test(name);}

// ================= API Calls =================
async function submitTask() {
    const rawName = $('taskName').value;
    if(rawName && !isValidName(rawName)){
        showToast('Task name can only contain letters, digits, and underscores', 'warning');
        return;
    }
    const payloadRaw = $('taskPayload').value.trim();
    if(!payloadRaw){
        showToast('Payload is required. Use the builder to create one.', 'warning');
        return;
    }
    try{
        JSON.parse(payloadRaw);
    }
    catch(e){
        showToast('Invalid JSON payload: ' + e.message, 'error');
        return;
    }
    const idempotentKey = $('taskIdempotentKey') ? $('taskIdempotentKey').value : '';
    const body = {
        name: rawName || $('taskType').value.toLowerCase() + '_' + Date.now(),
        type: $('taskType').value,
        priority: $('taskPriority').value,
        payload: payloadRaw,
        maxRetries: parseInt($('taskRetries').value) || 3,
        timeoutMs: (parseInt($('taskTimeout').value) || 60) * 1000,
    };
    if(idempotentKey) body.idempotentKey = idempotentKey;
    const res = await fetch('/api/tasks', {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
    });
    if(res.status === 429){
        const err = await res.json().catch(() => ({}));
        showToast(err.error || 'Rate limit exceeded! Wait and try again.', 'error');
    }
    else if (res.ok) {
        $('taskName').value = '';
        $('taskPayload').value = '';
        if($('taskIdempotentKey')) $('taskIdempotentKey').value = '';
        showToast('Task submitted', 'info');
    }
}



async function cancelTask(id) {

    const t = tasks.get(id);
    if(t && !canCancel(t)){
        showToast('Cannot cancel task in ' + t.status + 'status','error');
        return;
    }
    showConfirm('Cancel this task?', async () => {
        const res = await fetch('/api/tasks/' + id, {method: 'DELETE'});
        if(res.ok){
            showToast('Task cancelled', 'warning');
        }
        else{
            const err = await res.json().catch(() => ({}));
            showToast(err.error || 'Failed to cancel task','error');
        }
        
        
        refreshTasks();
    });
}

async function refreshTasks() {

    const params = new URLSearchParams();
    params.set('page', currentPage - 1);
    params.set('size', pageSize);
    const idFilter = $('filterId') ? $('filterId').value : '';
    const nameFilter = $('filterName') ? $('filterName').value : '';
    const typeFilter = $('filterType') ? $('filterType').value : '';
    const priorityFilter = $('filterPriority') ? $('filterPriority').value : '';
    const statusFilter = $('filterStatus') ? $('filterStatus').value : '';
    const workerFilter = $('filterWorker') ? $('filterWorker').value : '';

    if(idFilter) params.set('id', idFilter);
    if(nameFilter) params.set('name', nameFilter);
    if(typeFilter) params.set('type', typeFilter);
    if(priorityFilter) params.set('priority', priorityFilter);
    if(statusFilter) params.set('status', statusFilter);
    // if(workerFilter) params.set('worker', workerFilter);

    if(workerFilter) params.set('worker', workerFilter);

    if(sortCol) {
        params.set('sortBy', sortCol);
        params.set('sortDir', sortDir);
    }

   const response = await fetch('/api/tasks?' + params.toString());
    const data = await response.json(); // Parse the response to JSON

    tasks.clear();
    (data.content || []).forEach(t => tasks.set(t.id, t));
    renderTasksFromServer(data);
}


async function refreshStats() {
    const res = await fetch('/api/stats');
    updateStats(await res.json());
}

async function refreshWorkers() {
    const res = await fetch('/api/workers');
    renderWorkers(await res.json());
}

setInterval(refreshWorkflows, 5000);
setInterval(refreshWorkers, 3000);
setInterval(refreshStats, 3000);

// ================= Bulk Actions =================
function toggleSelectAll(checked) {
    const filtered = getFilteredTasks();
    const start = (currentPage - 1) * pageSize;
    const page = filtered.slice(start, start + pageSize);
    page.forEach(t => {
        if (checked && canCancel(t)) selectedIds.add(t.id);
        else selectedIds.delete(t.id);
    });
    updateBulkBar();
    renderTasks();
}

function toggleSelect(id) {
    if (selectedIds.has(id)) selectedIds.delete(id);
    else {
        const t = tasks.get(id);
        if(t && !canCancel(t)){
            showToast('Cannot select ' + t.status + ' task for cancellation', 'warning');
            return;
        }
        selectedIds.add(id);
    }
    updateBulkBar();
}

function updateBulkBar() {
    const bar = $('bulkBar');
    $('bulkCount').textContent = selectedIds.size;
    bar.classList.toggle('active', selectedIds.size > 0);
}

function clearSelection() {
    selectedIds.clear();
    $('selectAll').checked = false;
    updateBulkBar();
    renderTasks();
}

async function bulkCancel() {
    const ids = [...selectedIds];
    const cancellable = ids.filter(id => {const t = tasks.get(id); return t && canCancel(t); });
    const notCancellable = ids.filter(id => {const t = tasks.get(id); return t && !canCancel(t); });
    if(notCancellable.length > 0){
        showToast(notCancellable.length + ' task(s) cannot be cancelled (' + notCancellable.map(id => tasks.get(id).status).join(', ') + ')','error');

    }
    if(cancellable.length === 0){
        showToast('No cancellable tasks selected','warning');
        return;
    }
    showConfirm('Cancel ' + cancellable.length + ' canellable task(s)?' + (notCancellable.length > 0 ? ' (' + notCancellable.length + ' skipped)' : ''), async () => {
        
        for (const id of cancellable) {
            await fetch('/api/tasks/' + id, {method: 'DELETE'});
        }
        selectedIds.clear();
        updateBulkBar();
        showToast(cancellable.length + ' task(s) cancelled', 'warning');
        refreshTasks();
    });
}

// ================= Column Sorting =================
function sortBy(col) {
    document.querySelectorAll('.sort-arrow').forEach(el => el.textContent = '');
    if (sortCol === col) {
        sortDir = sortDir === 'asc' ? 'desc' : 'asc';
    } else {
        sortCol = col;
        sortDir = 'asc';
    }
    const arrow = $('sort-' + col);
    if (arrow) arrow.textContent = sortDir === 'asc' ? ' \u25B2' : ' \u25BC';
    refreshTasks();
}

// ================= Filtering =================
function getFilteredTasks() {
    const idFilter = $('filterId').value.toLowerCase();
    const nameFilter = $('filterName').value.toLowerCase();
    const typeFilter = $('filterType').value;
    const priorityFilter = $('filterPriority').value;
    const statusFilter = $('filterStatus').value;
    const workerFilter = $('filterWorker').value.toLowerCase();

    let result = [...tasks.values()].filter(t => {
        if (idFilter && !t.id.toLowerCase().includes(idFilter)) return false;
        if (nameFilter && !(t.name || '').toLowerCase().includes(nameFilter)) return false;
        if (typeFilter && t.type !== typeFilter) return false;
        if (priorityFilter && t.priority !== priorityFilter) return false;
        if (statusFilter && t.status !== statusFilter) return false;
        if (workerFilter && !(t.assignedWorker || '').toLowerCase().includes(workerFilter)) return false;
        return true;
    });

    if (sortCol) {
        const priOrder = {CRITICAL:0, HIGH:1, MEDIUM:2, LOW:3};
        const statOrder = {RUNNING:0, QUEUED:1, RETRYING:2, PENDING:3, FAILED:4, COMPLETED:5, CANCELLED:6};
        result.sort((a,b) => {
            let va = a[sortCol], vb = b[sortCol];
            if (sortCol === 'priority') { va = priOrder[va]||9; vb = priOrder[vb]||9; }
            else if (sortCol === 'status') { va = statOrder[va]||9; vb = statOrder[vb]||9; }
            else if (sortCol === 'executionTimeMs') { va = va||0; vb = vb||0; }
            else { va = (va||'').toString().toLowerCase(); vb = (vb||'').toString().toLowerCase(); }
            if (va < vb) return sortDir === 'asc' ? -1 : 1;
            if (va > vb) return sortDir === 'asc' ? 1 : -1;
            return 0;
        })
    } else {
        result.sort((a,b) => {
            const order = {RUNNING:0, QUEUED:1, RETRYING:2, PENDING:3, FAILED:4, COMPLETED:5, CANCELLED:6};
            const d = (order[a.status]||9) - (order[b.status]||9);
            return d !== 0 ? d : new Date(b.createdAt) - new Date(a.createdAt);
        });
    }
    return result;
}

function applyFilters() {
    currentPage = 1;
    refreshTasks();
}

function clearFilters() {
    $('filterId').value = '';
    $('filterName').value = '';
    $('filterType').value = '';
    $('filterPriority').value = '';
    $('filterStatus').value = '';
    $('filterWorker').value = '';
    currentPage = 1;
    refreshTasks();
}

// ================= Rendering =================
function renderTasks() {
    const tbody = $('taskTableBody');
    const filtered = getFilteredTasks();
    // Use the global server counts, fallback to local math if missing
    const totalPages = window.serverTotalPages || Math.max(1, Math.ceil(filtered.length / pageSize));
    const actualTotalItems = window.serverTotalItems || filtered.length;
    
    if(currentPage > totalPages) currentPage = totalPages;
    const start = (currentPage - 1) * pageSize;
    const page = filtered.slice(start, start + pageSize);

    
    

    if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="empty-state">No tasks match filters.</td></tr>';
    } else {
        tbody.innerHTML = page.map(t => {
            const flash = getFlashClass(t.id);
            const checked = selectedIds.has(t.id) ? 'checked' : '';
            return `
            <tr class="${flash}" onclick="openModal('${t.id}')" style="cursor:pointer">
                <td class="cb" onclick="event.stopPropagation()">
                    <input type="checkbox" ${checked} onchange="toggleSelect('${t.id}');updateBulkBar()">
                </td>
                <td class="id">${t.id}</td>
                <td>${t.name || '-'}</td>
                <td>${t.type}</td>
                <td><span class="priority-${t.priority}">${t.priority}</span></td>
                <td><span class="status-badge status-${t.status}">${t.status}</span>
                    ${t.retryCount > 0 ? '<span style="font-size:11px;color:var(--muted)"> ('+t.retryCount+'/'+t.maxRetries+')</span>' : ''}
                </td>
                <td style="font-size:12px;color:var(--muted)">${t.assignedWorker || '-'}</td>
                <td style="font-size:12px">${t.executionTimeMs > 0 ? t.executionTimeMs+'ms' : '-'}</td>
                <td onclick="event.stopPropagation()">${canCancel(t) ? '<button class="btn btn-sm btn-danger" onclick="cancelTask(\''+t.id+'\')">Cancel</button>' : ''}</td>
            </tr>`;
        }).join('');
    }

    renderPaginationControls(
        {
            containerId: 'pagination',
            currentPage: currentPage,
            totalPages: totalPages,
            totalItems: actualTotalItems,
            pageSize: pageSize,
            onPage: 'goToPage',
            onPageSize: 'changePageSize',
            showPageSize: true,
            itemLabel: ' tasks'
        }
    );

    if (autoScroll) {
        const running = tbody.querySelector('.status-RUNNING');
        if (running) running.closest('tr').scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

function renderTasksFromServer(data) {
    const tbody = $('taskTableBody');
    const page = data.content || [];
    // Safely check for totalElements, fallback to total, and finally fallback to the page array length
    const totalItems = data.totalItems || page.length; 
    const totalPages = data.totalPages || 1;

    // Save these globally for the bug fix in step 3!
    window.serverTotalPages = totalPages;
    window.serverTotalItems = totalItems;

    if (page.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="empty-state">No tasks match filters.</td></tr>';
    } else {
        tbody.innerHTML = page.map(t => {
            const flash = getFlashClass(t.id);
            const checked = selectedIds.has(t.id) ? 'checked' : '';
            return `
            <tr class="${flash}" onclick="openModal('${t.id}')" style="cursor:pointer">
                <td class="cb" onclick="event.stopPropagation()">
                    <input type="checkbox" ${checked} onchange="toggleSelect('${t.id}');updateBulkBar()">
                </td>
                <td class="id">${t.id}</td>
                <td>${t.name || '-'}</td>
                <td>${t.type}</td>
                <td><span class="priority-${t.priority}">${t.priority}</span></td>
                <td><span class="status-badge status-${t.status}">${t.status}</span>
                    ${t.retryCount > 0 ? '<span style="font-size:11px;color:var(--muted)"> ('+t.retryCount+'/'+t.maxRetries+')</span>' : ''}
                </td>
                <td style="font-size:12px;color:var(--muted)">${t.assignedWorker || '-'}</td>
                <td style="font-size:12px">${t.executionTimeMs > 0 ? t.executionTimeMs+'ms' : '-'}</td>
                <td onclick="event.stopPropagation()">${canCancel(t) ? '<button class="btn btn-sm btn-danger" onclick="cancelTask(\''+t.id+'\')">Cancel</button>' : ''}</td>
            </tr>`;
        }).join('');
    }

    renderPaginationControls(
        {
            containerId: 'pagination',
            currentPage: currentPage,
            totalPages: totalPages,
            totalItems: totalItems,
            pageSize: pageSize,
            onPage: 'goToPage',
            onPageSize: 'changePageSize',
            showPageSize: true,
            itemLabel: ' tasks'
        }
    );

    if (autoScroll) {
        const running = tbody.querySelector('.status-RUNNING');
        if (running) running.closest('tr').scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

function getFlashClass(taskId) {
    const p = prevStatus.get(taskId);
    if (!p || Date.now() - p.time > 1500) return '';
    if (p.to === 'COMPLETED') return 'flash-success';
    if (p.to === 'FAILED') return 'flash-fail';
    return 'flash';
}


function goToPage(p) { currentPage = p; refreshTasks(); }
function changePageSize(size) { pageSize = parseInt(size); currentPage = 1; refreshTasks(); }
function canCancel(t) { return ['PENDING','QUEUED','RUNNING','RETRYING'].includes(t.status); }

// ================= Stats + Chart =================
let lastStats = {};
function updateStats(s) {
    lastStats = s;
    $('statTotal').textContent = s.totalTasks;
    $('statQueued').textContent = s.queuedTasks + s.pendingTasks;
    $('statRunning').textContent = s.runningTasks;
    $('statCompleted').textContent = s.completedTasks;
    $('statFailed').textContent = s.failedTasks;
    $('statRetrying').textContent = s.retryingTasks;
    $('statThroughput').textContent = s.tasksCompletedLastMinute || 0;
    $('statDlq').textContent = s.dlqCount || 0;
    $('statRateLimit').textContent = s.rateLimitRejections || 0;
    $('statRecovered').textContent = s.recoveredTasks || 0;
    updateChart(s);
    updateFavicon();
    
    if(s.hashRingDistribution) renderHashRing(s);

}

function updateChart(s) {
    if (!s) return;
    const ctx = $('statusChart');
    if (!ctx) return;
    const data = [
        s.queuedTasks + (s.pendingTasks||0),
        s.runningTasks,
        s.completedTasks,
        s.failedTasks,
        s.retryingTasks
    ];
    const colors = ['#06b6d4','#3b82f6','#22c55e','#ef4444','#f59e0b'];
    const labels = ['Queued','Running','Completed','Failed','Retrying'];

    if (statusChart) {
        statusChart.data.datasets[0].data = data;
        statusChart.update();
    } else {
        statusChart = new Chart(ctx, {
            type: 'doughnut',
            data: { labels, datasets: [{ data, backgroundColor: colors, borderWidth: 0 }] },
            options: {
                responsive: true, maintainAspectRatio: true,
                plugins: {
                    legend: { position: 'bottom', labels: { color: getComputedStyle(document.body).getPropertyValue('--muted'), font: { size: 11 } } }
                },
                cutout: '60%'
            }
        });
    }
}

// ================= Worker Utilization Bars =================
function renderWorkers(list) {
    const el = $('workerList');
    if (!list || list.length === 0) {
        el.innerHTML = '<div class="empty-state">No workers</div>';
        return;
    }
    const activeCount = list.filter(w => w.status !== 'OFFLINE').length;
    
    const hideOffline  = $('hideOfflineToggle') && $('hideOfflineToggle').checked;
    const filtered = hideOffline ? list.filter(w => w.status != 'OFFLINE') : list;
    if(filtered.length === 0){
        el.innerHTML = '<div class ="empty-state">All workers offline (hidden)</div>';
        return;
    }
    el.innerHTML = filtered.map(w => {
        const total = w.tasksCompleted + w.tasksFailed;
        const okPct = total > 0 ? (w.tasksCompleted / total * 100) : 0;
        const failPct = total > 0 ? (w.tasksFailed / total * 100) : 0;
        
        return `
        <div class="worker-card">
            <div style="flex:1">
                <div class="worker-name">${w.name}</div>
                <div class="worker-stats">${w.tasksCompleted} succeeded / ${w.tasksFailed} failed (attempts)
                    ${w.currentTaskId ? ' | Task: '+w.currentTaskId : ''}
                </div>
                <div class="util-bar">
                    <div class="bar-ok" style="width:${okPct}%"></div>
                    <div class="bar-fail" style="width:${failPct}%"></div>
                </div>
            </div>
            <span class="badge badge-${w.status.toLowerCase()}">${w.status}</span>
            
        </div>`;
    }).join('');
}

// ================= Favicon Badge =================
function updateFavicon() {
    const running = lastStats.runningTasks || 0;
    const canvas = document.createElement('canvas');
    canvas.width = 32; canvas.height = 32;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#1e293b'; ctx.fillRect(0, 0, 32, 32);
    ctx.fillStyle = '#3b82f6'; ctx.font = 'bold 18px sans-serif'; ctx.textAlign = 'center';
    ctx.fillText('\u26A1', 16, 20);
    if (running > 0) {
        ctx.fillStyle = '#ef4444'; ctx.beginPath(); ctx.arc(24, 8, 8, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = '#fff'; ctx.font = 'bold 10px sans-serif'; ctx.fillText(running, 24, 11);
    }
    let link = document.querySelector("link[rel*='icon']") || document.createElement('link');
    link.rel = 'icon'; link.href = canvas.toDataURL();
    document.head.appendChild(link);
    document.title = running > 0 ? '(' + running + ') Task Scheduler' : 'Task Scheduler Dashboard';
}

// ================= Export CSV =================
function exportCsv() {
    const filtered = getFilteredTasks();
    if (filtered.length === 0) { showToast('No tasks to export', 'warning'); return; }
    const headers = ['ID','Name','Type','Priority','Status','Worker','Execution Time (ms)','Retry Count','Max Retries','Created','Started','Completed','Error','Result'];
    const rows = filtered.map(t => [
        t.id, t.name||'', t.type, t.priority, t.status, t.assignedWorker||'',
        t.executionTimeMs||'', t.retryCount||0, t.maxRetries||0,
        t.createdAt||'', t.startedAt||'', t.completedAt||'',
        '"'+(t.errorMessage||'').replace(/"/g, '""')+'"',
        '"'+(t.result||'').replace(/"/g, '""')+'"'
    ]);
    const csv = [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'tasks_' + new Date().toISOString().slice(0,10) + '.csv';
    a.click(); URL.revokeObjectURL(url);
    showToast('Exported ' + filtered.length + ' tasks to CSV', 'success');
}





// ================== Hash Ring Panel ==================
function renderHashRing(stats) {
    const el = $('hrInfo');
    const dist = stats.hashRingDistribution;
    if (!dist) { el.innerHTML = '<div class="empty-state">No data</div>'; return; }

    const total = Object.values(dist).reduce((a, b) => a + b, 0);
    const rows = Object.entries(dist).map(([worker, count]) => {
        const pct = total > 0 ? (count / total * 100).toFixed(1) : 0;
        return `<div class="hr-bar-row">
            <span class="hr-bar-label">${worker.replace('worker-', 'W')}</span>
            <div class="hr-bar-track"><div class="hr-bar-fill" style="width:${pct}%"></div></div>
            <span class="hr-bar-val">${count} (${pct}%)</span>
        </div>`;
    }).join('');

    let taskDistHtml = '';
    const taskDist = stats.workerTaskDistribution;
    if (taskDist) {
        const taskTotal = Object.values(taskDist).reduce((a, b) => a + b, 0);
        if (taskTotal > 0) {
            taskDistHtml = '<div style="margin-top:12px;margin-bottom:6px;color:var(--muted);font-size:11px;font-weight:600">Actual Task Distribution (' + taskTotal + ' attempts)</div>' +
                Object.entries(taskDist).map(function(entry) {
                    var w = entry[0], c = entry[1];
                    var pct = taskTotal > 0 ? (c / taskTotal * 100).toFixed(1) : 0;
                    return '<div class="hr-bar-row"><span class="hr-bar-label">' + w.replace('worker-', 'W') + '</span>'
                        + '<div class="hr-bar-track"><div class="hr-bar-fill" style="width:'+pct+'%;background:var(--success)"></div></div>'
                        + '<span class="hr-bar-val">'+c+' ('+pct+'%)</span></div>';
                }).join('');
        }
    }

    el.innerHTML = `<div style="margin-bottom:8px;color:var(--muted);font-size:11px">${stats.hashRingNodeCount} virtual nodes across ${Object.keys(dist).length} workers</div>` + rows + taskDistHtml;

}

// ================== DAG Dependency Display (Modal) ==================
async function loadDagInfo(taskId, container) {
    try {
        const [depsRes, deptsRes] = await Promise.all([
            fetch('/api/tasks/' + taskId + '/dependencies'),
            fetch('/api/tasks/' + taskId + '/dependents')
        ]);
        const deps = depsRes.ok ? await depsRes.json() : [];
        const depts = deptsRes.ok ? await deptsRes.json() : [];

        if (deps.length === 0 && depts.length === 0) {
            container.innerHTML = '<div style="font-size:12px;color:var(--muted)">No dependencies</div>';
            return;
        }

        let html = '';
        if (deps.length > 0) {
            html += '<div style="font-size:12px;margin-bottom:4px"><strong>Depends on:</strong></div><div class="dep-list">' +
                deps.map(d => `<span class="dep-tag upstream" onclick="openModal('${d}')" title="Click to view">${d}</span>`).join('') + '</div>';
        }
        if (depts.length > 0) {
            html += '<div style="font-size:12px;margin-top:8px;margin-bottom:4px"><strong>Dependents:</strong></div><div class="dep-list">' +
                depts.map(d => `<span class="dep-tag" onclick="openModal('${d}')" title="Click to view">${d}</span>`).join('') + '</div>';
        }
        container.innerHTML = html;
    } catch(e) {
        container.innerHTML = '<div style="font-size:12px;color:var(--muted)">Could not load dependencies</div>';
    }
}

// ==================== Workflow Canvas ====================
const canvasNodes = new Map();
const canvasEdges = [];
let dragState = null;
let edgeDragState = null;
let nodeCounter = 0;
const workflows = new Map();

function addToCanvas() {
    const rawName = $('taskName').value;
    if(rawName && !isValidName(rawName)){
        showToast('Task name can only contain letters, digits, and underscores', 'warning');
        return;
    }
    const payloadRaw = $('taskPayload').value.trim();
    if(!payloadRaw){
        showToast('Payload is required. Use the builder to create one.', 'warning');
        return;
    }
    try{
        JSON.parse(payloadRaw);
    }
    catch(e){
        showToast('Invalid JSON payload: ' + e.message, 'error');
        return;
    }
    nodeCounter++;
    const name = rawName || $('taskType').value.toLowerCase() + '_' + nodeCounter;
    const type = $('taskType').value;
    const priority = $('taskPriority').value;
    const payload = payloadRaw;
    const maxRetries = parseInt($('taskRetries').value) || 3;
    const tempId = 'n' + Date.now() + nodeCounter;
    const existing = canvasNodes.size;
    const col = existing % 3;
    const row = Math.floor(existing / 3);
    canvasNodes.set(tempId, { tempId, name, type, priority, payload, maxRetries, x: 40 + col * 200, y: 40 + row * 120, status: null, realId: null });
    renderCanvas();
    showToast('Added "' + name + '" to canvas', 'info');
    $('taskName').value = '';
    $('taskPayload').value = '';
    $('taskPriority').value = 'MEDIUM';
    $('taskRetries').value = '3';
    $('taskTimeout').value = '60';
}

// Get the 4 anchor points of a node element
function getNodeAnchors(node, el) {
    const w = el.offsetWidth, h = el.offsetHeight;
    return {
        top:    { x: node.x + w / 2, y: node.y },
        right:  { x: node.x + w,     y: node.y + h / 2 },
        bottom: { x: node.x + w / 2, y: node.y + h },
        left:   { x: node.x,         y: node.y + h / 2 }
    };
}

// Pick the pair of anchors (one from each node) that are closest
function closestAnchors(fromNode, fromEl, toNode, toEl) {
    const fa = getNodeAnchors(fromNode, fromEl);
    const ta = getNodeAnchors(toNode, toEl);
    let best = null, bestDist = Infinity;
    for (const fk of ['top','right','bottom','left']) {
        for (const tk of ['top','right','bottom','left']) {
            const dx = fa[fk].x - ta[tk].x, dy = fa[fk].y - ta[tk].y;
            const d = dx * dx + dy * dy;
            if (d < bestDist) { bestDist = d; best = { x1: fa[fk].x, y1: fa[fk].y, x2: ta[tk].x, y2: ta[tk].y }; }
        }
    }
    return best;
}

// Pick the anchor on a node closest to an arbitrary point
function closestAnchorToPoint(node, el, px, py) {
    const a = getNodeAnchors(node, el);
    let best = null, bestDist = Infinity;
    for (const k of ['top','right','bottom','left']) {
        const dx = a[k].x - px, dy = a[k].y - py;
        const d = dx * dx + dy * dy;
        if (d < bestDist) { bestDist = d; best = a[k]; }
    }
    return best;
}

function renderCanvas() {
    const canvas = $('wfCanvas');
    const empty = $('canvasEmpty');
    if (empty) empty.style.display = canvasNodes.size === 0 ? 'block' : 'none';
    canvas.querySelectorAll('.wf-node').forEach(n => n.remove());
    canvasNodes.forEach((node, tempId) => {
        const div = document.createElement('div');
        div.className = 'wf-node ' + (node.status ? ' node-' + node.status : '');
        div.id = 'wfn-' + tempId;
        div.style.left = node.x + 'px';
        div.style.top = node.y + 'px';
        div.innerHTML = '<button class="wf-node-del" onclick="event.stopPropagation();removeCanvasNode(\''+tempId+'\')">&times;</button>'
            + '<div class="wf-node-name">' + node.name + '</div>'
            + '<div class="wf-node-type">' + node.type + ' | ' + node.priority + '</div>'
            + (node.status ? '<div style="margin-top:4px"><span class="status-badge status-'+node.status+'">'+node.status+'</span></div>' : '')
            + (node.realId ? '<div style="font-size:10px;color:var(--info);font-family:monospace;margin-top:2px">'+node.realId+'</div>' : '')
            + '<div class="wf-connector conn-top" data-node="'+tempId+'"></div>'
            + '<div class="wf-connector conn-right" data-node="'+tempId+'"></div>'
            + '<div class="wf-connector conn-bottom" data-node="'+tempId+'"></div>'
            + '<div class="wf-connector conn-left" data-node="'+tempId+'"></div>';
        
        div.addEventListener('mousedown', function(e) {
            if (e.target.classList.contains('wf-node-del') || e.target.classList.contains('wf-connector')) return;
            const rect = canvas.getBoundingClientRect();
            dragState = { tempId, offX: e.clientX - node.x - rect.left, offY: e.clientY - node.y - rect.top, rect };
            div.style.cursor = 'grabbing';
            e.preventDefault();
        });
        
        // All 4 connector dots: start edge drag
        div.querySelectorAll('.wf-connector').forEach(function(dot) {
            dot.addEventListener('mousedown', function(e) {
                e.stopPropagation();
                e.preventDefault();
                edgeDragState = { from: tempId };
                canvas.querySelectorAll('.wf-node').forEach(n => {
                    if (n.id !== 'wfn-' + tempId) n.classList.add('drop-target');
                });
            });
        });
        //right click context menu
        div.addEventListener('contextmenu', function(e){
            e.preventDefault();
            e.stopPropagation();
            showCanvasCtxMenu(e.clientX, e.clientY, [
                {label: '\u274C Remove Task', cls: 'danger', action: function() {removeCanvasNode(tempId);}}
            ]);
        });
        canvas.appendChild(div);
    });
    
    renderArrows();
    let maxY = 300, maxX = 500;
    canvasNodes.forEach(n => {
        if (n.y + 120 > maxY) maxY = n.y + 120;
        if (n.x + 200 > maxX) maxX = n.x + 200;
    });
    canvas.style.minHeight = maxY + 'px';
    canvas.style.minWidth = maxX + 'px';
}

document.addEventListener('mousemove', function(e) {
    // Node drag
    if (dragState) {
        const node = canvasNodes.get(dragState.tempId);
        if (!node) return;
        node.x = Math.max(0, e.clientX - dragState.offX - dragState.rect.left);
        node.y = Math.max(0, e.clientY - dragState.offY - dragState.rect.top);
        const el = $('wfn-' + dragState.tempId);
        if (el) { el.style.left = node.x + 'px'; el.style.top = node.y + 'px'; }
        renderArrows();
    }
    
    // Edge drag: draw temp line from closest anchor to cursor
    if (edgeDragState) {
        const svg = $('wfSvg');
        var tempLine = svg.querySelector('#tempEdgeLine');
        if (!tempLine) {
            tempLine = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            tempLine.id = 'tempEdgeLine';
            tempLine.setAttribute('stroke', 'var(--success)');
            tempLine.setAttribute('stroke-width', '2');
            tempLine.setAttribute('stroke-dasharray', '6,3');
            tempLine.setAttribute('marker-end', 'url(#ah)');
            svg.appendChild(tempLine);
        }
        
        const fromNode = canvasNodes.get(edgeDragState.from);
        const fromEl = $('wfn-' + edgeDragState.from);
        if (fromNode && fromEl) {
            const canvas = $('wfCanvas');
            const cRect = canvas.getBoundingClientRect();
            const mx = e.clientX - cRect.left + canvas.parentElement.scrollLeft;
            const my = e.clientY - cRect.top + canvas.parentElement.scrollTop;
            const anchor = closestAnchorToPoint(fromNode, fromEl, mx, my);
            tempLine.setAttribute('x1', anchor.x);
            tempLine.setAttribute('y1', anchor.y);
            tempLine.setAttribute('x2', mx);
            tempLine.setAttribute('y2', my);
        }
    }
});

document.addEventListener('mouseup', function(e) {
    if (dragState) {
        const el = $('wfn-' + dragState.tempId);
        if (el) el.style.cursor = 'grab';
        dragState = null;
    }
    
    if (edgeDragState) {
        var tempLine = $('wfSvg').querySelector('#tempEdgeLine');
        if (tempLine) tempLine.remove();
        document.querySelectorAll('.wf-node.drop-target').forEach(n => n.classList.remove('drop-target'));
        
        const canvas = $('wfCanvas');
        const allNodes = canvas.querySelectorAll('.wf-node');
        let targetId = null;
        allNodes.forEach(n => {
            const r = n.getBoundingClientRect();
            if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) {
                const nid = n.id.replace('wfn-', '');
                if (nid !== edgeDragState.from) targetId = nid;
            }
        });
        
        if (targetId && !canvasEdges.some(function(ed) { return ed.from === edgeDragState.from && ed.to === targetId; })) {
            canvasEdges.push({ from: edgeDragState.from, to: targetId });
            renderCanvas();
        }
        edgeDragState = null;
    }
});

function renderArrows() {
    const svg = $('wfSvg');
    var tempLine = svg.querySelector('#tempEdgeLine');
    svg.innerHTML = '<defs><marker id="ah" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="var(--primary)"/></marker></defs>';
    if (tempLine) svg.appendChild(tempLine);
    canvasEdges.forEach(function(edge) {
        const from = canvasNodes.get(edge.from), to = canvasNodes.get(edge.to);
        if (!from || !to) return;
        const fe = $('wfn-' + edge.from), te = $('wfn-' + edge.to);
        if (!fe || !te) return;
        const pts = closestAnchors(from, fe, to, te);
        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', pts.x1); line.setAttribute('y1', pts.y1);
        line.setAttribute('x2', pts.x2); line.setAttribute('y2', pts.y2);
        line.setAttribute('stroke', 'var(--primary)'); line.setAttribute('stroke-width', '2');
        line.setAttribute('marker-end', 'url(#ah)');
        svg.appendChild(line);
    });
    const canvas = $('wfCanvas');
    svg.setAttribute('width', canvas.scrollWidth);
    svg.setAttribute('height', canvas.scrollHeight);
}

// Point-to-line-segment distance for edge hit detection
function distToSegment(px, py, x1, y1, x2, y2) {
    var dx = x2 - x1, dy = y2 - y1;
    var lenSq = dx * dx + dy * dy;
    if (lenSq === 0) return Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
    var t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
    var projX = x1 + t * dx, projY = y1 + t * dy;
    return Math.sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY));
}

// Find edge near a canvas-relative point
function findEdgeNear(cx, cy, threshold) {
    var best = null, bestDist = threshold;
    canvasEdges.forEach(function(edge) {
        var from = canvasNodes.get(edge.from), to = canvasNodes.get(edge.to);
        if (!from || !to) return;
        var fe = $('wfn-' + edge.from), te = $('wfn-' + edge.to);
        if (!fe || !te) return;
        var pts = closestAnchors(from, fe, to, te);
        var d = distToSegment(cx, cy, pts.x1, pts.y1, pts.x2, pts.y2);
        if (d < bestDist) { bestDist = d; best = edge; }
    });
    return best;
}

// Canvas-level right-click: detect edge proximity
$('wfCanvas').addEventListener('contextmenu', function(e) {
    // If right-click is on a node, let the node handler deal with it
    if (e.target.closest && e.target.closest('.wf-node')) return;
    var canvas = $('wfCanvas');
    var rect = canvas.getBoundingClientRect();
    var cx = e.clientX - rect.left + canvas.parentElement.scrollLeft;
    var cy = e.clientY - rect.top + canvas.parentElement.scrollTop;
    var edge = findEdgeNear(cx, cy, 12);
    if (edge) {
        e.preventDefault();
        e.stopPropagation();
        var fromNode = canvasNodes.get(edge.from), toNode = canvasNodes.get(edge.to);
        var fromName = fromNode ? fromNode.name : edge.from;
        var toName = toNode ? toNode.name : edge.to;
        showCanvasCtxMenu(e.clientX, e.clientY, [
            { label: '\u2702 Remove: ' + fromName + ' \u2192 ' + toName, cls: 'danger', action: function() { removeCanvasEdge(edge.from, edge.to); } }
        ]);
    }
});

function removeCanvasNode(tempId) {
    canvasNodes.delete(tempId);
    for (var i = canvasEdges.length - 1; i >= 0; i--) {
        if (canvasEdges[i].from === tempId || canvasEdges[i].to === tempId) canvasEdges.splice(i, 1);
    }
    renderCanvas();
}

function clearCanvas() {
    canvasNodes.clear();
    canvasEdges.length = 0;
    nodeCounter = 0;
    $('wfName').value = '';
    $('wfDesc').value = '';
    renderCanvas();
}

function removeCanvasEdge(fromId, toId){
    for(var i = canvasEdges.length - 1; i >= 0; i--){
        if(canvasEdges[i].from === fromId && canvasEdges[i].to === toId){
            canvasEdges.splice(i,1); break;
        }
    }
    renderArrows();
    
}

// ==================== Canvas Context Menu ====================
function showCanvasCtxMenu(x, y, items) {
    var menu = $('canvasCtxMenu');
    menu.innerHTML = items.map(function(item) {
        return '<div class="canvas-ctx-item'+(item.cls ? ' '+item.cls : '')+'" data-idx="'+items.indexOf(item)+'">'+item.label+'</div>';
    }).join('');
    menu.style.left = x + 'px';
    menu.style.top = y + 'px';
    menu.classList.add('active');
    // Bind click handlers
    menu.querySelectorAll('.canvas-ctx-item').forEach(function(el, idx) {
        el.addEventListener('mousedown', function(e) {
            e.stopPropagation();
            e.preventDefault();
            hideCanvasCtxMenu();
            var action = items[idx].action;
            setTimeout(function() {action(); }, 0);
        });
    });
}

function hideCanvasCtxMenu() {
    $('canvasCtxMenu').classList.remove('active');
}


document.addEventListener('mousedown', function(e) {
    var menu = $('canvasCtxMenu');
    if (menu && menu.classList.contains('active') && !menu.contains(e.target)) {
        hideCanvasCtxMenu();
    }
});


// ==================== Cycle Detection (DFS) ====================
function detectCycle() {
    var WHITE = 0, GRAY = 1, BLACK = 2;
    var color = {};
    canvasNodes.forEach(function(_, id) { color[id] = WHITE; });
    var adj = {};
    canvasNodes.forEach(function(_, id) { adj[id] = []; });
    canvasEdges.forEach(function(e) { adj[e.from].push(e.to); });
    var cyclePath = null;
    function dfs(u, path) {
        color[u] = GRAY;
        path.push(u);
        for (var vi = 0; vi < adj[u].length; vi++) {
            var v = adj[u][vi];
            if (color[v] === GRAY) {
                var start = path.indexOf(v);
                cyclePath = path.slice(start).map(function(id) { var n = canvasNodes.get(id); return n ? n.name : id; });
                cyclePath.push(canvasNodes.get(v) ? canvasNodes.get(v).name : v);
                return true;
            }
            if (color[v] === WHITE && dfs(v, path)) return true;
        }
        path.pop();
        color[u] = BLACK;
        return false;
    }
    for (var id of canvasNodes.keys()) {
        if (color[id] === WHITE && dfs(id, [])) return cyclePath;
    }
    return null;
}

// ==================== Submit Workflow ====================
async function submitWorkflow() {
    var name = $('wfName').value;
    var desc = $('wfDesc').value;
    if (!name) { showToast('Enter a workflow name', 'warning'); return; }
    if(!isValidName(name)){ showToast('Workflow name can only contain letters, digits, and underscores','warning'); return; }
    if (canvasNodes.size === 0) { showToast('Add tasks to canvas first', 'warning'); return; }
    var cycle = detectCycle();
    if (cycle) { showToast('Cycle detected: ' + cycle.join(' \u2192 '), 'error'); return; }
    var depMap = {};
    canvasEdges.forEach(function(e) { if (!depMap[e.to]) depMap[e.to] = []; depMap[e.to].push(e.from); });
    var taskNodes = [];
    canvasNodes.forEach(function(node, tempId) {
        taskNodes.push({ tempId: tempId, name: node.name, type: node.type, priority: node.priority, payload: node.payload, maxRetries: node.maxRetries, dependsOn: depMap[tempId] || [] });
    });
    try {
        var res = await fetch('/api/workflows', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({ name: name, description: desc, tasks: taskNodes }) });
        if (res.ok) {
            var wf = await res.json();
            showToast('Workflow "' + name + '" submitted with ' + wf.totalTasks + ' tasks', 'success');
            if (wf.tasks) {
                wf.tasks.forEach(function(t, idx) {
                    var tid = taskNodes[idx] ? taskNodes[idx].tempId : null;
                    if (tid && canvasNodes.has(tid)) { canvasNodes.get(tid).realId = t.id; canvasNodes.get(tid).status = t.status; }
                });
                renderCanvas();
            }
            refreshWorkflows();
        } else { showToast('Failed to submit workflow', 'error'); }
    } catch(e) { showToast('Error: ' + e.message, 'error'); }
}

// ==================== Live Canvas Updates ====================
function updateCanvasNodeStatus(taskId, status) {
    canvasNodes.forEach(function(node, tempId) {
        if (node.realId === taskId) {
            node.status = status;
            renderCanvas();
        }
    });
}

// ==================== Workflow List ====================
let wfCurrentPage = 1;
const wfPageSize = 10;

async function refreshWorkflows() {
    try {
        var res = await fetch('/api/workflows');
        var list = await res.json();
        workflows.clear();
        list.forEach(function(w) { workflows.set(w.id, w); });
        renderWorkflowList();
    } catch(e) {}
}

function getFilteredWorkflows() {
    var search = ($('wfFilterSearch').value || '').toLowerCase();
    var statusFilter = ($('wfFilterStatus').value || '');
    var result = [...workflows.values()].filter(function(w) {
        if (search && !(w.name || '').toLowerCase().includes(search) && !w.id.toLowerCase().includes(search)) return false;
        if (statusFilter && w.status != statusFilter) return false;
        return true;
    });
    return result;
}

function applyWfFilters() { wfCurrentPage = 1; renderWorkflowList(); }
function clearWfFilters() {
    $('wfFilterSearch').value = '';
    $('wfFilterStatus').value = '';
    wfCurrentPage = 1;
    renderWorkflowList();
}

function renderWorkflowList() {
    var tbody = $('wfTableBody');
    var filtered = getFilteredWorkflows();
    var totalPages = Math.max(1, Math.ceil(filtered.length / wfPageSize));
    if (wfCurrentPage > totalPages) wfCurrentPage = totalPages;
    var start = (wfCurrentPage - 1) * wfPageSize;
    var page = filtered.slice(start, start + wfPageSize);

    if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="empty-state">No workflows found.</td></tr>';
    } else {
        tbody.innerHTML = page.map(function(w) {
            return '<tr onclick="openWorkflowDetail(\''+w.id+'\')" style="cursor:pointer">'
                + '<td class="id">'+w.id+'</td>'
                + '<td>'+w.name+'</td>'
                + '<td><span class="status-badge wf-status-'+w.status+'">'+w.status+'</span></td>'
                + '<td style="font-size:12px;color:var(--muted)">'+w.completedTasks+'/'+w.totalTasks+'</td>'
                + '</tr>';
        }).join('');
    }
    
    // Pagination
    var pagDiv = $('wfPagination');
    if (totalPages <= 1) { 
        pagDiv.innerHTML = '<span style="font-size:12px;color:var(--muted)">Showing ' + filtered.length + ' workflows</span>'; 
        return; 
    }
    var html = '<button class="page-btn" '+(wfCurrentPage==1?'disabled':'')+' onclick="wfCurrentPage--;renderWorkflowList()">Prev</button>';
    for (var p = 1; p <= totalPages; p++) {
        html += '<button class="page-btn '+(p==wfCurrentPage?'active':'')+'" onclick="wfCurrentPage='+p+';renderWorkflowList()">'+p+'</button>';
    }
    html += '<button class="page-btn" '+(wfCurrentPage>=totalPages?'disabled':'')+' onclick="wfCurrentPage++;renderWorkflowList()">Next</button>';
    pagDiv.innerHTML = html;
}

// ==================== Workflow Detail Modal (DAG Graph) ====================
async function openWorkflowDetail(id) {
    var body = $('wfDetailBody');
    $('wfDetailTitle').textContent = 'Loading...';
    $('wfDetailPanel').classList.add('active');
    try {
        var res = await fetch('/api/workflows/' + id);
        if (!res.ok) { showToast('Workflow not found', 'error'); closeWorkflowDetail(); return; }
        var wf = await res.json();
        $('wfDetailTitle').textContent = wf.name;

        // Header info
        var headerHtml = '<div style="display:flex;gap:16px;align-items:center;margin-bottom:16px;flex-wrap:wrap">'
            + '<span class="status-badge wf-status-'+wf.status+'">'+wf.status+'</span>'
            + (wf.description ? '<span style="font-size:13px;color:var(--muted)">'+wf.description+'</span>' : '')
            + '<span style="font-size:12px;color:var(--muted)">Progress: '+wf.completedTasks+'/'+wf.totalTasks+' tasks'+(wf.failedTasks>0? ' ('+wf.failedTasks+' failed)' : '')+'</span>'
            + '</div>';

        var tasks = wf.tasks || [];
        if (tasks.length === 0) {
            body.innerHTML = headerHtml + '<div class="empty-state">No tasks in this workflow</div>';
            return;
        }

        // Build DAG layout using topological layers
        var taskMap = {};
        tasks.forEach(function(t) { taskMap[t.id] = t; });

        // Build adjacency from dependsOn
        var inEdges = {}; // taskId -> [upstream ids]
        var outEdges = {}; // taskId -> [downstream ids]
        tasks.forEach(function(t) {
            inEdges[t.id] = (t.dependsOn || []).filter(function(d) { return taskMap[d]; });
            outEdges[t.id] = (t.dependents || []).filter(function(d) { return taskMap[d]; });
        });

        // Assign layers via BFS (Kahn's)
        var inDeg = {};
        tasks.forEach(function(t) { inDeg[t.id] = inEdges[t.id].length; });
        var queue = [];
        tasks.forEach(function(t) { if (inDeg[t.id] === 0) queue.push(t.id); });
        var layers = [];
        var taskLayer = {};
        while (queue.length > 0) {
            var nextQueue = [];
            var layer = [];
            queue.forEach(function(tid) {
                layer.push(tid);
                taskLayer[tid] = layers.length;
                outEdges[tid].forEach(function(d) {
                    inDeg[d]--;
                    if (inDeg[d] === 0) nextQueue.push(d);
                });
            });
            layers.push(layer);
            queue = nextQueue;
        }
        // Any tasks not placed (cycle or no edges) go to last layer
        tasks.forEach(function(t) {
            if (taskLayer[t.id] === undefined) {
                if (layers.length === 0) layers.push([]);
                layers[layers.length - 1].push(t.id);
                taskLayer[t.id] = layers.length - 1;
            }
        });

        // Position nodes
        var nodeW = 150, nodeH = 60, gapX = 40, gapY = 80;
        var positions = {};
        var totalH = 0, totalW = 0;
        layers.forEach(function(layer, li) {
            var layerW = layer.length * (nodeW + gapX) - gapX;
            layer.forEach(function(tid, ci) {
                var x = ci * (nodeW + gapX) + 20;
                var y = li * (nodeH + gapY) + 20;
                positions[tid] = { x: x, y: y };
                if (x + nodeW + 20 > totalW) totalW = x + nodeW + 20;
                if (y + nodeH + 20 > totalH) totalH = y + nodeH + 20;
            });
        });

        // Render graph HTML
        var icons = { PENDING: '\u23F3', QUEUED: '\uD83D\uDCCB', RUNNING: '\u2699\uFE0F', COMPLETED: '\u2705', FAILED: '\u274C', RETRYING: '\uD83D\uDD04', CANCELLED: '\uD83D\uDEAB' };
        var nodesHtml = tasks.map(function(t) {
            var p = positions[t.id];
            return '<div class="wf-graph-node node-'+t.status+'" style="left:'+p.x+'px;top:'+p.y+'px;width:'+nodeW+'px;cursor:pointer" onclick="openModal(\''+t.id+'\')"">'
                + '<div class="gn-name">'+(icons[t.status]||'\u23F3')+' '+t.name+'</div>'
                + '<div class="gn-meta">'+t.type+' | '+t.priority+'</div>'
                + '<div style="margin-top:2px"><span class="status-badge status-'+t.status+'" style="font-size:9px;padding:1px 6px">'+t.status+'</span></div>'
                + '</div>';
        }).join('');

        // SVG arrows
        var arrowsSvg = '<svg xmlns="http://www.w3.org/2000/svg" width="'+totalW+'" height="'+totalH+'" style="position:absolute;inset:0;pointer-events:none;z-index:1">'
            + '<defs><marker id="ahd" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="var(--primary)"/></marker></defs>';
        tasks.forEach(function(t) {
            (t.dependents || []).forEach(function(depId) {
                if (!positions[depId]) return;
                var from = positions[t.id], to = positions[depId];
                var x1 = from.x + nodeW / 2, y1 = from.y + nodeH;
                var x2 = to.x + nodeW / 2, y2 = to.y;
                arrowsSvg += '<line x1="'+x1+'" y1="'+y1+'" x2="'+x2+'" y2="'+y2+'" stroke="var(--primary)" stroke-width="2" marker-end="url(#ahd)"/>';
            });
        });
        arrowsSvg += '</svg>';

        body.innerHTML = headerHtml
            + '<div class="wf-modal-graph"><div class="wf-modal-graph-inner" style="width:'+totalW+'px;height:'+totalH+'px;min-height:250px">'
            + arrowsSvg + nodesHtml
            + '</div></div>';
    } catch(e) { showToast('Failed to load workflow', 'error'); }
}

function closeWorkflowDetail() {
    $('wfDetailPanel').classList.remove('active');
}

// ==================== Failure Rate Sliders ====================
async function loadFailureRates() {
    try {
        var res = await fetch('/api/config/failure-rates');
        var rates = await res.json();
        renderFailureRates(rates);
    } catch(e) {}
}

function renderFailureRates(rates) {
    var el = $('frPanel');
    el.innerHTML = Object.entries(rates).map(function(entry) {
        var type = entry[0], rate = entry[1];
        return '<div class="fr-row">'
            + '<span class="fr-label">'+type.replace(/_/g, ' ')+'</span>'
            + '<input type="range" class="fr-slider" min="0" max="100" value="'+rate+'" oninput="this.nextElementSibling.textContent=this.value+\'%\'" onchange="updateFailureRate(\''+type+'\', this.value)">'
            + '<span class="fr-val">'+rate+'%</span></div>';
    }).join('');
}

async function updateFailureRate(type, value) {
    var rates = {};
    rates[type] = parseInt(value);
    try {
        await fetch('/api/config/failure-rates', { method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify(rates) });
        showToast(type.replace(/_/g, ' ') + ' failure rate: ' + value + '%', 'info');
    } catch(e) {}
}




// ================= DLQ Dashboard =========================
let dlqAllEntries = [];
let dlqPage = 1;
const dlqPageSize = 10;

async function openDlqModal() {
    $('dlqModal').classList.add('active');
    dlqPage = 1;
    try {
        const res = await fetch('/api/dlq');
        dlqAllEntries = await res.json();
        renderDlqEntries();
    } catch(e) { $('dlqList').innerHTML = '<div class="empty-state">Failed to load DLQ</div>'; }
}

function renderDlqEntries() {
    const el = $('dlqList');
    const search = ($('dlqSearch').value || '').toLowerCase();
    const hideReplayed = $('dlqHideReplayed').checked;
    let filtered = dlqAllEntries.filter(e => {
        if (hideReplayed && e.replayed) return false;
        if (search && !(e.taskName || '').toLowerCase().includes(search) && !(e.taskId || '').toLowerCase().includes(search)) return false;
        return true;
    });
    if (filtered.length === 0) {
        el.innerHTML = '<div class="empty-state">No DLQ entries match filters</div>';
        $('dlqPagination').innerHTML = '';
        return;
    }
    const totalPages = Math.ceil(filtered.length / dlqPageSize);
    if (dlqPage > totalPages) dlqPage = totalPages;
    const start = (dlqPage - 1) * dlqPageSize;
    const page = filtered.slice(start, start + dlqPageSize);
    el.innerHTML = page.map(e => `
        <div style="border:1px solid var(--border);border-radius:8px;padding:10px;margin-bottom:8px;background:var(--card)">
            <div style="display:flex;justify-content:space-between;align-items:center">
                <div>
                    <strong>${e.taskName || e.taskId}</strong>
                    <span class="badge badge-failed" style="margin-left:6px">${e.taskType || '?'}</span>
                    ${e.replayed ? `<span class="badge badge-completed" style="margin-left:4px">Replayed</span>` : ''}
                </div>
                ${!e.replayed ? '<button class="btn btn-sm btn-primary" onclick="replayDlq(\''+e.id+'\')">Replay</button>' : '<span style="font-size:11px;color:var(--muted)">'+e.replayedAt+'</span>'}
            </div>
            <div style="font-size:11px;color:var(--muted);margin-top:4px">
                ID: ${e.taskId || '-'} | Retries: ${e.retryCount}/${e.maxRetries} | Worker: ${e.workerName || '-'} | Failed: ${e.failedAt || '-'}
            </div>
            <div style="font-size:11px;color:var(--danger);margin-top:2px;word-break:break-all">${e.errorMessage || '-'}</div>
        </div>
    `).join('');
    
    // Pagination
    const pg = $('dlqPagination');
    if (totalPages === 1) { pg.innerHTML = '<div style="font-size:11px;color:var(--muted)">' + filtered.length + ' entries</div>'; return; }
    let pgHtml = '<div style="display:flex;gap:4px;align-items:center;justify-content:center;font-size:12px">';
    pgHtml += '<button class="btn btn-sm btn-outline" onclick="dlqPage=1;renderDlqEntries()" ' + (dlqPage===1?'disabled':'') + '>&laquo;</button>';
    pgHtml += '<button class="btn btn-sm btn-outline" onclick="dlqPage--;renderDlqEntries()" ' + (dlqPage===1?'disabled':'') + '>&lsaquo;</button>';
    pgHtml += '<span style="margin:0 8px">Page ' + dlqPage + ' / ' + totalPages + ' (' + filtered.length + ' entries)</span>';
    pgHtml += '<button class="btn btn-sm btn-outline" onclick="dlqPage++;renderDlqEntries()" ' + (dlqPage===totalPages?'disabled':'') + '>&rsaquo;</button>';
    pgHtml += '<button class="btn btn-sm btn-outline" onclick="dlqPage='+totalPages+';renderDlqEntries()" ' + (dlqPage===totalPages?'disabled':'') + '>&raquo;</button>';
    pgHtml += '</div>';
    pg.innerHTML = pgHtml;
}



async function replayDlq(dlqId) {
    try {
        const res = await fetch('/api/dlq/' + dlqId + '/replay', { method: 'POST' });
        if (res.ok) {
            showToast('DLQ entry replayed - new task created', 'info');
            openDlqModal(); // refresh
        } else {
            const data = await res.json();
            showToast(data.error || 'Replay failed', 'error');
        }
    } catch(e) { showToast('Replay failed', 'error'); }
}

// ================= Rate Limiter =========================
async function openRlModal() {
    $('rlModal').classList.add('active');
    try {
        const res = await fetch('/api/rate-limits');
        const data = await res.json();
        const el = $('rlPanel');
        const g = data.global || {};
        el.innerHTML = `
            <div style="margin-bottom:12px">
                <label style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
                    <input type="checkbox" id="rlEnabled" ${data.enabled?'checked':''} onchange="toggleRateLimit(this.checked)">
                    <strong>Rate Limiting Enabled</strong>
                </label>
                <div style="font-size:12px;color:var(--muted);margin-bottom:10px">
                    Total rejections: <strong>${data.totalRejections || 0}</strong>
                </div>
            </div>
            <div style="margin-bottom:12px">
                <strong style="font-size:13px">Global Bucket</strong>
                <div style="font-size:12px;margin-top:4px">Capacity: ${g.capacity} | Refill: ${g.refillPerSec}/sec | Available: ${g.availableTokens}</div>
                <div style="display:flex;gap:8px;margin-top:6px">
                    <label style="font-size:11px">Capacity<br><input type="number" id="rlGCap" value="${g.capacity}" min="1" max="100" style="width:60px"></label>
                    <label style="font-size:11px">Refill/sec<br><input type="number" id="rlGRefill" value="${g.refillPerSec}" min="0.1" max="50" step="0.5" style="width:60px"></label>
                    <button class="btn btn-sm btn-primary" onclick="updateGlobalRL()" style="align-self:flex-end">Apply</button>
                </div>
            </div>
            <strong style="font-size:13px">Per-Type Buckets</strong>
            ${Object.entries(data.perType||{}).map(([type, b]) => 
                '<div style="font-size:11px;margin-top:4px"><span style="display:inline-block;width:130px">'+type.replace(/_/g, ' ')+'</span> tokens: '+b.availableTokens+'/'+b.capacity+' ('+b.refillPerSec+'/sec)</div>'
            ).join('')}
        `;
    } catch(e) { $('rlPanel').innerHTML = '<div class="empty-state">Failed to load</div>'; }
}

async function toggleRateLimit(enabled) {
    try {
        await fetch('/api/rate-limits', { method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify({enabled}) });
        showToast('Rate limiter ' + (enabled ? 'enabled' : 'disabled'), 'info');
    } catch(e) {}
}

async function updateGlobalRL() {
    const cap = parseInt($('rlGCap').value);
    const refill = parseFloat($('rlGRefill').value);
    try {
        await fetch('/api/rate-limits', { method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify({globalCapacity: cap, globalRefillPerSec: refill}) });
        showToast('Global rate limit updated: ' + cap + ' tokens, ' + refill + '/sec', 'info');
        openRlModal();
    } catch(e) {}
}

// ================= Dynamic Placeholders =================
const typeExamples = {
    
    DATA_PROCESSING: { name: 'e.g. Sort employees by salary', payload: '{"input":"employees.csv", "operation":"sort", "columm":"salary","order":"desc"}' },
    
};

$('taskType').addEventListener('change', function() {
    const ex = typeExamples[this.value];
    if (ex) {
        $('taskName').placeholder = ex.name;
        $('taskPayload').placeholder = ex.payload;
    }
    
});

let drawerFiles = [];
let drawerActiveFile = null;
let drawerFilesLoaded = false;

function toggleDataDrawer(){
    const drawer = $('dataDrawer');
    const isOpen = drawer.classList.toggle('open');
    if(isOpen && !drawerFilesLoaded) loadDrawerFiles();
}

async function loadDrawerFiles() {
    $('drawerFileList').innerHTML = '<span style="padding:6px 12px; font-size:12px; color:var(--muted)">Loading files...</span>'
    try {
        drawerFiles = await fetchJson('/api/data/files');
        drawerFilesLoaded = true;
        renderDrawerFiles();
    } catch(e) {
        $('drawerFileList').innerHTML = '<span style="padding:6px 12px;font-size:12px;color:var(--danger)">Failed to load files</span>';
    }
}

function renderDrawerFiles() {
    if(!drawerFilesLoaded) return;
    const search = ($('drawerFileSearch').value || '').toLowerCase();
    const filtered = drawerFiles.filter(f => f.toLowerCase().includes(search));
    const el = $('drawerFileList');
    if (filtered.length === 0) {
        el.innerHTML = '<span style="padding:6px 12px;font-size:12px;color:var(--muted)">' + (drawerFiles.length === 0 ? 'No CSV files in data/' : 'No files matching search') + '</span>';
        return;
    }
    el.innerHTML = filtered.map(f => 
        '<button class="file-tab ' + (f === drawerActiveFile ? ' active' : '') + '" onclick="previewDrawerFile(\'' + f + '\')">' + f + '</button>'
    ).join('');
}

function filterDrawerFiles() { renderDrawerFiles(); }

async function previewDrawerFile(filename) {
    drawerActiveFile = filename;
    renderDrawerFiles();
    const container = $('drawerPreview');
    container.innerHTML = '<div class="drawer-empty">Loading...</div>';
    
    try {
        const res = await fetch('/api/data/preview/' + filename);

        if (!res.ok) { 
            container.innerHTML = '<div style="color:var(--danger);padding:12px">Failed to load: HTTP ' + res.status + '</div>'; 
            return; 
        }

        const data = await res.json();

        if (data.error) { 
            container.innerHTML = '<div style="color:var(--danger);padding:12px">' + data.error + '</div>'; 
            return; 
        }

        // FIX 1: Safely grab the headers array, not the first string element
        const headers = data.headers || [];

        // FIX 2: Removed .length from totalRows since it is already a number
        let html = '<div class="drawer-meta"><strong>' + data.filename + '</strong> \u2014 ' + data.totalRows + ' rows (showing first 5)</div>';
        
        html += '<div style="overflow-x:auto"><table class="task-table" style="font-size:11px"><thead><tr>';
        
        headers.forEach(h => html += '<th style="padding:4px 8px">' + h.trim() + '</th>');
        
        html += '</tr></thead><tbody>';
        
        (data.sampleRows || []).forEach(row => {
            html += '<tr>' + row.map(c => '<td style="padding:4px 8px">' + (c || '').trim() + '</td>').join('') + '</tr>';
        });
        
        html += '</tbody></table></div>';
        container.innerHTML = html;
        
    } catch(e) { 
        container.innerHTML = '<div style="color:var(--danger);padding:12px">Error: ' +  e.message + '</div>'; 
    }
}

// =============== JSON Payload Builder ===============
const payloadSchemas = {
    DATA_PROCESSING: [
        { key: 'input', label: 'Input File', type: 'file-select', placeholder: 'Search files...' },
        { key: 'operation', label: 'Operation', type: 'select', options: ['count', 'sort', 'filter', 'aggregate'] },
        { key: 'column', label: 'Column', type: 'column-select', placeholder: 'Search columns..'},
        { key: 'order', label: 'Sort Order', type: 'select', options: ['asc', 'desc'], showIf: 'sort' },
        { key: 'condition', label: 'Condition', type: 'text', placeholder: '>30 or =New York', showIf: 'filter' }
    ],
    MULTI_CSV_PROCESSING: [
        { key: 'files', label: 'Files', type: 'file-multi-select', placeholder: 'Search files...'},
        { key: 'operation', label: 'Operation', type: 'select', options: ['merge', 'compare', 'join'] },
        { key: 'joinColumn', label: 'Join Column', type: 'column-select', placeholder: 'Search columns...', showIf: 'join' }
    ]
};

let pbFilesList = [];
let pbAvailableFiles = [];
let pbAvailableColumns = [];

function openPayloadBuilder() {
    const taskType = $('taskType').value;
    $('pbTypeLabel').textContent = taskType.replace(/_/g, ' ');
    pbFilesList = [];
    pbAvailableColumns = [];
    
    fetchJson('/api/data/files').then(files => {
        pbAvailableFiles = files || [];
        renderBuilderFields(taskType);
        openPanelModal('payloadBuilderModal');
    }).catch(e => {
        pbAvailableFiles = [];
        renderBuilderFields(taskType);
        openPanelModal('payloadBuilderModal');
    });
}

function renderBuilderFields(taskType) {
    const schema = payloadSchemas[taskType] || [];
    const container = $('pbFields');
    container.innerHTML = '';

    schema.forEach(field => {
        const row = document.createElement('div');
        row.className = 'pb-field-row';
        row.dataset.key = field.key;
        if (field.showIf) row.dataset.showIf = field.showIf;

        let inputHtml = '';
        if (field.type === 'select') {
            inputHtml = '<select class="pb-input" data-key="' + field.key + '" oninput="updateBuilderPreview();toggleBuilderFields()">'
                + field.options.map(o => '<option value="' + o + '">' + o + '</option>').join('')
                + '</select>';
        } else if (field.type === 'file-select') {
            const uid = 'pbfs_'+field.key;
            inputHtml = '<div class = "pb-file-select-wrap" style="flex:1;position:relative">'
                + '<input type="text" class="pb-input pb-file-search" data-key="' + field.key + '"  data-uid="' + uid + '" '
                + 'placeholder="' + (field.placeholder || '') + '" autocomplete="off" ' 
                + ' oninput="filterFileDropdown(\'' + uid + '\', this.value); updateBuilderPreview()"'
                + ' onfocus="showFileDropdown(\'' + uid + '\', this.value)">'
                + '<div class="pb-file-dropdown" id="' + uid + '"></div>'
                + '</div>';
        }
        else if(field.type === 'column-select'){

            const uid = 'pbcs_' + field.key;
            inputHtml = '<div class = "pb-file-select-wrap" style="flex:1;position:relative">'
                + '<input type="text" class="pb-input pb-col-search" data-key="' + field.key + '"  data-uid="' + uid + '" '
                + 'placeholder="' + (field.placeholder || '') + '" autocomplete="off" ' 
                + ' oninput="filterColumnDropdown(\'' + uid + '\', this.value); updateBuilderPreview()"'
                + ' onfocus="showColumnDropdown(\'' + uid + '\', this.value)">'
                + '<div class="pb-file-dropdown" id="' + uid + '"></div>'
                + '</div>';

        }
        else if(field.type === 'file-multi-select') {
            const uid = 'pbfm_'+field.key;
            inputHtml = '<div class = "pb-file-select-wrap" style="flex:1;position:relative">'
                + '<input type="text" class="pb-file-search" data-key="' + field.key + '"  data-uid="' + uid + '" '
                + 'placeholder="' + (field.placeholder || '') + '" autocomplete="off" ' 
                + ' oninput="filterFileDropdown(\'' + uid + '\', this.value)"'
                + ' onfocus="showFileDropdown(\'' + uid + '\', this.value)"'
                + ' onkeydown="if(event.key===\'Enter\'){event.preventDefault(); addPbFileFromDropdown(\'' + uid + '\', this)}">'
                + '<div class="pb-file-dropdown" id="' + uid + '"></div>'
                + '</div>'
                + '<div class ="pb-files-list" id = "pbFileChips"></div>';
        }
        else {
            inputHtml = '<input type="text" class="pb-input" data-key="' + field.key + '" placeholder="' + (field.placeholder || '') + '" oninput="updateBuilderPreview()">';
        }

        row.innerHTML = '<label>' + field.label + '</label>' + inputHtml
            + (field.hint && field.type !== 'file-multi-select' ? '<span class="pb-hint">' + field.hint + '</span>' : '');
        container.appendChild(row);
    });

    toggleBuilderFields();
    updateBuilderPreview();
}

function toggleBuilderFields() {
    const opSelect = document.querySelector('.pb-input[data-key="operation"]');
    const opVal = opSelect ? opSelect.value : '';
    document.querySelectorAll('#pbFields .pb-field-row[data-show-if]').forEach(row => {
        row.style.display = row.dataset.showIf === opVal ? 'flex' : 'none';
    });
}

function addPbFile(input) {
    const val = input.value.trim();
    if (!val) return;
    pbFilesList.push(val);
    input.value = '';
    renderPbFileChips();
    updateBuilderPreview();
}

function removePbFile(idx) {
    pbFilesList.splice(idx, 1);
    renderPbFileChips();
    updateBuilderPreview();
    fetchColumnsForFiles(pbFilesList);
}

function renderPbFileChips() {
    const el = $('pbFileChips');
    if (!el) return;
    el.innerHTML = pbFilesList.map((f, i) =>
        '<span class="pb-file-chip">' + f + '<button onclick="removePbFile(' + i + ')">&times;</button></span>'
    ).join('');
}

function updateBuilderPreview() {
    const taskType = $('taskType').value;
    const obj = {};
    document.querySelectorAll('#pbFields .pb-input').forEach(el => {
        const key = el.dataset.key;
        const row = el.closest('.pb-field-row');
        if (row && row.style.display === 'none') return;
        if (el.value) obj[key] = el.value;
    });
    if (pbFilesList.length > 0) obj.files = pbFilesList;
    $('pbPreview').textContent = JSON.stringify(obj, null, 2);
}

function applyPayloadBuilder() {
    const preview = $('pbPreview').textContent;
    try {
        JSON.parse(preview); // validate
        $('taskPayload').value = preview;
        closePanelModal('payloadBuilderModal');
        showToast('Payload applied', 'info');
    } catch (e) {
        showToast('Invalid JSON: ' + e.message, 'error');
    }
}

function showFileDropdown(uid, query) {
    filterFileDropdown(uid, query || '') ;
    $(uid).classList.add('open');
}

function filterFileDropdown(uid, query) {
    const dropdown = $(uid);
    if (!dropdown) return;
    const q = query.toLowerCase().trim();
    const filtered = q ? pbAvailableFiles.filter(f => f.toLowerCase().includes(q)) : pbAvailableFiles;
    if (filtered.length === 0) {
        dropdown.innerHTML = '<div class="pb-dd-empty" >No files found</div>';
        
    }
    else{
        dropdown.innerHTML = filtered.map(f =>
            '<div class="pb-dd-item" onclick="selectFileDropdownItem(\'' + uid + '\', \'' + f + '\')">' + f + '</div>'
        ).join('');
    }
    dropdown.classList.add('open');
}

function selectFileDropdownItem(uid, filename) {
    const dd = $(uid);
    if (!dd) return;
    dd.classList.remove('open');
    const wrap = dd.closest('.pb-file-select-wrap');
    const input = wrap.querySelector('input');

    if(input.classList.contains('pb-input')){
        input.value = filename;
        updateBuilderPreview();
        fetchColumnsForFiles([filename]);
    }
    else{
        if(!pbFilesList.includes(filename)){
            pbFilesList.push(filename);
            renderPbFileChips();
            updateBuilderPreview();
            fetchColumnsForFiles(pbFilesList)
        }
        input.value = '';
    }
    
}

function addPbFileFromDropdown(uid, input) {
    const val = input.value.trim();
    if (!val) return;
    if(!pbFilesList.includes(val)){
        pbFilesList.push(val);
        renderPbFileChips();
        updateBuilderPreview();
    }
    input.value = '';
    $(uid).classList.remove('open');
}

document.addEventListener('click', function(e){
    if(!e.target.closest('.pb-file-select-wrap')){
        document.querySelectorAll('.pb-file-dropdown.open').forEach(dd => dd.classList.remove('open'));
    }
});

function fetchColumnsForFiles(filenames) {
    if (!filenames || filenames.length === 0) {
        pbAvailableColumns = [];
        return;
    }
    Promise.all(
        filenames.map(f => fetchJson('/api/data/preview/' + encodeURIComponent(f)).then(data => {
            const headers = data.headers ? (Array.isArray(data.headers[0]) ? data.headers[0] : data.headers) : [];
            return headers.map(h => h.trim());
        }).catch(() => []))
    ).then(allHeaders => {
        if (allHeaders.length === 1) {
            pbAvailableColumns = allHeaders[0];
        } else {
            // Intersection: only columns present in ALL files
            const first = new Set(allHeaders[0]);
            pbAvailableColumns = [...first].filter(col => allHeaders.every(h => h.includes(col)));
        }
        // Clear column inputs if selected column no longer valid
        document.querySelectorAll('.pb-col-search').forEach(inp => {
            if (inp.value && !pbAvailableColumns.includes(inp.value)) inp.value = '';
        });
        updateBuilderPreview();
    });
}

function showColumnDropdown(uid, query) {
    filterColumnDropdown(uid, query || '');
    $(uid).classList.add('open');
}

function filterColumnDropdown(uid, query) {
    const dd = $(uid);
    if (!dd) return;
    const q = query.toLowerCase().trim();
    const matched = q ? pbAvailableColumns.filter(c => c.toLowerCase().includes(q)) : pbAvailableColumns;
    if (pbAvailableColumns.length === 0) {
        dd.innerHTML = '<div class="pb-dd-empty">Select a file first</div>';
    } else if (matched.length === 0) {
        dd.innerHTML = '<div class="pb-dd-empty">No columns match</div>';
    } else {
        dd.innerHTML = matched.map(c =>
            '<div class="pb-dd-item" onmousedown="selectColumnDropdownItem(\'' + uid + '\',\'' + c + '\')">' + c + '</div>'
        ).join('');
    }
    dd.classList.add('open');
}

function selectColumnDropdownItem(uid, colName) {
    const dd = $(uid);
    if (!dd) return;
    dd.classList.remove('open');
    const wrap = dd.closest('.pb-file-select-wrap');
    const input = wrap.querySelector('input');
    input.value = colName;
    updateBuilderPreview();
}




// Drawer resize handle
(function() {
    const drawer = document.getElementById('dataDrawer');
    const handle = document.getElementById('drawerHandle');
    if (!handle) return;
    let startY, startH;
    handle.addEventListener('mousedown', function(e) {
        startY = e.clientY;
        startH = drawer.offsetHeight;
        document.addEventListener('mousemove', onDrag);
        document.addEventListener('mouseup', stopDrag);
        e.preventDefault();
    });
    function onDrag(e) {
        const newH = Math.min(window.innerHeight * 0.85, Math.max(120, startH + (startY - e.clientY)));
        drawer.style.maxHeight = newH + 'px';
    }
    function stopDrag() {
        document.removeEventListener('mousemove', onDrag);
        document.removeEventListener('mouseup', stopDrag);
    }
})();





// ================= Init =================
connect();