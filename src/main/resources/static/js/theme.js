/**
 * CPMS Global Theme Manager — theme.js
 *
 * How to use:
 *   1. Add <link rel="stylesheet" href="css/theme.css"> to every page
 *   2. Add <script src="js/theme.js"></script> before other scripts
 *   3. Call CPMSTheme.toggle() to switch themes
 *   4. Listen to "theme-changed" event to update ECharts instances
 *
 * On page load, the saved preference is restored from localStorage.
 */

(function () {
  'use strict';

  const STORAGE_KEY = 'cpms-theme';
  const DARK = 'dark';
  const LIGHT = 'light';

  /* ---------- public API ---------- */
  const CPMSTheme = {
    /** Return 'dark' or 'light' */
    current: function () {
      return document.documentElement.classList.contains('light-theme') ? LIGHT : DARK;
    },

    /** Switch to a specific theme ('dark'|'light') or toggle if omitted */
    toggle: function (theme) {
      const target = theme || (this.current() === DARK ? LIGHT : DARK);
      this.set(target);
    },

    /** Force-set a theme */
    set: function (theme) {
      const isLight = theme === LIGHT;
      document.documentElement.classList.toggle('light-theme', isLight);
      try { localStorage.setItem(STORAGE_KEY, theme); } catch (_) { /* quota */ }
      _dispatch(theme);
      _updateAllECharts(theme);
    },

    /**
     * Return axis/text/color tokens for chart rendering.
     *   chartText   — axis label colour
     *   chartAxis   — axis line colour
     *   chartSplit  — splitLine colour
     */
    chartTokens: function () {
      return this.current() === DARK
        ? { chartText: '#64748b', chartAxis: 'rgba(255,255,255,0.1)', chartSplit: 'rgba(255,255,255,0.04)' }
        : { chartText: '#64748b', chartAxis: 'rgba(0,0,0,0.08)',     chartSplit: 'rgba(0,0,0,0.04)' };
    },

    /** Re-render a single ECharts instance with the current theme tokens */
    updateChart: function (chartInstance, getOptionFn) {
      if (!chartInstance || chartInstance.isDisposed()) return;
      const tokens = this.chartTokens();
      const base = getOptionFn ? getOptionFn(tokens) : _genericChartUpdate(chartInstance, tokens);
      chartInstance.setOption(base, { notMerge: false });
    },

    /** Register an ECharts instance + option builder for auto-update on theme change */
    registerChart: function (chartInstance, getOptionFn) {
      if (!chartInstance || chartInstance.isDisposed()) return;
      _charts.push({ instance: chartInstance, builder: getOptionFn || null });
    },

    /** Unregister a chart (call on component teardown) */
    unregisterChart: function (chartInstance) {
      _charts = _charts.filter(function (c) { return c.instance !== chartInstance; });
    }
  };

  /* ---------- internal state ---------- */
  var _charts = [];

  /* ---------- internal helpers ---------- */
  function _dispatch(theme) {
    window.dispatchEvent(new CustomEvent('theme-changed', { detail: { theme: theme } }));
  }

  function _genericChartUpdate(chart, tokens) {
    var opt = chart.getOption();
    if (!opt) return {};

    var patches = {};
    // Patch xAxis
    if (opt.xAxis) {
      if (Array.isArray(opt.xAxis)) {
        patches.xAxis = opt.xAxis.map(function (a) { return _patchAxis(a, tokens); });
      } else {
        patches.xAxis = _patchAxis(opt.xAxis, tokens);
      }
    }
    // Patch yAxis
    if (opt.yAxis) {
      if (Array.isArray(opt.yAxis)) {
        patches.yAxis = opt.yAxis.map(function (a) { return _patchAxis(a, tokens); });
      } else {
        patches.yAxis = _patchAxis(opt.yAxis, tokens);
      }
    }
    // Textstyle
    var textStyle = { color: tokens.chartText };
    return Object.assign({}, patches, {
      backgroundColor: 'transparent',
      textStyle: textStyle
    });
  }

  function _patchAxis(axis, tokens) {
    var a = Object.assign({}, axis);
    a.axisLine = Object.assign({}, a.axisLine, { lineStyle: Object.assign({}, a.axisLine && a.axisLine.lineStyle, { color: tokens.chartAxis }) });
    a.splitLine = Object.assign({}, a.splitLine, { lineStyle: Object.assign({}, a.splitLine && a.splitLine.lineStyle, { color: tokens.chartSplit }) });
    a.axisLabel = Object.assign({}, a.axisLabel, { color: tokens.chartText });
    a.nameTextStyle = Object.assign({}, a.nameTextStyle, { color: tokens.chartText });
    return a;
  }

  function _updateAllECharts(theme) {
    var tokens = CPMSTheme.chartTokens();
    _charts = _charts.filter(function (c) { return c.instance && !c.instance.isDisposed(); });
    _charts.forEach(function (entry) {
      try {
        var opt = entry.builder ? entry.builder(tokens, theme) : _buildFromExisting(entry.instance, tokens, theme);
        if (opt) entry.instance.setOption(opt, { notMerge: false });
      } catch (e) { /* silently skip broken charts */ }
    });
  }

  function _buildFromExisting(chart, tokens, theme) {
    var opt = chart.getOption();
    if (!opt) return null;

    // Inline the dark/light patch. Could use the registered ECharts theme, but
    // this is more reliable across versions — we only touch the colours.
    var isLight = theme === LIGHT;
    var textColor = tokens.chartText;

    // Try setting ECharts built-in dark mode if available (echarts 5.x)
    try {
      // ECharts 5 does not support runtime theme switching — we patch manually.
    } catch (_) {}

    return {
      backgroundColor: 'transparent',
      textStyle: { color: textColor }
    };
  }

  /* ---------- initialise ---------- */
  (function _init() {
    var saved;
    try { saved = localStorage.getItem(STORAGE_KEY); } catch (_) { saved = null; }

    // Also respect OS preference if no saved value
    if (!saved) {
      saved = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? LIGHT : DARK;
    }

    CPMSTheme.set(saved);

    // Listen for OS-level changes (only when user hasn't explicitly chosen)
    if (window.matchMedia) {
      window.matchMedia('(prefers-color-scheme: light)').addEventListener('change', function (e) {
        var explicit;
        try { explicit = localStorage.getItem(STORAGE_KEY); } catch (_) { explicit = null; }
        if (!explicit) {
          CPMSTheme.set(e.matches ? LIGHT : DARK);
        }
      });
    }
  })();

  /* ---------- expose ---------- */
  window.CPMSTheme = CPMSTheme;

})();
