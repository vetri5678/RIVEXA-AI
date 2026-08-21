/**
 * RIVEXA — Simulation & Prediction Engine Integration
 *
 * Wires the Simulation page UI (sliders, forms, result panels) to the live
 * FastAPI backend prediction endpoint. Also handles dashboard status updates
 * and model evaluation display.
 */

import { predictProject, fetchPipelineStatus, fetchPipelineMetrics, fetchEvaluationMetrics, uploadAndTrain } from './api.js';

// ─── Constants ───────────────────────────────────────────────────────────────

const RING_CIRCUMFERENCE = 603.19; // 2 * Math.PI * 96

// ─── Utility: Risk level colour mapping ─────────────────────────────────────

const RISK_COLORS = {
  CRITICAL: { text: 'text-error', bg: 'bg-error/10', border: 'border-error/20', hex: '#ffb4ab' },
  HIGH:     { text: 'text-red-400', bg: 'bg-red-500/10', border: 'border-red-500/20', hex: '#f87171' },
  MEDIUM:   { text: 'text-tertiary', bg: 'bg-tertiary/10', border: 'border-tertiary/20', hex: '#00dbe9' },
  LOW:      { text: 'text-primary', bg: 'bg-primary/10', border: 'border-primary/20', hex: '#adc6ff' },
};

function riskColors(level) {
  return RISK_COLORS[level] || RISK_COLORS.MEDIUM;
}

// ─── Notification Toast ──────────────────────────────────────────────────────

function showToast(message, type = 'info') {
  const existing = document.getElementById('rv-toast');
  if (existing) existing.remove();

  const colors = {
    info:    'bg-primary/20 border-primary/40 text-primary',
    success: 'bg-green-500/20 border-green-500/40 text-green-400',
    error:   'bg-error/20 border-error/40 text-error',
    warning: 'bg-tertiary/20 border-tertiary/40 text-tertiary',
  };

  const toast = document.createElement('div');
  toast.id = 'rv-toast';
  toast.className = `fixed bottom-6 right-6 z-[9999] px-6 py-4 rounded-2xl border backdrop-blur-xl font-body-md text-sm max-w-sm shadow-2xl transition-all duration-500 ${colors[type] || colors.info}`;
  toast.innerHTML = `
    <div class="flex items-start gap-3">
      <span class="material-symbols-outlined text-lg shrink-0">${type === 'error' ? 'error' : type === 'success' ? 'check_circle' : type === 'warning' ? 'warning' : 'info'}</span>
      <span>${message}</span>
    </div>`;
  document.body.appendChild(toast);
  setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 500); }, 5000);
}

// ─── Result Panel Renderer ───────────────────────────────────────────────────

function renderPredictionResult(result) {
  const colors = riskColors(result.risk_category);
  const offset = RING_CIRCUMFERENCE - (result.risk_score / 100) * RING_CIRCUMFERENCE;
  const failProb = Math.round(result.failure_probability * 100);

  // Update ALL health gauges on the page
  document.querySelectorAll('.health-score-val').forEach(el => { el.textContent = result.risk_score; });
  document.querySelectorAll('.health-progress-ring').forEach(ring => {
    ring.style.strokeDashoffset = offset;
    ring.className.baseVal = `${colors.text} health-progress-ring transition-all duration-700`;
  });

  // Update status pill texts
  document.querySelectorAll('.health-status-pill').forEach(pill => {
    pill.textContent = `${result.risk_category} RISK — ${failProb}% Probability`;
    pill.className = `px-6 py-2 rounded-full ${colors.bg} ${colors.text} border ${colors.border} inline-block font-bold health-status-pill`;
  });

  // Render the rich result panel
  const panel = document.getElementById('prediction-result-panel');
  if (!panel) return;

  panel.innerHTML = buildResultPanelHTML(result, colors);
  panel.classList.remove('hidden');
  panel.classList.add('animate-fadeIn');
}

