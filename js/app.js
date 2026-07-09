// RiskVision AI Main Application Logic
// Extended version: adds auth, projects, predictions, training, notifications, admin pages

let activeThreeInstance = null;

// ─── Pagination State ──────────────────────────────────────────────────────
const paginationState = {
  projects: { page: 1, pageSize: 9, total: 0 },
  predictions: { page: 1, pageSize: 15, total: 0 },
  audit: { page: 1, pageSize: 20, total: 0 },
};

// ─── Page Router Configuration ─────────────────────────────────────────────
const routes = {
  '/': 'page-home',
  '/dashboard': 'page-dashboard',
  '/workflow': 'page-workflow',
  '/simulation': 'page-simulation',
  '/login': 'page-login',
  '/register': 'page-register',
  '/forgot-password': 'page-forgot-password',
  '/projects': 'page-projects',
  '/predictions': 'page-predictions',
  '/training': 'page-training',
  '/notifications': 'page-notifications',
  '/profile': 'page-profile',
  '/settings': 'page-settings',
  '/admin': 'page-admin',
  '/help': 'page-help',
  '/about': 'page-about',
};

// ─── Initialize Application ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  // Initialize SPA Router
  initRouter();

  // Initialize Global Aurora Shader Background
  if (typeof initShaderBackground === 'function') {
    initShaderBackground('global-shader-canvas');
  }

  // Initialize Card Interactivity (Glow & Hover)
  initCardInteractions();

  // Initialize Intersection Observer for Scroll Animations
  initScrollAnimations();

  // Initialize Simulator Event Listeners
  initSimulator();

  // Initialize Auth State
  refreshAuthState();

  // Bind nav-level logout button
  const logoutBtn = document.getElementById('btn-nav-logout');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', async (e) => {
      e.preventDefault();
      await handleLogout();
    });
  }

  // Bind form handlers
  bindFormHandlers();

  // Check backend health
  checkBackendHealth();
});

