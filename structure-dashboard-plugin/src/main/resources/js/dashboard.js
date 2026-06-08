/**
 * Structure Analytics Dashboard — dashboard.js
 * Requires: ECharts 5, Sortable.js (loaded via Velocity template)
 */
(function () {
  'use strict';

  const CFG = window.SD_CONFIG || { baseUrl: '', restBase: '/rest/structuredash/1.0', initialDashboardId: '' };
  const REST = CFG.restBase;

  // ── State ─────────────────────────────────────────────────────
  let state = {
    dashboards: [],       // DashboardLayoutDto[]
    currentDashboard: null,
    structures: [],       // StructureInfo[]
    editMode: false,
    charts: {},           // widgetId → ECharts instance
    modal: { type: null, editingWidget: null, cols: 4 }
  };

  // ── DOM refs ──────────────────────────────────────────────────
  const $ = id => document.getElementById(id);
  const grid         = $('sd-grid');
  const emptyState   = $('sd-empty');
  const editToolbar  = $('sd-edit-toolbar');
  const dashSelect   = $('sd-dashboard-select');
  const dashName     = $('sd-dashboard-name');
  const modalOverlay = $('sd-modal-overlay');
  const modal        = $('sd-modal');

  // ── API helpers ───────────────────────────────────────────────
  async function api(method, path, body) {
    const opts = {
      method,
      headers: { 'Content-Type': 'application/json', 'X-Atlassian-Token': 'no-check' },
      credentials: 'same-origin'
    };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(REST + path, opts);
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: res.statusText }));
      throw new Error(err.message || `HTTP ${res.status}`);
    }
    if (res.status === 204) return null;
    return res.json();
  }

  const GET    = path        => api('GET',    path);
  const POST   = (path, b)  => api('POST',   path, b);
  const PUT    = (path, b)  => api('PUT',    path, b);
  const DELETE = path        => api('DELETE', path);

  // ── Init ──────────────────────────────────────────────────────
  async function init() {
    await loadStructures();
    await loadDashboards();

    if (CFG.initialDashboardId) {
      await loadDashboard(CFG.initialDashboardId);
    } else if (state.dashboards.length > 0) {
      await loadDashboard(state.dashboards[0].id);
    } else {
      showEmpty();
    }

    bindEvents();
  }

  async function loadStructures() {
    try {
      state.structures = await GET('/structures');
      populateStructureSelect($('sd-widget-structure'), state.structures);
    } catch (e) {
      console.warn('Failed to load structures:', e);
    }
  }

  async function loadDashboards() {
    state.dashboards = await GET('/dashboards').catch(() => []);
    dashSelect.innerHTML = '<option value="">— выберите дашборд —</option>';
    state.dashboards.forEach(d => {
      const opt = document.createElement('option');
      opt.value = d.id;
      opt.textContent = d.name;
      dashSelect.appendChild(opt);
    });
  }

  async function loadDashboard(id) {
    try {
      state.currentDashboard = await GET(`/dashboards/${id}`);
      dashSelect.value = id;
      dashName.textContent = state.currentDashboard.name;
      renderDashboard();
    } catch (e) {
      showError('Failed to load dashboard: ' + e.message);
    }
  }

  // ── Render ────────────────────────────────────────────────────
  function renderDashboard() {
    destroyCharts();
    grid.innerHTML = '';
    const widgets = state.currentDashboard && state.currentDashboard.widgets || [];

    if (widgets.length === 0) { showEmpty(); return; }
    emptyState.classList.add('sd-hidden');
    grid.classList.remove('sd-hidden');

    widgets.forEach(w => grid.appendChild(buildWidgetCard(w)));

    // Загружаем данные для каждого виджета асинхронно
    widgets.forEach(w => loadWidgetData(w));

    // Drag & drop
    if (typeof Sortable !== 'undefined') {
      Sortable.create(grid, {
        handle: '.sd-widget-drag',
        animation: 200,
        ghostClass: 'sortable-chosen',
        onEnd: onDragEnd
      });
    }
  }

  function buildWidgetCard(widget) {
    const card = document.createElement('div');
    card.className = `sd-widget sd-col-${widget.cols || 4}`;
    card.dataset.widgetId = widget.id;
    card.dataset.widgetType = widget.type;

    card.innerHTML = `
      <div class="sd-widget-header">
        <span class="sd-widget-drag" title="Drag to reorder">⠿</span>
        <span class="sd-widget-title">${escHtml(widget.title || widget.type)}</span>
        <div class="sd-widget-actions">
          <span class="sd-widget-action" data-action="refresh" title="Refresh">↻</span>
          <span class="sd-widget-action" data-action="edit" title="Edit">✎</span>
          <span class="sd-widget-action" data-action="delete" title="Remove">✕</span>
        </div>
      </div>
      <div class="sd-resize-bar">
        ${[3,4,6,8,12].map(c =>
          `<button class="sd-resize-btn ${c === (widget.cols||4) ? 'active':''}"
                   data-cols="${c}">${c} col</button>`
        ).join('')}
      </div>
      <div class="sd-widget-body">
        <div class="sd-loading">
          <div class="sd-spinner"></div>
          <span>Loading…</span>
        </div>
      </div>`;

    // Bind actions
    card.querySelectorAll('.sd-widget-action').forEach(btn => {
      btn.addEventListener('click', e => {
        e.stopPropagation();
        handleWidgetAction(btn.dataset.action, widget, card);
      });
    });
    card.querySelectorAll('.sd-resize-btn').forEach(btn => {
      btn.addEventListener('click', () => resizeWidget(card, widget, parseInt(btn.dataset.cols)));
    });

    return card;
  }

  // ── Widget data loading ───────────────────────────────────────
  async function loadWidgetData(widget) {
    const card = grid.querySelector(`[data-widget-id="${widget.id}"]`);
    if (!card) return;
    const body = card.querySelector('.sd-widget-body');

    try {
      const cols = (widget.columnKeys || []).join(',') || 'progress';
      let data;

      switch (widget.type) {
        case 'kpi':
          data = await GET(`/structure/${widget.structureId}/aggregates?columns=${cols}`);
          renderKPI(body, widget, data);
          break;

        case 'epic-progress':
          data = await GET(`/structure/${widget.structureId}/epic-progress?columns=${cols}`);
          renderEpicProgress(body, widget, data);
          break;

        case 'aggregates-bar':
          data = await GET(`/structure/${widget.structureId}/aggregates?columns=${cols}`);
          renderAggregatesBar(body, widget, data);
          break;

        case 'donut':
          data = await GET(`/structure/${widget.structureId}/aggregates?columns=${cols}`);
          renderDonut(body, widget, data);
          break;

        case 'progress-ring':
          data = await GET(`/structure/${widget.structureId}/aggregates?columns=progress`);
          renderProgressRing(body, widget, data);
          break;

        case 'table':
          data = await GET(`/structure/${widget.structureId}/epic-progress?columns=${cols}`);
          renderTable(body, widget, data);
          break;

        default:
          body.innerHTML = `<div class="sd-error">Unknown widget type: ${widget.type}</div>`;
      }
    } catch (e) {
      body.innerHTML = `<div class="sd-error">⚠ ${escHtml(e.message)}</div>`;
    }
  }

  // ── Widget renderers ──────────────────────────────────────────

  function renderKPI(body, widget, data) {
    const colKey = (widget.columnKeys || ['progress'])[0];
    const value = data.sums && data.sums[colKey] != null
      ? data.sums[colKey]
      : data.overallProgress || 0;

    const formatted = colKey === 'progress'
      ? Math.round(value) + '%'
      : formatNumber(value);

    // Генерируем sparkline из фиктивных трендов (реальные данные можно добавить позже)
    const bars = generateSparkline(7, value);

    body.innerHTML = `
      <div style="padding-top:4px">
        <div class="sd-kpi-value">${formatted}</div>
        <div class="sd-kpi-label">${escHtml(colKey)}</div>
        <div class="sd-kpi-delta sd-delta-up">↑ vs prev. period</div>
        <div class="sd-sparkline">
          ${bars.map((h, i) =>
            `<div class="sd-spark-bar ${i === bars.length-1 ? 'hi':''}"
                  style="height:${h}%"></div>`
          ).join('')}
        </div>
      </div>`;
  }

  function renderProgressRing(body, widget, data) {
    const pct = Math.min(100, Math.round(data.overallProgress || 0));
    const r = 42, cx = 56, cy = 56;
    const circ = 2 * Math.PI * r;
    const dash = circ * pct / 100;
    const color = pct >= 80 ? '#36b37e' : pct >= 50 ? '#0052cc' : '#ffc400';

    body.innerHTML = `
      <div class="sd-ring-wrap">
        <div class="sd-ring-center">
          <svg width="112" height="112" viewBox="0 0 112 112">
            <circle cx="${cx}" cy="${cy}" r="${r}" fill="none"
                    stroke="#dfe1e6" stroke-width="10"/>
            <circle cx="${cx}" cy="${cy}" r="${r}" fill="none"
                    stroke="${color}" stroke-width="10"
                    stroke-dasharray="${dash.toFixed(1)} ${circ.toFixed(1)}"
                    stroke-dashoffset="${circ / 4}"
                    stroke-linecap="round"
                    transform="rotate(-90 ${cx} ${cy})"/>
          </svg>
          <div class="sd-ring-label">
            <span class="sd-ring-pct">${pct}%</span>
            <span class="sd-ring-sub">done</span>
          </div>
        </div>
      </div>
      <div style="text-align:center;font-size:11px;color:#6b778c;margin-top:4px">
        ${data.totalIssues} issues total
      </div>`;
  }

  function renderEpicProgress(body, widget, data) {
    if (!data || data.length === 0) {
      body.innerHTML = '<div style="padding:16px;color:#97a0af;font-size:12px">No epics found</div>';
      return;
    }

    const rows = data.slice(0, 8).map(epic => {
      const pct = Math.min(100, Math.round(epic.progress || 0));
      const fillClass = pct >= 100 ? 'done' : pct < 30 ? 'overdue' : '';
      const colVals = Object.entries(epic.columnValues || {})
        .map(([k, v]) => `<span>${k}: <b>${formatNumber(v)}</b></span>`)
        .join(' · ');

      return `
        <div class="sd-epic-row">
          <div class="sd-epic-name">
            <a href="${CFG.baseUrl}/browse/issue-${epic.issueId}"
               target="_blank" style="color:inherit;text-decoration:none">
              #${epic.issueId}
            </a>
          </div>
          <div class="sd-epic-meta">
            <span>${epic.childCount} issues</span>
            ${colVals ? '· ' + colVals : ''}
          </div>
          <div class="sd-progress-track">
            <div class="sd-progress-fill ${fillClass}" style="width:${pct}%"></div>
          </div>
          <div class="sd-epic-pct">${pct}%</div>
        </div>`;
    }).join('');

    body.innerHTML = rows;
  }

  function renderAggregatesBar(body, widget, data) {
    const sums = data.sums || {};
    const keys = Object.keys(sums);
    if (keys.length === 0) {
      body.innerHTML = '<div style="padding:16px;color:#97a0af;font-size:12px">No data</div>';
      return;
    }

    const chartId = `chart-${widget.id}`;
    body.innerHTML = `<div id="${chartId}" class="sd-chart-wrap" style="height:180px"></div>`;

    const chart = echarts.init(document.getElementById(chartId));
    state.charts[widget.id] = chart;

    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis' },
      grid: { left: 10, right: 10, top: 10, bottom: 30, containLabel: true },
      xAxis: {
        type: 'category',
        data: keys,
        axisLabel: { color: '#6b778c', fontSize: 10, rotate: keys.length > 4 ? 30 : 0 },
        axisLine: { lineStyle: { color: '#dfe1e6' } }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#6b778c', fontSize: 10 },
        splitLine: { lineStyle: { color: '#f4f5f7', type: 'dashed' } }
      },
      series: [{
        type: 'bar',
        data: keys.map(k => ({ value: Math.round(sums[k] * 100) / 100, name: k })),
        barMaxWidth: 40,
        itemStyle: {
          color: '#0052cc',
          borderRadius: [3, 3, 0, 0]
        },
        label: { show: true, position: 'top', fontSize: 10, color: '#6b778c',
                 formatter: p => formatNumber(p.value) }
      }]
    });

    window.addEventListener('resize', () => chart.resize());
  }

  function renderDonut(body, widget, data) {
    const chartId = `chart-${widget.id}`;
    body.innerHTML = `<div id="${chartId}" class="sd-chart-wrap" style="height:160px"></div>`;

    const chart = echarts.init(document.getElementById(chartId));
    state.charts[widget.id] = chart;

    const sums = data.sums || {};
    const pieData = Object.entries(sums).map(([k, v]) => ({ name: k, value: Math.round(v * 10) / 10 }));
    const palette = ['#0052cc','#36b37e','#ffc400','#ff5630','#6554c0','#00b8d9'];

    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', right: 0, top: 'middle',
                textStyle: { color: '#6b778c', fontSize: 10 } },
      series: [{
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        label: { show: false },
        emphasis: { label: { show: false } },
        data: pieData.map((d, i) => ({ ...d, itemStyle: { color: palette[i % palette.length] } }))
      }]
    });
  }

  function renderTable(body, widget, data) {
    if (!data || data.length === 0) {
      body.innerHTML = '<div style="padding:12px;color:#97a0af;font-size:12px">No data</div>';
      return;
    }

    const colKeys = widget.columnKeys || [];
    const headerCols = colKeys.map(k => `<th>${escHtml(k)}</th>`).join('');

    const rows = data.slice(0, 10).map(epic => {
      const cells = colKeys.map(k => {
        const v = epic.columnValues && epic.columnValues[k];
        return `<td class="sd-num">${v != null ? formatNumber(v) : '—'}</td>`;
      }).join('');
      return `<tr>
        <td class="sd-issue-key">#${epic.issueId}</td>
        <td>${Math.round(epic.progress || 0)}%</td>
        <td>${epic.childCount}</td>
        ${cells}
      </tr>`;
    }).join('');

    body.innerHTML = `
      <div style="overflow-x:auto">
        <table class="sd-table">
          <thead><tr>
            <th>Issue</th><th>Progress</th><th>Children</th>
            ${headerCols}
          </tr></thead>
          <tbody>${rows}</tbody>
        </table>
      </div>`;
  }

  // ── Layout helpers ────────────────────────────────────────────
  function resizeWidget(card, widget, newCols) {
    // Убираем старый класс колонок
    [3,4,6,8,12].forEach(c => card.classList.remove(`sd-col-${c}`));
    card.classList.add(`sd-col-${newCols}`);
    widget.cols = newCols;

    // Обновляем кнопки resize
    card.querySelectorAll('.sd-resize-btn').forEach(b => {
      b.classList.toggle('active', parseInt(b.dataset.cols) === newCols);
    });

    // Перерисовываем ECharts если есть
    const chart = state.charts[widget.id];
    if (chart) setTimeout(() => chart.resize(), 50);
  }

  function onDragEnd() {
    if (!state.currentDashboard) return;
    const cards = grid.querySelectorAll('.sd-widget');
    const newOrder = Array.from(cards).map(c => c.dataset.widgetId);
    state.currentDashboard.widgets.sort((a, b) =>
      newOrder.indexOf(a.id) - newOrder.indexOf(b.id));
  }

  async function saveLayout() {
    if (!state.currentDashboard) return;
    try {
      await PUT(`/dashboards/${state.currentDashboard.id}`, state.currentDashboard);
      showToast('Layout saved', 'success');
    } catch (e) {
      showToast('Save failed: ' + e.message, 'error');
    }
  }

  // ── Edit mode ─────────────────────────────────────────────────
  function setEditMode(on) {
    state.editMode = on;
    grid.classList.toggle('sd-edit-mode', on);
    editToolbar.classList.toggle('sd-hidden', !on);
    $('sd-btn-add-widget').style.display = on ? '' : 'none';
    $('sd-btn-edit').textContent = on ? '✕ Cancel' : '✎ Edit';
  }

  // ── Widget actions ────────────────────────────────────────────
  function handleWidgetAction(action, widget, card) {
    if (action === 'refresh') {
      const body = card.querySelector('.sd-widget-body');
      body.innerHTML = '<div class="sd-loading"><div class="sd-spinner"></div><span>Loading…</span></div>';
      destroyChart(widget.id);
      loadWidgetData(widget);
    } else if (action === 'edit') {
      openModal(widget);
    } else if (action === 'delete') {
      if (!confirm('Remove this widget?')) return;
      state.currentDashboard.widgets = state.currentDashboard.widgets.filter(w => w.id !== widget.id);
      destroyChart(widget.id);
      card.remove();
      if (state.currentDashboard.widgets.length === 0) showEmpty();
    }
  }

  // ── Modal ──────────────────────────────────────────────────────
  function openModal(editingWidget = null) {
    state.modal.editingWidget = editingWidget;
    state.modal.type = editingWidget ? editingWidget.type : null;
    state.modal.cols = editingWidget ? editingWidget.cols : 4;

    $('sd-modal-title').textContent = editingWidget ? 'Edit widget' : 'Add widget';
    $('sd-modal-save').textContent  = editingWidget ? 'Save' : 'Add widget';
    $('sd-widget-title').value      = editingWidget ? (editingWidget.title || '') : '';
    $('sd-widget-structure').value  = editingWidget ? editingWidget.structureId : '';
    $('sd-widget-columns').value    = editingWidget ? (editingWidget.columnKeys || []).join(', ') : '';

    // Select type card
    document.querySelectorAll('.sd-type-card').forEach(c => {
      c.classList.toggle('selected', c.dataset.type === state.modal.type);
    });

    // Select cols button
    document.querySelectorAll('.sd-col-btn').forEach(b => {
      b.classList.toggle('sd-col-btn-active', parseInt(b.dataset.cols) === state.modal.cols);
    });

    modalOverlay.classList.remove('sd-hidden');
  }

  function closeModal() { modalOverlay.classList.add('sd-hidden'); }

  function saveModal() {
    const type   = state.modal.type;
    const title  = $('sd-widget-title').value.trim() || type;
    const strId  = parseInt($('sd-widget-structure').value) || 0;
    const cols   = state.modal.cols;
    const colKeys = $('sd-widget-columns').value
      .split(',').map(s => s.trim()).filter(Boolean);

    if (!type)   { alert('Select a widget type'); return; }
    if (!strId)  { alert('Select a Structure'); return; }

    if (state.modal.editingWidget) {
      Object.assign(state.modal.editingWidget, { type, title, structureId: strId, cols, columnKeys: colKeys });
      renderDashboard();
    } else {
      const newWidget = {
        id: 'w-' + Date.now(),
        type, title, structureId: strId, cols,
        columnKeys: colKeys, order: (state.currentDashboard.widgets.length)
      };
      state.currentDashboard.widgets.push(newWidget);
      renderDashboard();
    }
    closeModal();
  }

  // ── Events ────────────────────────────────────────────────────
  function bindEvents() {
    dashSelect.addEventListener('change', e => { if (e.target.value) loadDashboard(e.target.value); });
    $('sd-btn-refresh').addEventListener('click', () => { if (state.currentDashboard) renderDashboard(); });
    $('sd-btn-edit').addEventListener('click', () => setEditMode(!state.editMode));
    $('sd-btn-cancel-edit').addEventListener('click', () => setEditMode(false));
    $('sd-btn-save-layout').addEventListener('click', saveLayout);
    $('sd-btn-add-widget').addEventListener('click', () => {
      if (!state.currentDashboard) return;
      openModal();
    });
    $('sd-empty-add').addEventListener('click', async () => {
      if (!state.currentDashboard) {
        const name = prompt('Dashboard name:', 'My Structure Dashboard');
        if (!name) return;
        state.currentDashboard = await POST('/dashboards', { name, widgets: [] });
        await loadDashboards();
        dashSelect.value = state.currentDashboard.id;
      }
      setEditMode(true);
      emptyState.classList.add('sd-hidden');
      grid.classList.remove('sd-hidden');
      openModal();
    });
    $('sd-btn-new-dashboard').addEventListener('click', async () => {
      const name = prompt('Dashboard name:', 'New Dashboard');
      if (!name) return;
      const d = await POST('/dashboards', { name, widgets: [] });
      await loadDashboards();
      await loadDashboard(d.id);
      setEditMode(true);
    });

    // Modal
    $('sd-modal-close').addEventListener('click', closeModal);
    $('sd-modal-cancel').addEventListener('click', closeModal);
    $('sd-modal-save').addEventListener('click', saveModal);
    modalOverlay.addEventListener('click', e => { if (e.target === modalOverlay) closeModal(); });

    // Type cards
    document.querySelectorAll('.sd-type-card').forEach(card => {
      card.addEventListener('click', () => {
        state.modal.type = card.dataset.type;
        document.querySelectorAll('.sd-type-card').forEach(c => c.classList.remove('selected'));
        card.classList.add('selected');
      });
    });

    // Cols picker
    document.querySelectorAll('.sd-col-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        state.modal.cols = parseInt(btn.dataset.cols);
        document.querySelectorAll('.sd-col-btn').forEach(b => b.classList.remove('sd-col-btn-active'));
        btn.classList.add('sd-col-btn-active');
      });
    });
  }

  // ── Utilities ──────────────────────────────────────────────────
  function escHtml(s) {
    return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }

  function formatNumber(v) {
    if (v == null) return '—';
    if (typeof v === 'string') return v;
    const n = parseFloat(v);
    if (isNaN(n)) return String(v);
    return n % 1 === 0 ? n.toLocaleString() : n.toFixed(1);
  }

  function generateSparkline(count, peakAt) {
    return Array.from({ length: count }, (_, i) => {
      const base = 30 + Math.random() * 40;
      return Math.round(i === count - 1 ? Math.min(95, peakAt) : base);
    });
  }

  function destroyChart(widgetId) {
    const chart = state.charts[widgetId];
    if (chart) { chart.dispose(); delete state.charts[widgetId]; }
  }

  function destroyCharts() {
    Object.keys(state.charts).forEach(destroyChart);
  }

  function populateStructureSelect(sel, structures) {
    sel.innerHTML = '<option value="">— select structure —</option>';
    (structures || []).forEach(s => {
      const opt = document.createElement('option');
      opt.value = s.id;
      opt.textContent = s.name;
      sel.appendChild(opt);
    });
  }

  function showEmpty() {
    grid.classList.add('sd-hidden');
    emptyState.classList.remove('sd-hidden');
  }

  function showToast(msg, type) {
    const t = document.createElement('div');
    t.style.cssText = `
      position:fixed;bottom:20px;right:20px;z-index:9999;
      padding:10px 16px;border-radius:4px;font-size:13px;font-weight:500;
      background:${type==='success'?'#e3fcef':'#ffebe6'};
      color:${type==='success'?'#006644':'#bf2600'};
      border:1px solid ${type==='success'?'#36b37e':'#ff5630'};
      box-shadow:0 2px 8px rgba(0,0,0,.15);
    `;
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 3000);
  }

  function showError(msg) {
    grid.innerHTML = `<div class="sd-error" style="grid-column:1/-1;margin:20px">⚠ ${escHtml(msg)}</div>`;
  }

  // ── Bootstrap ─────────────────────────────────────────────────
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