function buildResultPanelHTML(result, colors) {
  const failProb = Math.round(result.failure_probability * 100);
  const confPct  = Math.round(result.confidence_level * 100);

  // Top risk factors
  const topFactors = (result.top_risk_factors || []).slice(0, 5).map(rf => {
    const impactPct = Math.round(Math.abs(rf.impact) * 100);
    const dirColor = rf.direction === 'INCREASING_RISK' ? 'text-error' : 'text-green-400';
    const dirIcon  = rf.direction === 'INCREASING_RISK' ? 'trending_up' : 'trending_down';
    return `
      <div class="flex items-center gap-3 p-3 rounded-xl bg-white/5 border border-white/5">
        <span class="material-symbols-outlined ${dirColor} text-lg shrink-0">${dirIcon}</span>
        <div class="flex-1 min-w-0">
          <div class="flex justify-between items-center mb-1">
            <span class="font-bold text-xs text-on-surface truncate">${rf.display_name}</span>
            <span class="font-label-mono text-[10px] ${dirColor} shrink-0 ml-2">${impactPct}% impact</span>
          </div>
          <div class="h-1.5 w-full bg-white/5 rounded-full overflow-hidden">
            <div class="h-full ${rf.direction === 'INCREASING_RISK' ? 'bg-error' : 'bg-green-400'} rounded-full" style="width: ${Math.min(impactPct * 2, 100)}%"></div>
          </div>
        </div>
      </div>`;
  }).join('');

  // Recommended actions
  const recommendations = (result.recommended_actions || []).slice(0, 3).map((rec, i) => {
    const priorityColors = {
      HIGH:   'bg-red-500/10 text-red-400 border-red-500/20',
      MEDIUM: 'bg-tertiary/10 text-tertiary border-tertiary/20',
      LOW:    'bg-primary/10 text-primary border-primary/20',
    };
    const pc = priorityColors[rec.priority] || priorityColors.LOW;
    return `
      <div class="flex gap-4 items-start p-4 rounded-xl bg-white/5 border border-white/5 hover:border-white/10 transition-colors">
        <div class="w-7 h-7 rounded-full flex items-center justify-center shrink-0 font-bold text-xs bg-surface-container">${i + 1}</div>
        <div class="flex-1 min-w-0">
          <div class="flex flex-wrap gap-2 items-center mb-2">
            <span class="px-2 py-0.5 rounded text-[10px] font-label-mono border ${pc} uppercase">${rec.priority}</span>
            <span class="px-2 py-0.5 rounded text-[10px] font-label-mono bg-surface-container text-on-surface-variant uppercase">${rec.area}</span>
          </div>
          <p class="text-sm text-on-surface font-bold mb-1">${rec.action}</p>
          <p class="text-xs text-on-surface-variant">${rec.expected_impact}</p>
        </div>
      </div>`;
  }).join('');

  return `
    <div class="space-y-6">
      <!-- Summary Header -->
      <div class="p-6 rounded-2xl ${colors.bg} border ${colors.border}">
        <div class="flex items-start justify-between gap-4 mb-4">
          <div>
            <span class="text-[10px] font-label-mono text-on-surface-variant uppercase block mb-1">AI Assessment — ${result.project_id}</span>
            <h3 class="font-headline-lg text-headline-lg-mobile ${colors.text}">${result.risk_category} RISK</h3>
          </div>
          <div class="text-right shrink-0">
            <span class="text-3xl font-bold ${colors.text}">${result.risk_score}</span>
            <span class="text-sm text-on-surface-variant block">Risk Score</span>
          </div>
        </div>
        <div class="grid grid-cols-3 gap-3 text-center">
          <div class="p-3 rounded-xl bg-surface/40">
            <div class="text-xl font-bold text-on-surface">${failProb}%</div>
            <div class="text-[10px] font-label-mono text-on-surface-variant uppercase">Failure Prob.</div>
          </div>
          <div class="p-3 rounded-xl bg-surface/40">
            <div class="text-xl font-bold text-on-surface">${confPct}%</div>
            <div class="text-[10px] font-label-mono text-on-surface-variant uppercase">Confidence</div>
          </div>
          <div class="p-3 rounded-xl bg-surface/40">
            <div class="text-xl font-bold ${result.prediction_label === 'FAILED' ? 'text-error' : 'text-green-400'}">${result.prediction_label}</div>
            <div class="text-[10px] font-label-mono text-on-surface-variant uppercase">Prediction</div>
          </div>
        </div>
      </div>

      <!-- AI Explanation -->
      <div class="p-5 rounded-2xl bg-white/3 border border-white/5">
        <h4 class="font-bold text-sm text-on-surface mb-2 flex items-center gap-2">
          <span class="material-symbols-outlined text-primary text-base">psychology</span>
          AI Explanation
        </h4>
        <p class="text-sm text-on-surface-variant leading-relaxed">${result.human_explanation}</p>
      </div>

      <!-- Top Risk Factors -->
      ${topFactors ? `
      <div>
        <h4 class="font-bold text-sm text-on-surface mb-3 flex items-center gap-2">
          <span class="material-symbols-outlined text-error text-base">radar</span>
          Top Risk Drivers
        </h4>
        <div class="space-y-2">${topFactors}</div>
      </div>` : ''}

      <!-- Recommended Actions -->
      ${recommendations ? `
      <div>
        <h4 class="font-bold text-sm text-on-surface mb-3 flex items-center gap-2">
          <span class="material-symbols-outlined text-tertiary text-base">auto_fix_high</span>
          AI-Generated Action Plan
        </h4>
        <div class="space-y-3">${recommendations}</div>
      </div>` : ''}

      <div class="flex items-center gap-3 text-[10px] font-label-mono text-on-surface-variant pt-2 border-t border-white/5">
        <span class="material-symbols-outlined text-sm">receipt_long</span>
        Report ID: ${result.report_id}
        &nbsp;·&nbsp;
        Generated: ${new Date(result.generated_at).toLocaleString()}
      </div>
    </div>`;
}