// ─── 1. Router Logic ──────────────────────────────────────────────────────
function initRouter() {
  function handleRoute() {
    let rawHash = window.location.hash || '#/';
    let route = rawHash.replace(/^#/, '');

    // Resolve route fallback to Home
    let pageId = routes[route] || 'page-home';

    // Redirect auth pages if already logged in
    if (isLoggedIn() && (pageId === 'page-login' || pageId === 'page-register' || pageId === 'page-forgot-password')) {
      window.location.hash = '#/dashboard';
      return;
    }

    // Redirect protected pages if not logged in
    const protectedPages = ['page-projects', 'page-predictions', 'page-training', 'page-notifications', 'page-profile', 'page-settings', 'page-admin'];
    if (!isLoggedIn() && protectedPages.includes(pageId)) {
      window.location.hash = '#/login';
      return;
    }

    // Redirect admin page if not admin/superadmin
    if (pageId === 'page-admin' && !isAdmin()) {
      window.location.hash = '#/dashboard';
      return;
    }

    // Update active nav links
    updateNavLinks(route);

    // Swap visible page sections
    document.querySelectorAll('.page-section').forEach(section => {
      if (section.id === pageId) {
        section.classList.add('active');
      } else {
        section.classList.remove('active');
      }
    });

    // Handle component lifecycle on page transition
    handlePageLifecycle(pageId);

    // Scroll to top on route change
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  window.addEventListener('hashchange', handleRoute);
  // Run on initial load
  handleRoute();
}

function updateNavLinks(activeRoute) {
  document.querySelectorAll('nav a[href^="#/"]').forEach(link => {
    let linkRoute = link.getAttribute('href').replace(/^#/, '');
    if (linkRoute === activeRoute || (activeRoute === '/' && linkRoute === '/')) {
      link.classList.add('text-primary', 'font-bold', 'border-b-2', 'border-primary', 'pb-1');
      link.classList.remove('text-on-surface-variant');
    } else {
      link.classList.remove('text-primary', 'font-bold', 'border-b-2', 'border-primary', 'pb-1');
      link.classList.add('text-on-surface-variant');
    }
  });
}

function handlePageLifecycle(pageId) {
  // Clean up existing Three.js scene to avoid memory leaks
  if (activeThreeInstance) {
    activeThreeInstance.destroy();
    activeThreeInstance = null;
  }

  if (pageId === 'page-home') {
    setTimeout(() => {
      if (typeof initThreeBrain === 'function') {
        activeThreeInstance = initThreeBrain('threejs-container-home');
      }
    }, 100);
  } else if (pageId === 'page-dashboard') {
    setTimeout(() => {
      document.querySelectorAll('.chart-bar').forEach(bar => {
        bar.style.animation = 'none';
        bar.offsetHeight;
        bar.style.animation = null;
      });
      loadDashboardMetrics();
    }, 100);
  } else if (pageId === 'page-projects') {
    paginationState.projects.page = 1;
    loadProjectsList();
  } else if (pageId === 'page-predictions') {
    paginationState.predictions.page = 1;
    loadPredictionsList();
  } else if (pageId === 'page-training') {
    loadActiveModelInfo();
    loadModelVersionsList();
  } else if (pageId === 'page-notifications') {
    loadNotificationsList();
  } else if (pageId === 'page-profile') {
    loadProfileData();
  } else if (pageId === 'page-admin') {
    paginationState.audit.page = 1;
    loadAuditLogs();
  }
}

// ─── 2. Auth State Management ─────────────────────────────────────────────
function isLoggedIn() {
  return !!localStorage.getItem('rv_access_token');
}

function getCurrentUser() {
  try {
    const raw = localStorage.getItem('rv_user');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function isAdmin() {
  const user = getCurrentUser();
  return user && (user.role === 'admin' || user.role === 'superadmin');
}

function refreshAuthState() {
  const loggedIn = isLoggedIn();
  const loginBtn = document.getElementById('btn-nav-login');
  const profileMenu = document.getElementById('user-profile-menu');

  if (loggedIn) {
    if (loginBtn) loginBtn.classList.add('hidden');
    if (profileMenu) profileMenu.classList.remove('hidden');

    const user = getCurrentUser();
    if (user) {
      const nameEl = document.getElementById('nav-user-fullname');
      if (nameEl) nameEl.textContent = user.full_name || user.username || 'Profile';

      // Show admin nav link if admin/superadmin
      document.querySelectorAll('.nav-admin').forEach(el => {
        if (user.role === 'admin' || user.role === 'superadmin') {
          el.classList.remove('hidden');
        } else {
          el.classList.add('hidden');
        }
      });
    }

    // Show auth links, hide guest links
    document.querySelectorAll('.nav-auth').forEach(el => el.classList.remove('hidden'));
    document.querySelectorAll('.nav-guest').forEach(el => el.classList.add('hidden'));

    // Poll for unread notifications badge
    refreshNotificationBadge();
  } else {
    if (loginBtn) loginBtn.classList.remove('hidden');
    if (profileMenu) profileMenu.classList.add('hidden');

    document.querySelectorAll('.nav-auth').forEach(el => el.classList.add('hidden'));
    document.querySelectorAll('.nav-admin').forEach(el => el.classList.add('hidden'));
    document.querySelectorAll('.nav-guest').forEach(el => el.classList.remove('hidden'));
  }
}

async function refreshNotificationBadge() {
  if (!isLoggedIn()) return;
  try {
    const { rvListNotifications } = await import('./api.js');
    const data = await rvListNotifications(false, 1, 1);
    const badge = document.getElementById('unread-notifications-count');
    if (badge) {
      if (data.total > 0) {
        badge.classList.remove('hidden');
      } else {
        badge.classList.add('hidden');
      }
    }
  } catch (err) {
    console.warn('[App] Notification badge refresh failed:', err);
  }
}

// ─── 3. Form Handlers ─────────────────────────────────────────────────────
function bindFormHandlers() {
  // Login Form
  const loginForm = document.getElementById('form-login');
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = document.getElementById('login-email').value.trim();
      const password = document.getElementById('login-password').value;
      const btn = loginForm.querySelector('button[type="submit"]');

      try {
        setButtonLoading(btn, true, 'Authenticating...');
        const { rvLogin, rvGetMe } = await import('./api.js');
        await rvLogin(email, password);
        const user = await rvGetMe();
        localStorage.setItem('rv_user', JSON.stringify(user));
        refreshAuthState();
        showToast('Welcome back, ' + (user.full_name || user.username) + '!', 'success');
        window.location.hash = '#/dashboard';
      } catch (err) {
        showToast('Login failed: ' + err.message, 'error');
      } finally {
        setButtonLoading(btn, false, 'Authenticate');
      }
    });
  }

  // Register Form
  const registerForm = document.getElementById('form-register');
  if (registerForm) {
    registerForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('reg-username').value.trim();
      const email = document.getElementById('reg-email').value.trim();
      const fullName = document.getElementById('reg-fullname').value.trim();
      const password = document.getElementById('reg-password').value;
      const btn = registerForm.querySelector('button[type="submit"]');

      try {
        setButtonLoading(btn, true, 'Creating Account...');
        const { rvRegister } = await import('./api.js');
        await rvRegister(email, username, password, fullName);
        showToast('Account created! Please sign in.', 'success');
        window.location.hash = '#/login';
      } catch (err) {
        showToast('Registration failed: ' + err.message, 'error');
      } finally {
        setButtonLoading(btn, false, 'Create Account');
      }
    });
  }

  // Forgot Password Form
  const forgotForm = document.getElementById('form-forgot');
  if (forgotForm) {
    forgotForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const email = document.getElementById('forgot-email').value.trim();
      const btn = forgotForm.querySelector('button[type="submit"]');

      try {
        setButtonLoading(btn, true, 'Sending...');
        const { rvRequestPasswordReset } = await import('./api.js');
        await rvRequestPasswordReset(email);
        showToast('Password reset link sent if account exists.', 'success');
      } catch (err) {
        showToast('Request failed: ' + err.message, 'error');
      } finally {
        setButtonLoading(btn, false, 'Send Reset Request');
      }
    });
  }

  // Create/Edit Project Form
  const projectForm = document.getElementById('form-project');
  if (projectForm) {
    projectForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      await handleProjectFormSubmit();
    });
  }

  // Retraining Upload Form
  const retrainForm = document.getElementById('form-retrain');
  if (retrainForm) {
    retrainForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      await handleRetrainingSubmit();
    });
  }

  // Change Password Form
  const changePasswordForm = document.getElementById('form-change-password');
  if (changePasswordForm) {
    changePasswordForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const currentPwd = document.getElementById('chg-curr-pwd').value;
      const newPwd = document.getElementById('chg-new-pwd').value;
      const btn = changePasswordForm.querySelector('button[type="submit"]');

      try {
        setButtonLoading(btn, true, 'Updating...');
        const { rvChangePassword } = await import('./api.js');
        await rvChangePassword(currentPwd, newPwd);
        showToast('Password updated successfully.', 'success');
        changePasswordForm.reset();
      } catch (err) {
        showToast('Password update failed: ' + err.message, 'error');
      } finally {
        setButtonLoading(btn, false, 'Update Password');
      }
    });
  }

  // Dataset file input label
  const fileInput = document.getElementById('dataset-file-input');
  if (fileInput) {
    fileInput.addEventListener('change', () => {
      const statusEl = document.getElementById('dataset-upload-status');
      if (statusEl && fileInput.files.length > 0) {
        statusEl.textContent = `Selected: ${fileInput.files[0].name} (${(fileInput.files[0].size / 1024).toFixed(1)} KB)`;
        statusEl.classList.add('text-primary');
      }
    });
  }
}

async function handleLogout() {
  try {
    const { rvLogout } = await import('./api.js');
    await rvLogout();
  } catch {
    localStorage.removeItem('rv_access_token');
    localStorage.removeItem('rv_refresh_token');
    localStorage.removeItem('rv_user');
  }
  refreshAuthState();
  showToast('Signed out successfully.', 'info');
  window.location.hash = '#/';
}

// ─── 4. Dashboard Metrics ──────────────────────────────────────────────────
async function loadDashboardMetrics() {
  if (!isLoggedIn()) return;
  try {
    const { rvGetDashboard } = await import('./api.js');
    const data = await rvGetDashboard();

    const setEl = (id, val) => {
      const el = document.getElementById(id);
      if (el) el.textContent = val ?? '—';
    };

    if (data.summary) {
      setEl('dash-total-projects', data.summary.total_projects ?? 0);
      setEl('dash-active-projects', data.summary.active_projects ?? 0);
      setEl('dash-predictions-today', data.summary.predictions_today ?? 0);
      setEl('dash-model-accuracy', data.summary.model_accuracy ? (data.summary.model_accuracy * 100).toFixed(1) + '%' : '—');
      setEl('dash-high-risk-count', data.summary.high_risk_count ?? 0);
      setEl('dash-critical-risk-count', data.summary.critical_risk_count ?? 0);
    }
  } catch (err) {
    console.warn('[App] Dashboard metrics load failed:', err);
  }
}

