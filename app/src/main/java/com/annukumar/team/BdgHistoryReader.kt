package com.annukumar.team

/** Shared read-only scripts used by the visible BDG page and the foreground reader WebView. */
internal object BdgHistoryReader {
const val NETWORK_HISTORY_HOOK_JS = """(function(){
  if (window.__annuNetworkHookInstalled) return;
  window.__annuNetworkHookInstalled = true;
  window.__annuNetworkRows = window.__annuNetworkRows || [];
  function collect(value) {
    function walk(v, depth) {
      if (!v || typeof v !== "object" || depth > 8) return;
      if (Array.isArray(v)) { v.forEach(function(x){ walk(x, depth + 1); }); return; }
      var period = v.period || v.issueNumber || v.issue || v.expect || v.drawNumber || v.round;
      var result = v.size || v.bigSmall || v.resultSize || v.openResult || v.result;
      if (period != null && result != null) {
        var p = String(period), z = String(result).toUpperCase();
        if (/^[0-9]{8,24}$/.test(p) && (z === "BIG" || z === "SMALL")) {
          if (!window.__annuNetworkRows.some(function(x){ return x.period === p; })) window.__annuNetworkRows.push({period:p,size:z});
        }
      }
      Object.keys(v).forEach(function(k){ walk(v[k], depth + 1); });
    }
    walk(value, 0);
    if (window.__annuNetworkRows.length > 100) window.__annuNetworkRows = window.__annuNetworkRows.slice(-100);
  }
  var fetch0 = window.fetch;
  if (fetch0) window.fetch = function(){
    return fetch0.apply(this, arguments).then(function(response){
      try { response.clone().json().then(collect).catch(function(){}); } catch(e) {}
      return response;
    });
  };
  var open0 = XMLHttpRequest.prototype.open;
  var send0 = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(method, url){ this.__annuUrl = String(url || ""); return open0.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function(){
    this.addEventListener("load", function(){
      try { if (this.responseType === "" || this.responseType === "text") collect(JSON.parse(this.responseText)); } catch(e) {}
    });
    return send0.apply(this, arguments);
  };
})()""";

const val HISTORY_READER_JS = """(function () {
  var found = [], seen = Object.create(null), roots = [];
  function visible(el) {
    if (!el || !el.getClientRects || !el.getClientRects().length) return false;
    var s = window.getComputedStyle(el);
    return s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0';
  }
  function textOf(el) {
    return (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  }
  var bodyText = (document.body && (document.body.innerText || document.body.textContent) || '').toLowerCase();
  var route = (location.href || '').toLowerCase();
  if (!/bingo|win[\s_-]*go/.test(route) && !/\bbingo\b|\bwin\s*go\b/.test(bodyText)) return JSON.stringify('WAIT_BINGO');

  var modes = [
    {name:'Win Go / Bingo 30 Seconds', re:/^(?:(?:win\s*go|bingo)\s*)?30\s*(?:seconds?|secs?|sec|s)$/i},
    {name:'Win Go / Bingo 1 Minute', re:/^(?:(?:win\s*go|bingo)\s*)?1\s*(?:minutes?|mins?|min|m)$/i},
    {name:'Win Go / Bingo 3 Minutes', re:/^(?:(?:win\s*go|bingo)\s*)?3\s*(?:minutes?|mins?|min|m)$/i},
    {name:'Win Go / Bingo 5 Minutes', re:/^(?:(?:win\s*go|bingo)\s*)?5\s*(?:minutes?|mins?|min|m)$/i}
  ];
  function modeFor(el) {
    var t = textOf(el).replace(/\s+/g, ' ').trim();
    if (t.length > 40) return null;
    return modes.find(function(m){ return m.re.test(t); }) || null;
  }
  function activeElement(el) {
    var cls = String(el.className && el.className.baseVal || el.className || '').toLowerCase();
    var parent = el.parentElement;
    var pcls = parent ? String(parent.className && parent.className.baseVal || parent.className || '').toLowerCase() : '';
    return el.getAttribute('aria-selected') === 'true' ||
      el.getAttribute('aria-pressed') === 'true' ||
      el.getAttribute('data-state') === 'active' ||
      /(^|[\s_-])(active|selected|current|is-active|tab-active)([\s_-]|$)/.test(cls) ||
      /(^|[\s_-])(active|selected|current|is-active|tab-active)([\s_-]|$)/.test(pcls);
  }
  var candidates = Array.from(document.querySelectorAll('[role="tab"],button,[aria-selected],[aria-pressed],a,div,span'));
  var selected = null, selectedEl = null;
  for (var i=0; i<candidates.length; i++) {
    var el = candidates[i];
    if (!visible(el)) continue;
    var mode = modeFor(el);
    if (mode && activeElement(el)) {
      selected = mode.name; selectedEl = el; break;
    }
  }
  if (!selected) return JSON.stringify('WAIT_MODE');

  function hasHistoryRows(root) {
    if (!root || !root.querySelectorAll) return false;
    var nodes = root.querySelectorAll('tr,[role="row"],article,li,[class*="record"],[class*="item"],[class*="row"]');
    for (var i=0; i<nodes.length; i++) {
      var el = nodes[i];
      if (!visible(el)) continue;
      var t = textOf(el);
      if (t.length <= 420 && /\b[0-9]{8,24}\b/.test(t) && /\b(BIG|SMALL)\b/i.test(t)) return true;
    }
    return false;
  }
  function findPanel(tab) {
    var controls = tab.getAttribute('aria-controls');
    if (controls) {
      var byId = document.getElementById(controls);
      if (byId && visible(byId)) return byId;
    }
    var rolePanel = tab.closest && tab.closest('[role="tabpanel"]');
    if (rolePanel && visible(rolePanel)) return rolePanel;

    var tabGroup = tab.closest && (tab.closest('[role="tablist"]') || tab.parentElement);
    for (var ancestor = tabGroup, depth = 0; ancestor && depth < 5; depth++, ancestor = ancestor.parentElement) {
      var children = Array.from(ancestor.children || []);
      for (var j=0; j<children.length; j++) {
        var child = children[j];
        if (child === tabGroup || child.contains(tabGroup) || !visible(child)) continue;
        if (hasHistoryRows(child)) return child;
      }
    }
    return null;
  }
  var scope = findPanel(selectedEl);
  if (!scope) return JSON.stringify('WAIT_PANEL');

  function addRow(el) {
    if (!visible(el)) return;
    var txt = textOf(el);
    if (!txt || txt.length > 420) return;
    var periods = txt.match(/\b[0-9]{8,24}\b/g) || [];
    var sizes = txt.match(/\b(BIG|SMALL)\b/ig) || [];
    if (periods.length !== 1 || sizes.length < 1) return;
    // Some BDG row layouts repeat the size label in nested badges; the final
    // visible label is the row's result badge. Do not discard a valid period.
    var period = periods[0], size = sizes[sizes.length - 1].toUpperCase();
    if (!seen[period]) {
      seen[period] = true;
      found.push({period:period, size:size});
    }
  }
  function scanRoot(root) {
    if (!root || roots.indexOf(root) >= 0 || !root.querySelectorAll) return;
    roots.push(root);
    root.querySelectorAll('tr,[role="row"],article,li,[class*="record"],[class*="item"],[class*="row"]').forEach(addRow);
    root.querySelectorAll('*').forEach(function(el) {
      if (el.shadowRoot) scanRoot(el.shadowRoot);
    });
    root.querySelectorAll('iframe').forEach(function(frame) {
      try { if (frame.contentDocument) scanRoot(frame.contentDocument); } catch (_) {}
    });
  }
  try { scanRoot(scope); } catch (_) {}
  if (found.length === 0 && Array.isArray(window.__annuNetworkRows)) {
    window.__annuNetworkRows.forEach(function(row) {
      if (row && row.period && (row.size === "BIG" || row.size === "SMALL") && !seen[row.period]) {
        seen[row.period] = true;
        found.push({period:String(row.period), size:row.size});
      }
    });
  }
  return JSON.stringify({mode:selected, rows:found.slice(0,10)});
})()"""
}