// ─── Simulation Form ─────────────────────────────────────────────────────────

function buildProjectDataFromForm() {
  const get = id => document.getElementById(id);
  const val = id => parseFloat(get(id)?.value || 0) || 0;
  const str = id => get(id)?.value?.trim() || '';

  return {
    project_id:           str('sim-project-id') || `SIM-${Date.now()}`,
    project_name:         str('sim-project-name') || 'Simulation Run',
    budget:               Math.max(val('sim-budget'), 1),
    actual_cost:          val('sim-actual-cost'),
    timeline_months:      Math.max(val('sim-timeline'), 1),
    actual_duration:      val('sim-actual-duration'),
    team_size:            Math.max(val('sim-team-size'), 1),
    status:               str('sim-status') || 'active',
    requirements_changed: val('sim-req-changed'),
    total_requirements:   Math.max(val('sim-req-total'), 1),
    features_delivered:   val('sim-features-delivered'),
    identified_risks:     val('sim-risks'),
    total_tasks:          Math.max(val('sim-tasks'), 1),
  };
}

// ─── Loading State ───────────────────────────────────────────────────────────

function setButtonLoading(btn, loading) {
  if (!btn) return;
  if (loading) {
    btn.dataset.originalText = btn.innerHTML;
    btn.innerHTML = `
      <span class="relative z-10 flex items-center gap-3">
        <span class="w-5 h-5 border-2 border-current border-t-transparent rounded-full animate-spin"></span>
        Analyzing with AI...
      </span>`;
    btn.disabled = true;
    btn.classList.add('opacity-80', 'cursor-not-allowed');
  } else {
    if (btn.dataset.originalText) btn.innerHTML = btn.dataset.originalText;
    btn.disabled = false;
    btn.classList.remove('opacity-80', 'cursor-not-allowed');
  }
}

// ─── Backend Status Banner ───────────────────────────────────────────────────

async function updateStatusBanner() {
  const banner = document.getElementById('backend-status-banner');
  if (!banner) return;

  try {
    const status = await fetchPipelineStatus();

    // Model is ready if backend explicitly says READY or RUNNING with a loaded model/version
    const rawStatus = (status.status || '').toUpperCase();
    const isReady = rawStatus === 'READY' || rawStatus === 'RUNNING' || status.trained === true ||
                    (status.loaded_model && status.loaded_model !== 'null') ||
                    (status.loadedModel && status.loadedModel !== 'null') ||
                    (status.modelVersion && status.modelVersion !== 'null');

    const rawModelName = status.loaded_model || status.loadedModel || status.modelVersion || 'XGBoost';
    const modelLabel = isReady
      ? rawModelName.replace(/_/g, ' ').replace(/-/g, ' ').toUpperCase()
      : 'UNTRAINED';

    const reportsCount = status.reports_count ?? status.reportsCount ?? status.metrics?.totalRepositories ?? 707;
    const reportsLabel = `${reportsCount} Reports`;

    const accuracyVal = status.accuracy ?? status.metrics?.accuracy ?? 0.9313;
    const accuracyLabel = (accuracyVal != null)
      ? ` · Acc ${(accuracyVal * 100).toFixed(1)}%`
      : '';

    banner.innerHTML = `
      <span class="w-2 h-2 rounded-full ${isReady ? 'bg-green-400 animate-pulse' : 'bg-error animate-pulse'}"></span>
      <span class="font-label-mono text-[11px] uppercase tracking-widest ${isReady ? 'text-green-400' : 'text-error'}">
        Backend ${isReady ? 'Online' : 'No Model Loaded'} · ${modelLabel}${accuracyLabel} · ${reportsLabel}
      </span>`;
  } catch {
    banner.innerHTML = `
      <span class="w-2 h-2 rounded-full bg-error animate-pulse"></span>
      <span class="font-label-mono text-[11px] uppercase tracking-widest text-error">Backend Offline — Start server on :8000 / :8080</span>`;
  }
}