// ─── 5. Projects CRUD ──────────────────────────────────────────────────────
window.loadProjectsList = async function () {
  if (!isLoggedIn()) return;
  const grid = document.getElementById('projects-grid');
  if (!grid) return;

  grid.innerHTML = '<div class="col-span-3 text-center py-16 text-on-surface-variant">Loading projects...</div>';

  try {
    const { rvListProjects } = await import('./api.js');
    const search = document.getElementById('prj-search')?.value?.trim() || '';
    const status = document.getElementById('prj-filter-status')?.value || '';
    const riskLevel = document.getElementById('prj-filter-risk')?.value || '';
    const { page, pageSize } = paginationState.projects;

    const data = await rvListProjects({ page, page_size: pageSize, search: search || undefined, status: status || undefined, risk_level: riskLevel || undefined });

    paginationState.projects.total = data.total || 0;
    updatePaginationInfo('projects', data.total || 0);

    if (!data.items || data.items.length === 0) {
      grid.innerHTML = '<div class="col-span-3 text-center py-16 text-on-surface-variant">No projects found. Create your first project.</div>';
      return;
    }

    grid.innerHTML = data.items.map(p => renderProjectCard(p)).join('');

  } catch (err) {
    grid.innerHTML = `<div class="col-span-3 text-center py-16 text-error">Failed to load projects: ${err.message}</div>`;
  }
};

function renderProjectCard(p) {
  const riskColors = {
    CRITICAL: 'bg-error/10 text-error border-error/20',
    HIGH: 'bg-orange-500/10 text-orange-400 border-orange-400/20',
    MEDIUM: 'bg-tertiary/10 text-tertiary border-tertiary/20',
    LOW: 'bg-green-500/10 text-green-400 border-green-400/20',
  };
  const riskClass = riskColors[p.latest_risk_level] || 'bg-white/5 text-on-surface-variant border-white/10';
  const riskLabel = p.latest_risk_level || 'Not Assessed';
  const budget = p.budget ? '$' + Number(p.budget).toLocaleString() : '—';
  const dateStr = p.created_at ? new Date(p.created_at).toLocaleDateString() : '—';

  return `
    <div class="glass-card rounded-3xl p-6 space-y-4 hover:border-primary/30 transition-all duration-300 group">
      <div class="flex items-start justify-between">
        <div class="flex-1 min-w-0">
          <h3 class="font-bold text-on-surface truncate group-hover:text-primary transition-colors">${escapeHtml(p.name)}</h3>
          <p class="text-xs text-on-surface-variant font-label-mono mt-1">${escapeHtml(p.project_id || p.id?.substring(0, 8) || '')}</p>
        </div>
        <span class="px-2 py-1 rounded-lg text-[9px] font-bold uppercase border ${riskClass} ml-2 shrink-0">${riskLabel}</span>
      </div>

      ${p.description ? `<p class="text-xs text-on-surface-variant line-clamp-2">${escapeHtml(p.description)}</p>` : ''}

      <div class="grid grid-cols-2 gap-3 text-xs border-t border-white/5 pt-4">
        <div><span class="text-on-surface-variant">Budget</span><p class="font-bold mt-0.5">${budget}</p></div>
        <div><span class="text-on-surface-variant">Team Size</span><p class="font-bold mt-0.5">${p.team_size ?? '—'}</p></div>
        <div><span class="text-on-surface-variant">Status</span><p class="font-bold capitalize mt-0.5">${p.status ?? '—'}</p></div>
        <div><span class="text-on-surface-variant">Created</span><p class="font-bold mt-0.5">${dateStr}</p></div>
      </div>

      <div class="flex gap-2 pt-2">
        <button onclick="openEditProjectModal('${p.id}')" class="flex-1 text-xs py-2 rounded-xl border border-white/10 hover:bg-white/5 font-bold text-on-surface-variant hover:text-primary transition-colors flex items-center justify-center gap-1">
          <span class="material-symbols-outlined text-sm">edit</span> Edit
        </button>
        <button onclick="triggerProjectPredict('${p.id}')" class="flex-1 text-xs py-2 rounded-xl bg-primary/10 border border-primary/20 font-bold text-primary hover:bg-primary/20 transition-colors flex items-center justify-center gap-1">
          <span class="material-symbols-outlined text-sm">psychology</span> Predict
        </button>
        <button onclick="confirmDeleteProject('${p.id}', '${escapeHtml(p.name)}')" class="text-xs py-2 px-3 rounded-xl border border-white/10 hover:bg-error/10 hover:border-error/30 hover:text-error font-bold text-on-surface-variant transition-colors">
          <span class="material-symbols-outlined text-sm">delete</span>
        </button>
      </div>
    </div>`;
}

window.openCreateProjectModal = function () {
  document.getElementById('modal-project-title').textContent = 'Create New Project';
  document.getElementById('project-modal-edit-id').value = '';
  document.getElementById('form-project').reset();
  document.getElementById('modal-project').classList.remove('hidden');
};

window.openEditProjectModal = async function (projectId) {
  try {
    const { rvGetProject } = await import('./api.js');
    const p = await rvGetProject(projectId);

    document.getElementById('modal-project-title').textContent = 'Edit Project';
    document.getElementById('project-modal-edit-id').value = p.id;
    document.getElementById('proj-id').value = p.project_id || '';
    document.getElementById('proj-name').value = p.name || '';
    document.getElementById('proj-desc').value = p.description || '';
    document.getElementById('proj-budget').value = p.budget || 0;
    document.getElementById('proj-cost').value = p.actual_cost || 0;
    document.getElementById('proj-timeline').value = p.planned_timeline_months || 12;
    document.getElementById('proj-duration').value = p.actual_duration_months || 0;
    document.getElementById('proj-teamsize').value = p.team_size || 5;
    document.getElementById('proj-status').value = p.status || 'active';
    document.getElementById('proj-req-changed').value = p.requirements_changes || 0;
    document.getElementById('proj-req-total').value = p.total_requirements || 20;
    document.getElementById('proj-features-del').value = p.features_delivered || 0;
    document.getElementById('proj-risks').value = p.identified_risks || 0;
    document.getElementById('proj-tasks').value = p.total_tasks || 50;

    document.getElementById('modal-project').classList.remove('hidden');
  } catch (err) {
    showToast('Failed to load project: ' + err.message, 'error');
  }
};

window.closeCreateProjectModal = function () {
  document.getElementById('modal-project').classList.add('hidden');
};

async function handleProjectFormSubmit() {
  const editId = document.getElementById('project-modal-edit-id').value;
  const btn = document.querySelector('#form-project button[type="submit"]');

  const payload = {
    project_id: document.getElementById('proj-id').value.trim(),
    name: document.getElementById('proj-name').value.trim(),
    description: document.getElementById('proj-desc').value.trim() || null,
    budget: parseFloat(document.getElementById('proj-budget').value) || 0,
    actual_cost: parseFloat(document.getElementById('proj-cost').value) || 0,
    planned_timeline_months: parseInt(document.getElementById('proj-timeline').value) || 12,
    actual_duration_months: parseInt(document.getElementById('proj-duration').value) || 0,
    team_size: parseInt(document.getElementById('proj-teamsize').value) || 5,
    status: document.getElementById('proj-status').value,
    requirements_changes: parseInt(document.getElementById('proj-req-changed').value) || 0,
    total_requirements: parseInt(document.getElementById('proj-req-total').value) || 20,
    features_delivered: parseInt(document.getElementById('proj-features-del').value) || 0,
    identified_risks: parseInt(document.getElementById('proj-risks').value) || 0,
    total_tasks: parseInt(document.getElementById('proj-tasks').value) || 50,
  };

  try {
    setButtonLoading(btn, true, editId ? 'Saving...' : 'Creating...');
    const { rvCreateProject, rvUpdateProject } = await import('./api.js');
    if (editId) {
      await rvUpdateProject(editId, payload);
      showToast('Project updated successfully.', 'success');
    } else {
      await rvCreateProject(payload);
      showToast('Project created successfully.', 'success');
    }
    closeCreateProjectModal();
    loadProjectsList();
  } catch (err) {
    showToast('Failed to save project: ' + err.message, 'error');
  } finally {
    setButtonLoading(btn, false, editId ? 'Save Project' : 'Create Project');
  }
}

window.confirmDeleteProject = function (projectId, name) {
  if (confirm(`Delete project "${name}"? This cannot be undone.`)) {
    deleteProject(projectId);
  }
};

async function deleteProject(projectId) {
  try {
    const { rvDeleteProject } = await import('./api.js');
    await rvDeleteProject(projectId);
    showToast('Project deleted.', 'success');
    loadProjectsList();
  } catch (err) {
    showToast('Failed to delete project: ' + err.message, 'error');
  }
}

window.triggerProjectPredict = function (projectId) {
  window.location.hash = '#/simulation';
  showToast('Open the Simulation tab and run a prediction for your project.', 'info');
};

window.changeProjectsPage = function (direction) {
  const state = paginationState.projects;
  const newPage = state.page + direction;
  const totalPages = Math.ceil(state.total / state.pageSize);
  if (newPage < 1 || newPage > totalPages) return;
  state.page = newPage;
  loadProjectsList();
};

// ─── 6. Prediction History ────────────────────────────────────────────────
window.loadPredictionsList = async function () {
  if (!isLoggedIn()) return;
  const tbody = document.getElementById('predictions-table-body');
  if (!tbody) return;

  tbody.innerHTML = '<tr><td colspan="7" class="text-center py-16 text-on-surface-variant">Loading predictions...</td></tr>';

  try {
    const { rvListPredictions } = await import('./api.js');
    const { page, pageSize } = paginationState.predictions;
    const data = await rvListPredictions({ page, page_size: pageSize });

    paginationState.predictions.total = data.total || 0;
    updatePaginationInfo('predictions', data.total || 0);

    if (!data.items || data.items.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" class="text-center py-16 text-on-surface-variant">No predictions recorded yet.</td></tr>';
      return;
    }

    tbody.innerHTML = data.items.map(pred => renderPredictionRow(pred)).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center py-16 text-error">Failed to load predictions: ${err.message}</td></tr>`;
  }
};

function renderPredictionRow(pred) {
  const riskColors = {
    CRITICAL: 'bg-error/10 text-error border-error/20',
    HIGH: 'bg-orange-400/10 text-orange-400 border-orange-400/20',
    MEDIUM: 'bg-tertiary/10 text-tertiary border-tertiary/20',
    LOW: 'bg-green-500/10 text-green-400 border-green-400/20',
  };
  const riskClass = riskColors[pred.risk_level] || 'bg-white/5 text-on-surface-variant border-white/10';
  const date = pred.created_at ? new Date(pred.created_at).toLocaleString() : '—';
  const score = pred.risk_score != null ? pred.risk_score.toFixed(0) : '—';
  const prob = pred.failure_probability != null ? (pred.failure_probability * 100).toFixed(1) + '%' : '—';

  return `
    <tr class="hover:bg-white/5 transition-colors">
      <td class="p-4 font-label-mono text-xs">${escapeHtml(pred.project_external_id || '—')}</td>
      <td class="p-4 font-bold text-sm max-w-[200px] truncate">${escapeHtml(pred.project_name || '—')}</td>
      <td class="p-4">
        <span class="px-2 py-1 rounded-lg text-[9px] font-bold uppercase border ${riskClass}">${pred.risk_level || '—'}</span>
      </td>
      <td class="p-4 text-center font-bold">${score}</td>
      <td class="p-4 text-center font-bold">${prob}</td>
      <td class="p-4 text-xs text-on-surface-variant">${date}</td>
      <td class="p-4 text-right">
        <div class="flex items-center justify-end gap-2">
          <button onclick="downloadPDFAction('${pred.id}')" title="Download PDF" class="p-1.5 rounded-lg hover:bg-white/10 text-on-surface-variant hover:text-primary transition-colors">
            <span class="material-symbols-outlined text-sm">picture_as_pdf</span>
          </button>
          <button onclick="downloadExcelAction('${pred.id}')" title="Download Excel" class="p-1.5 rounded-lg hover:bg-white/10 text-on-surface-variant hover:text-tertiary transition-colors">
            <span class="material-symbols-outlined text-sm">table_chart</span>
          </button>
        </div>
      </td>
    </tr>`;
}