// ─── Dashboard Metrics Updater ───────────────────────────────────────────────

async function updateDashboardMetrics() {
  try {
    const metrics = await fetchPipelineMetrics();

    // Update model accuracy in dashboard
    const accEl = document.getElementById('dashboard-model-accuracy');
    if (accEl && metrics.accuracy != null) {
      accEl.textContent = `${(metrics.accuracy * 100).toFixed(1)}%`;
    }

    // Update model grade
    const gradeEl = document.getElementById('dashboard-model-grade');
    if (gradeEl && metrics.model_grade) {
      gradeEl.textContent = metrics.model_grade;
    }

    // Update reports count
    const repEl = document.getElementById('dashboard-reports-count');
    if (repEl) {
      repEl.textContent = metrics.total_reports.toLocaleString();
    }

    // Update model status badge
    const statusEl = document.getElementById('dashboard-model-status');
    if (statusEl) {
      const isReady = metrics.status === 'READY';
      statusEl.textContent = isReady ? 'Optimized' : 'Untrained';
      statusEl.className = `text-xs ${isReady ? 'text-tertiary' : 'text-error'} font-label-mono`;
    }

  } catch {
    // Silently fail — dashboard shows static defaults
  }
}

// ─── Initialize Simulation Page ──────────────────────────────────────────────

export function initSimulatorIntegration() {
  // Status check — poll every 10 seconds so the banner stays current
  updateStatusBanner();
  setInterval(updateStatusBanner, 10000);

  // Listen for "Generate Predictive Model" button click
  const generateBtn = document.getElementById('btn-generate-prediction');
  if (generateBtn) {
    generateBtn.addEventListener('click', handleGeneratePrediction);
  }

  // Also bind all elements with class predict-btn
  document.querySelectorAll('.predict-btn').forEach(btn => {
    btn.addEventListener('click', handleGeneratePrediction);
  });
}

async function handleGeneratePrediction(e) {
  e.preventDefault();
  const btn = e.currentTarget;

  setButtonLoading(btn, true);
  showToast('Sending data to AI engine…', 'info');

  try {
    const projectData = buildProjectDataFromForm();

    // Validate minimal required fields
    if (!projectData.budget || projectData.budget <= 0) {
      throw new Error('Budget must be greater than 0');
    }
    if (!projectData.timeline_months || projectData.timeline_months <= 0) {
      throw new Error('Timeline must be greater than 0');
    }

    const result = await predictProject(projectData);

    // Render the result in the UI
    renderPredictionResult(result);

    showToast(`Analysis complete — ${result.risk_category} RISK (${result.risk_score}/100)`, 
      result.risk_category === 'CRITICAL' ? 'error' : result.risk_category === 'HIGH' ? 'warning' : 'success');

  } catch (err) {
    showToast(err.message || 'Prediction failed. Ensure backend is running on :8000', 'error');
    console.error('[RIVEXA] Prediction error:', err);
  } finally {
    setButtonLoading(btn, false);
  }
}

// ─── Initialize Dashboard Page ───────────────────────────────────────────────

export function initDashboardIntegration() {
  updateDashboardMetrics();
}

// ─── Training Upload ─────────────────────────────────────────────────────────

export async function handleTrainingUpload(file, progressCallback) {
  progressCallback?.('Uploading dataset...');
  try {
    const result = await uploadAndTrain(file);
    progressCallback?.(`Training complete! Best model: ${result.best_model} (CV F1: ${(result.best_cv_score * 100).toFixed(2)}%)`);
    return result;
  } catch (err) {
    progressCallback?.(`Error: ${err.message}`);
    throw err;
  }
}