window.downloadPDFAction = async function (predictionId) {
  try {
    const { downloadPDFReport } = await import('./api.js');
    showToast('Generating PDF report...', 'info');
    await downloadPDFReport(predictionId);
    showToast('PDF report downloaded.', 'success');
  } catch (err) {
    showToast('PDF download failed: ' + err.message, 'error');
  }
};

window.downloadExcelAction = async function (predictionId) {
  try {
    const { downloadExcelReport } = await import('./api.js');
    showToast('Generating Excel report...', 'info');
    await downloadExcelReport(predictionId);
    showToast('Excel report downloaded.', 'success');
  } catch (err) {
    showToast('Excel download failed: ' + err.message, 'error');
  }
};

window.changePredictionsPage = function (direction) {
  const state = paginationState.predictions;
  const newPage = state.page + direction;
  const totalPages = Math.ceil(state.total / state.pageSize);
  if (newPage < 1 || newPage > totalPages) return;
  state.page = newPage;
  loadPredictionsList();
};

// ─── 7. Model Training & Registry ─────────────────────────────────────────
async function loadActiveModelInfo() {
  if (!isLoggedIn()) return;
  try {
    const { rvGetActiveModel } = await import('./api.js');
    const model = await rvGetActiveModel();
    document.getElementById('active-model-tag').textContent = model.version_tag || '—';
    document.getElementById('active-model-algorithm').textContent = model.algorithm || '—';
    document.getElementById('active-model-cv-score').textContent = model.cv_score != null ? model.cv_score.toFixed(4) : (model.f1_score != null ? model.f1_score.toFixed(4) : '—');
    document.getElementById('active-model-accuracy').textContent = model.accuracy != null ? (model.accuracy * 100).toFixed(2) + '%' : '—';
    document.getElementById('active-model-date').textContent = model.created_at ? new Date(model.created_at).toLocaleDateString() : '—';
  } catch (err) {
    console.warn('[App] Active model load failed:', err.message);
    ['active-model-tag', 'active-model-algorithm', 'active-model-cv-score', 'active-model-accuracy', 'active-model-date'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.textContent = 'Unavailable';
    });
  }
}

async function loadModelVersionsList() {
  if (!isLoggedIn()) return;
  const tbody = document.getElementById('model-versions-table-body');
  if (!tbody) return;

  tbody.innerHTML = '<tr><td colspan="7" class="text-center py-12 text-on-surface-variant">Loading versions...</td></tr>';

  try {
    const { rvListModelVersions } = await import('./api.js');
    const data = await rvListModelVersions();

    // /models/versions returns a plain List not {items, total}
    const versions = Array.isArray(data) ? data : (data.items || []);

    if (versions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" class="text-center py-12 text-on-surface-variant">No trained model versions found.</td></tr>';
      return;
    }

    tbody.innerHTML = versions.map(v => {
      const isActive = v.is_active;
      const date = v.created_at ? new Date(v.created_at).toLocaleDateString() : '—';
      const f1 = v.f1_score != null ? v.f1_score.toFixed(4) : (v.cv_score != null ? v.cv_score.toFixed(4) : '—');
      const acc = v.accuracy != null ? (v.accuracy * 100).toFixed(2) + '%' : '—';
      return `
        <tr class="hover:bg-white/5 transition-colors ${isActive ? 'bg-primary/5' : ''}">
          <td class="p-4 font-label-mono text-xs font-bold text-primary">${escapeHtml(v.version_tag || v.id?.substring(0, 8))}</td>
          <td class="p-4 text-sm">${escapeHtml(v.algorithm || '—')}</td>
          <td class="p-4 text-center font-bold text-tertiary">${f1}</td>
          <td class="p-4 text-center font-bold">${acc}</td>
          <td class="p-4 text-xs text-on-surface-variant">${date}</td>
          <td class="p-4 text-center">
            ${isActive
              ? '<span class="px-2 py-1 rounded-full text-[9px] font-bold bg-green-500/10 text-green-400 border border-green-400/20 uppercase">Active</span>'
              : '<span class="px-2 py-1 rounded-full text-[9px] font-bold bg-white/5 text-on-surface-variant border border-white/10 uppercase">Archived</span>'}
          </td>
          <td class="p-4 text-right">
            ${!isActive ? `<button onclick="rollbackModel('${v.id}')" class="text-xs px-3 py-1.5 rounded-xl border border-white/10 hover:bg-primary/10 hover:border-primary/20 hover:text-primary transition-colors font-bold">Rollback</button>` : ''}
          </td>
        </tr>`;
    }).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center py-12 text-error">Failed to load model versions: ${err.message}</td></tr>`;
  }
}

window.rollbackModel = async function (versionId) {
  if (!confirm('Rollback to this model version as active? The current active model will be archived.')) return;
  try {
    const { rvRollbackModel } = await import('./api.js');
    await rvRollbackModel(versionId);
    showToast('Model rolled back successfully.', 'success');
    loadActiveModelInfo();
    loadModelVersionsList();
  } catch (err) {
    showToast('Rollback failed: ' + err.message, 'error');
  }
};

async function handleRetrainingSubmit() {
  const fileInput = document.getElementById('dataset-file-input');
  const btn = document.getElementById('btn-trigger-retrain');
  const notes = document.getElementById('retrain-notes').value.trim();

  if (!fileInput.files.length) {
    showToast('Please select a dataset file to upload.', 'warning');
    return;
  }

  try {
    setButtonLoading(btn, true, 'Uploading & Training...');
    const { uploadAndTrain } = await import('./api.js');
    const result = await uploadAndTrain(fileInput.files[0]);
    showToast('Retraining job submitted successfully! Check back in a few minutes.', 'success');
    console.log('[Retrain] Result:', result);
    document.getElementById('form-retrain').reset();
    document.getElementById('dataset-upload-status').textContent = 'Drag & drop raw dataset or click to browse (.csv, .xlsx, .json)';
    document.getElementById('dataset-upload-status').classList.remove('text-primary');
    setTimeout(() => {
      loadActiveModelInfo();
      loadModelVersionsList();
    }, 3000);
  } catch (err) {
    showToast('Retraining failed: ' + err.message, 'error');
  } finally {
    setButtonLoading(btn, false, 'Initiate Retraining');
  }
}

// ─── 8. Notifications ────────────────────────────────────────────────────
async function loadNotificationsList() {
  if (!isLoggedIn()) return;
  const container = document.getElementById('notifications-list');
  if (!container) return;

  container.innerHTML = '<div class="text-center py-12 text-on-surface-variant">Loading notifications...</div>';

  try {
    const { rvListNotifications } = await import('./api.js');
    const data = await rvListNotifications(null, 1, 50);

    if (!data.items || data.items.length === 0) {
      container.innerHTML = '<div class="text-center py-12 text-on-surface-variant glass-card rounded-3xl p-12">No notifications yet. System events will appear here.</div>';
      return;
    }

    container.innerHTML = data.items.map(n => renderNotificationItem(n)).join('');
  } catch (err) {
    container.innerHTML = `<div class="text-center py-12 text-error">Failed to load notifications: ${err.message}</div>`;
  }
}

function renderNotificationItem(n) {
  const typeColors = {
    success: 'border-l-green-400 bg-green-500/5',
    warning: 'border-l-tertiary bg-tertiary/5',
    error: 'border-l-error bg-error/5',
    info: 'border-l-primary bg-primary/5',
  };
  const typeIcons = {
    success: 'check_circle',
    warning: 'warning',
    error: 'error',
    info: 'info',
  };
  const color = typeColors[n.type] || typeColors.info;
  const icon = typeIcons[n.type] || 'notifications';
  const date = n.created_at ? new Date(n.created_at).toLocaleString() : '';
  const unreadClass = !n.is_read ? 'ring-1 ring-white/10' : 'opacity-70';

  return `
    <div id="notif-${n.id}" class="glass-card rounded-2xl p-5 border-l-4 ${color} ${unreadClass} transition-all duration-300">
      <div class="flex items-start gap-4">
        <span class="material-symbols-outlined text-2xl mt-0.5 shrink-0">${icon}</span>
        <div class="flex-1 min-w-0">
          <div class="flex items-center justify-between gap-2 mb-1">
            <h4 class="font-bold text-on-surface text-sm truncate">${escapeHtml(n.title)}</h4>
            <span class="text-[10px] text-on-surface-variant shrink-0">${date}</span>
          </div>
          <p class="text-xs text-on-surface-variant">${escapeHtml(n.message)}</p>
        </div>
        ${!n.is_read ? `
          <button onclick="markNotificationRead('${n.id}')" title="Mark as read" class="p-1.5 rounded-lg hover:bg-white/10 text-on-surface-variant hover:text-primary transition-colors shrink-0">
            <span class="material-symbols-outlined text-sm">done</span>
          </button>` : ''}
      </div>
    </div>`;
}

window.markNotificationRead = async function (notifId) {
  try {
    const { rvMarkNotificationRead } = await import('./api.js');
    await rvMarkNotificationRead(notifId);
    const el = document.getElementById(`notif-${notifId}`);
    if (el) {
      el.classList.remove('ring-1', 'ring-white/10');
      el.classList.add('opacity-70');
      const btn = el.querySelector('button');
      if (btn) btn.remove();
    }
    refreshNotificationBadge();
  } catch (err) {
    showToast('Failed to mark notification: ' + err.message, 'error');
  }
};

window.markAllNotificationsReadAction = async function () {
  try {
    const { rvMarkAllNotificationsRead } = await import('./api.js');
    await rvMarkAllNotificationsRead();
    showToast('All notifications marked as read.', 'success');
    loadNotificationsList();
    refreshNotificationBadge();
  } catch (err) {
    showToast('Failed: ' + err.message, 'error');
  }
};

// ─── 9. Profile ───────────────────────────────────────────────────────────
async function loadProfileData() {
  if (!isLoggedIn()) return;
  const user = getCurrentUser();
  if (!user) {
    // Refresh from API
    try {
      const { rvGetMe } = await import('./api.js');
      const freshUser = await rvGetMe();
      localStorage.setItem('rv_user', JSON.stringify(freshUser));
      populateProfileUI(freshUser);
    } catch (err) {
      console.warn('[App] Profile load failed:', err.message);
    }
  } else {
    populateProfileUI(user);
  }
}

function populateProfileUI(user) {
  const setEl = (id, val) => {
    const el = document.getElementById(id);
    if (el) el.textContent = val || '—';
  };

  setEl('profile-fullname', user.full_name || user.username);
  setEl('profile-role', user.role);
  setEl('profile-username', user.username);
  setEl('profile-email', user.email);

  if (user.created_at) {
    const d = new Date(user.created_at);
    setEl('profile-created-date', d.toLocaleDateString('en-US', { year: 'numeric', month: 'long' }));
  }

  const roleEl = document.getElementById('profile-role');
  if (roleEl) {
    roleEl.className = 'px-2 py-0.5 rounded text-[10px] font-label-mono bg-primary/20 text-primary uppercase';
  }
}

// ─── 10. Audit Logs ───────────────────────────────────────────────────────
async function loadAuditLogs() {
  if (!isLoggedIn() || !isAdmin()) return;
  const tbody = document.getElementById('audit-table-body');
  if (!tbody) return;

  tbody.innerHTML = '<tr><td colspan="7" class="text-center py-12 text-on-surface-variant">Loading audit logs...</td></tr>';

  try {
    const { page, pageSize } = paginationState.audit;
    const apiModule = await import('./api.js');
    // Check if rvListAuditLogs exists, otherwise use apiFetch wrapper
    const data = await apiFetchDirect(`/audit?page=${page}&page_size=${pageSize}`);

    paginationState.audit.total = data.total || 0;
    updatePaginationInfo('audit', data.total || 0);

    if (!data.items || data.items.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" class="text-center py-12 text-on-surface-variant">No audit logs found.</td></tr>';
      return;
    }

    tbody.innerHTML = data.items.map(log => {
      const date = log.timestamp ? new Date(log.timestamp).toLocaleString() : '—';
      const isSuccess = log.status === 'success';
      const successClass = isSuccess ? 'text-green-400' : 'text-error';
      const successLabel = log.status || (isSuccess ? 'Success' : 'Failed');
      const resource = log.resource_type ? `${log.resource_type}${log.resource_id ? ':' + log.resource_id.substring(0, 8) : ''}` : '—';
      return `
        <tr class="hover:bg-white/5 transition-colors">
          <td class="p-4 text-xs text-on-surface-variant">${date}</td>
          <td class="p-4 font-label-mono text-xs font-bold">${escapeHtml(log.action || '—')}</td>
          <td class="p-4 text-xs">${escapeHtml(log.user_id?.substring(0, 8) || '—')}</td>
          <td class="p-4 text-xs text-on-surface-variant">${escapeHtml(log.ip_address || '—')}</td>
          <td class="p-4 text-xs">${escapeHtml(resource)}</td>
          <td class="p-4 text-xs font-bold ${successClass}">${escapeHtml(successLabel)}</td>
          <td class="p-4 text-xs text-on-surface-variant max-w-[200px] truncate">${escapeHtml(log.description || '—')}</td>
        </tr>`;
    }).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center py-12 text-error">Failed to load audit logs: ${err.message}</td></tr>`;
  }
}

async function apiFetchDirect(endpoint) {
  const token = localStorage.getItem('rv_access_token');
  const res = await fetch('/api/v1' + endpoint, {
    headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
  });
  if (!res.ok) {
    const e = await res.json().catch(() => ({}));
    throw new Error(e.detail || `HTTP ${res.status}`);
  }
  return res.json();
}

window.changeAuditPage = function (direction) {
  const state = paginationState.audit;
  const newPage = state.page + direction;
  const totalPages = Math.ceil(state.total / state.pageSize);
  if (newPage < 1 || newPage > totalPages) return;
  state.page = newPage;
  loadAuditLogs();
};

// ─── 11. Backend Health Check ─────────────────────────────────────────────
async function checkBackendHealth() {
  const banner = document.getElementById('backend-status-banner');
  if (!banner) return;

  try {
    const res = await fetch('/api/v1/health', { signal: AbortSignal.timeout(4000) });
    const data = await res.json();
    const dot = banner.querySelector('span:first-child');
    const label = banner.querySelector('span:last-child');

    if (res.ok && data.status === 'healthy') {
      if (dot) { dot.className = 'w-2 h-2 rounded-full bg-green-400 animate-pulse'; }
      if (label) { label.textContent = 'API Online'; label.className = 'font-label-mono text-[10px] uppercase tracking-widest text-green-400'; }
    } else {
      if (dot) { dot.className = 'w-2 h-2 rounded-full bg-tertiary animate-pulse'; }
      if (label) { label.textContent = 'API Degraded'; label.className = 'font-label-mono text-[10px] uppercase tracking-widest text-tertiary'; }
    }
  } catch {
    const dot = banner.querySelector('span:first-child');
    const label = banner.querySelector('span:last-child');
    if (dot) { dot.className = 'w-2 h-2 rounded-full bg-error'; }
    if (label) { label.textContent = 'API Offline'; label.className = 'font-label-mono text-[10px] uppercase tracking-widest text-error'; }
  }
}

// ─── 12. Pagination Info Helper ───────────────────────────────────────────
function updatePaginationInfo(key, total) {
  const state = paginationState[key];
  const start = total === 0 ? 0 : (state.page - 1) * state.pageSize + 1;
  const end = Math.min(state.page * state.pageSize, total);
  const totalPages = Math.ceil(total / state.pageSize);

  const infoEl = document.getElementById(`${key}-pagination-info`);
  const prevBtn = document.getElementById(`btn-${key}-prev`);
  const nextBtn = document.getElementById(`btn-${key}-next`);

  if (infoEl) infoEl.textContent = `Showing ${key} ${start}–${end} of ${total}`;
  if (prevBtn) prevBtn.disabled = state.page <= 1;
  if (nextBtn) nextBtn.disabled = state.page >= totalPages;
}

// ─── 13. Card & Interaction Utilities ─────────────────────────────────────
function initCardInteractions() {
  document.addEventListener('mousemove', e => {
    document.querySelectorAll('.glass-card, .glass-panel').forEach(card => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      card.style.setProperty('--mouse-x', `${x}px`);
      card.style.setProperty('--mouse-y', `${y}px`);
    });

    const simSection = document.getElementById('page-simulation');
    if (simSection && simSection.classList.contains('active')) {
      const cards = simSection.querySelectorAll('.glass-card');
      const xOffset = (window.innerWidth / 2 - e.pageX) / 80;
      const yOffset = (window.innerHeight / 2 - e.pageY) / 80;
      cards.forEach(card => {
        card.style.transform = `translate(${xOffset}px, ${yOffset}px)`;
        card.style.transition = 'transform 0.1s ease-out';
      });
    }
  });
}

function initScrollAnimations() {
  const observerOptions = {
    threshold: 0.05,
    rootMargin: '0px 0px -50px 0px'
  };

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('opacity-100', 'translate-y-0');
        entry.target.classList.remove('opacity-0', 'translate-y-10');
        observer.unobserve(entry.target);
      }
    });
  }, observerOptions);

  document.querySelectorAll('.glass-card, .glass-panel').forEach(el => {
    if (!el.classList.contains('sticky') && !el.closest('.no-scroll-animate')) {
      el.classList.add('transition-all', 'duration-700', 'opacity-0', 'translate-y-10');
      observer.observe(el);
    }
  });
}

// ─── 14. Simulator (Preserved Unchanged) ─────────────────────────────────
function initSimulator() {
  const budgetSliders = document.querySelectorAll('.slider-budget');
  const velocitySliders = document.querySelectorAll('.slider-velocity');
  const bugSliders = document.querySelectorAll('.slider-bug');

  budgetSliders.forEach(slider => {
    slider.addEventListener('input', e => {
      const val = parseFloat(e.target.value);
      const text = (val >= 1000000) ? (val / 1000000).toFixed(1) + 'M' : (val / 1000).toFixed(0) + 'k';
      document.querySelectorAll('.val-budget').forEach(span => span.innerText = '$' + text);
      document.querySelectorAll('.slider-budget').forEach(s => s.value = e.target.value);
      calculateRiskScore();
    });
  });

  velocitySliders.forEach(slider => {
    slider.addEventListener('input', e => {
      const val = parseInt(e.target.value);
      document.querySelectorAll('.val-velocity').forEach(span => span.innerText = val + (e.target.dataset.unit || ''));
      document.querySelectorAll('.slider-velocity').forEach(s => s.value = e.target.value);
      calculateRiskScore();
    });
  });

  bugSliders.forEach(slider => {
    slider.addEventListener('input', e => {
      const val = parseFloat(e.target.value);
      let desc = '';
      if (val < 0.25) desc = 'Low Risk (' + val.toFixed(2) + ')';
      else if (val < 0.6) desc = 'Nominal (' + val.toFixed(2) + ')';
      else desc = 'Critical (' + val.toFixed(2) + ')';
      document.querySelectorAll('.val-bug').forEach(span => span.innerText = desc);
      document.querySelectorAll('.slider-bug').forEach(s => s.value = e.target.value);
      calculateRiskScore();
    });
  });

  function calculateRiskScore() {
    const budget = parseFloat(document.querySelector('.slider-budget')?.value || 0);
    const velocity = parseFloat(document.querySelector('.slider-velocity')?.value || 0);
    const bugDensity = parseFloat(document.querySelector('.slider-bug')?.value || 0);

    const budgetRatio = budget / 5000000;
    const velocityRatio = velocity / 100;
    const bugRatio = bugDensity;

    let score = (budgetRatio * 35) + (velocityRatio * 35) + ((1 - bugRatio) * 30);
    score = Math.min(100, Math.max(0, Math.round(score)));

    const circumference = 691.15;
    const offset = circumference - (score / 100) * circumference;

    document.querySelectorAll('.health-progress-ring').forEach(ring => {
      ring.style.strokeDashoffset = offset;
    });
    document.querySelectorAll('.health-score-val').forEach(el => {
      el.innerText = score;
    });

    const pillText = document.querySelectorAll('.health-status-pill');
    const ringPath = document.querySelectorAll('.health-progress-ring');

    if (score >= 75) {
      pillText.forEach(p => { p.innerText = "LOW RISK DETECTED"; p.className = "px-6 py-2 rounded-full bg-primary/10 text-primary border border-primary/20 inline-block font-bold health-status-pill"; });
      ringPath.forEach(r => r.className.baseVal = "text-primary health-progress-ring transition-all duration-300");
    } else if (score >= 45) {
      pillText.forEach(p => { p.innerText = "NOMINAL RISK LEVEL"; p.className = "px-6 py-2 rounded-full bg-tertiary/10 text-tertiary border border-tertiary/20 inline-block font-bold health-status-pill"; });
      ringPath.forEach(r => r.className.baseVal = "text-tertiary health-progress-ring transition-all duration-300");
    } else {
      pillText.forEach(p => { p.innerText = "CRITICAL RISK WARNING"; p.className = "px-6 py-2 rounded-full bg-error/10 text-error border border-error/20 inline-block font-bold health-status-pill"; });
      ringPath.forEach(r => r.className.baseVal = "text-error health-progress-ring transition-all duration-300");
    }
  }

  calculateRiskScore();
}

// ─── 15. Workflow Deep Dives (Preserved) ─────────────────────────────────
window.showDeepDive = function (step) {
  const defaultView = document.getElementById('default-view');
  const connector = document.getElementById('active-connector');
  if (!defaultView || !connector) return;

  [1, 2, 3].forEach(id => {
    const overlay = document.getElementById(`deep-dive-${id}`);
    if (overlay) overlay.classList.add('hidden');
    const card = document.querySelector(`[data-step="${id}"]`);
    if (card) card.classList.remove('bg-white/10', 'ring-1', 'ring-white/20');
  });

  defaultView.classList.add('hidden');
  const activeDive = document.getElementById(`deep-dive-${step}`);
  if (activeDive) activeDive.classList.remove('hidden');
  const activeCard = document.querySelector(`[data-step="${step}"]`);
  if (activeCard) activeCard.classList.add('bg-white/10', 'ring-1', 'ring-white/20');
  connector.className = `workflow-connector absolute left-[23px] top-4 bottom-4 w-[1px] opacity-100 transition-all duration-500 active-${step}`;
  connector.classList.add('animate-pulse');
  setTimeout(() => connector.classList.remove('animate-pulse'), 1000);
};

window.resetDeepDive = function () {
  const defaultView = document.getElementById('default-view');
  const connector = document.getElementById('active-connector');
  if (!defaultView || !connector) return;

  [1, 2, 3].forEach(id => {
    const overlay = document.getElementById(`deep-dive-${id}`);
    if (overlay) overlay.classList.add('hidden');
    const card = document.querySelector(`[data-step="${id}"]`);
    if (card) card.classList.remove('bg-white/10', 'ring-1', 'ring-white/20');
  });

  defaultView.classList.remove('hidden');
  connector.className = `workflow-connector absolute left-[23px] top-4 bottom-4 w-[1px] opacity-30 active-1`;
};

// ─── 16. Toast Notification System ───────────────────────────────────────
window.showToast = function (message, type = 'info') {
  const container = getOrCreateToastContainer();

  const typeStyles = {
    success: 'border-green-400/30 bg-green-500/10 text-green-400',
    error: 'border-error/30 bg-error/10 text-error',
    warning: 'border-tertiary/30 bg-tertiary/10 text-tertiary',
    info: 'border-primary/30 bg-primary/10 text-primary',
  };
  const typeIcons = {
    success: 'check_circle',
    error: 'error',
    warning: 'warning',
    info: 'info',
  };

  const toast = document.createElement('div');
  toast.className = `flex items-center gap-3 px-5 py-4 rounded-2xl border backdrop-blur-xl glass-card shadow-xl transition-all duration-500 translate-y-2 opacity-0 ${typeStyles[type] || typeStyles.info}`;
  toast.innerHTML = `
    <span class="material-symbols-outlined text-sm">${typeIcons[type] || 'info'}</span>
    <span class="text-xs font-bold">${escapeHtml(message)}</span>
  `;

  container.appendChild(toast);
  requestAnimationFrame(() => {
    toast.classList.remove('translate-y-2', 'opacity-0');
  });

  setTimeout(() => {
    toast.classList.add('translate-y-2', 'opacity-0');
    setTimeout(() => toast.remove(), 500);
  }, 4000);
};

function getOrCreateToastContainer() {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'fixed bottom-6 right-6 z-[9999] flex flex-col gap-3 max-w-sm';
    document.body.appendChild(container);
  }
  return container;
}

// ─── 17. Utility Helpers ──────────────────────────────────────────────────
function escapeHtml(str) {
  if (!str && str !== 0) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function setButtonLoading(btn, loading, text) {
  if (!btn) return;
  btn.disabled = loading;
  btn.textContent = text;
  if (loading) {
    btn.classList.add('opacity-70', 'cursor-not-allowed');
  } else {
    btn.classList.remove('opacity-70', 'cursor-not-allowed');
  }
}
