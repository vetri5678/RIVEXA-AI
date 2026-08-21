#!/usr/bin/env node
/**
 * RiskVision AI — Unified Development Runner
 * Orchestrates Spring Boot, FastAPI, and Vite frontend startup.
 *
 * Key design: uses spawn WITHOUT shell:true so that paths with spaces
 * on Windows work correctly — Node passes them directly to CreateProcess.
 */

const { spawn, execSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const http = require('http');
const net = require('net');

// ─── Project Roots ────────────────────────────────────────────────────────────
const PROJECT_ROOT = path.resolve(__dirname, '..');
const SPRINGBOOT_DIR = path.join(PROJECT_ROOT, 'riskvision_ai_springboot_backend');
const FASTAPI_DIR = path.join(PROJECT_ROOT, 'riskvision_ai_backend');
const FRONTEND_DIR = path.join(PROJECT_ROOT, 'dashboard');
const ENV_FILE = path.join(SPRINGBOOT_DIR, '.env');
const IS_WIN = process.platform === 'win32';

// ─── Configuration ────────────────────────────────────────────────────────────
const SPRINGBOOT_PORT = 8080;
const FASTAPI_PORT = 8000;
const FRONTEND_PORT = 5176;
const HEALTH_CHECK_INTERVAL_MS = 2000;
const HEALTH_CHECK_MAX_RETRIES = 75;

// ─── Load Environment Variables ───────────────────────────────────────────────
console.log('[SYSTEM] === Phase 1: Environment Variable Check ===');
console.log('[SYSTEM] Loading environment from: ' + ENV_FILE);

const envVars = { ...process.env };

if (fs.existsSync(ENV_FILE)) {
  const envContent = fs.readFileSync(ENV_FILE, 'utf-8');
  envContent.split('\n').forEach(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return;
    const eqIdx = trimmed.indexOf('=');
    if (eqIdx < 0) return;
    const key = trimmed.slice(0, eqIdx).trim();
    const value = trimmed.slice(eqIdx + 1).trim();
    envVars[key] = value;
  });
  console.log('[SYSTEM] ✅ Environment validation completed (all credentials present).');
} else {
  console.warn('[SYSTEM] ⚠️  .env file not found. Proceeding with system environment.');
}

// ─── Port Check ───────────────────────────────────────────────────────────────
function isPortInUse(port) {
  return new Promise((resolve) => {
    const server = net.createServer();
    server.once('error', () => resolve(true));
    server.once('listening', () => { server.close(); resolve(false); });
    server.listen(port);
  });
}

async function killPortIfNeeded(port) {
  const inUse = await isPortInUse(port);
  if (inUse) {
    console.log('[SYSTEM] ⚠️  Port ' + port + ' is in use. Attempting to free it...');
    try {
      if (IS_WIN) {
        const out = execSync('netstat -ano', { encoding: 'utf8' });
        out.split('\n').forEach(line => {
          if (line.includes(':' + port + ' ') && line.includes('LISTENING')) {
            const parts = line.trim().split(/\s+/);
            const pid = parts[parts.length - 1];
            if (pid && /^\d+$/.test(pid) && pid !== '0') {
              try { execSync('taskkill /PID ' + pid + ' /F', { stdio: 'ignore' }); } catch(_) {}
            }
          }
        });
      } else {
        execSync('lsof -ti:' + port + ' | xargs kill -9 2>/dev/null || true');
      }
    } catch (_) {}
    await new Promise(r => setTimeout(r, 1500));
  } else {
    console.log('[SYSTEM] ✅ Port ' + port + ' is free and ready.');
  }
}

// ─── Health Check ─────────────────────────────────────────────────────────────
function httpGet(url) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, { timeout: 5000 }, (res) => {
      res.resume();
      resolve(res.statusCode);
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('TIMEOUT')); });
  });
}

async function waitForHealth(label, url, maxRetries) {
  maxRetries = maxRetries || HEALTH_CHECK_MAX_RETRIES;
  for (let i = 1; i <= maxRetries; i++) {
    try {
      const status = await httpGet(url);
      if (status >= 200 && status < 400) {
        console.log('[SYSTEM] Health check passed: ' + url + ' (HTTP ' + status + ')');
        return true;
      }
    } catch (err) {
      const reason = err.code || (err.message || 'unknown').split(' ')[0];
      console.log('[SYSTEM] [SYSTEM] Health check attempt ' + i + ' failed (' + reason + ') for ' + url);
    }
    await new Promise(r => setTimeout(r, HEALTH_CHECK_INTERVAL_MS));
  }
  return false;
}

// ─── Process Launcher ─────────────────────────────────────────────────────────
// On Windows, .cmd/.bat files MUST be run via cmd.exe (EINVAL otherwise).
// Real .exe files can be spawned directly (handles paths with spaces).
// On Unix, always spawn directly.
function spawnDirect(label, executable, args, cwd, extraEnv) {
  const env = { ...envVars, ...(extraEnv || {}) };

  // Determine actual command + args for this platform
  let cmd, cmdArgs;
  if (IS_WIN) {
    const ext = path.extname(executable).toLowerCase();
    if (ext === '.cmd' || ext === '.bat' || executable === 'mvn.cmd' || executable === 'npm.cmd') {
      // Batch files need cmd.exe — pass executable and its args as separate elements
      cmd = process.env.ComSpec || 'C:\\Windows\\System32\\cmd.exe';
      cmdArgs = ['/c', executable, ...args];
    } else {
      cmd = executable;
      cmdArgs = args;
    }
  } else {
    cmd = executable;
    cmdArgs = args;
  }

  const proc = spawn(cmd, cmdArgs, {
    cwd,
    env,
    shell: false,
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });

  proc.stdout.on('data', (d) => {
    d.toString().split('\n').filter(l => l.trim()).forEach(line =>
      console.log('[' + label + '] ' + line.replace(/\r$/, ''))
    );
  });
  proc.stderr.on('data', (d) => {
    d.toString().split('\n').filter(l => l.trim()).forEach(line =>
      console.log('[' + label + '] ' + line.replace(/\r$/, ''))
    );
  });
  proc.on('close', (code) => {
    if (code !== 0 && code !== null) {
      console.log('\n[' + label + '] ' + label + ' exited with code ' + code);
    }
  });
  return proc;
}

// ─── Find Maven ───────────────────────────────────────────────────────────────
function findMvn() {
  // Check local maven-bin sibling directory
  const candidates = [
    path.join(PROJECT_ROOT, '..', 'maven-bin', 'apache-maven-3.9.6', 'bin', 'mvn.cmd'),
    path.join(PROJECT_ROOT, '..', 'maven-bin', 'apache-maven-3.9.6', 'bin', 'mvn'),
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) {
      console.log('[SYSTEM] Found Maven: ' + c);
      return c;
    }
  }
  // Fall back to system PATH — use cmd.exe to resolve mvn.cmd
  return IS_WIN ? 'mvn.cmd' : 'mvn';
}

// ─── Find Python ──────────────────────────────────────────────────────────────
function findPython() {
  const candidates = [
    path.join(FASTAPI_DIR, '.venv', 'Scripts', 'python.exe'),
    path.join(FASTAPI_DIR, '.venv', 'bin', 'python3'),
    path.join(FASTAPI_DIR, '.venv', 'bin', 'python'),
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) {
      console.log('[SYSTEM] Found Python: ' + c);
      return c;
    }
  }
  return IS_WIN ? 'python.exe' : 'python3';
}

// ─── Find npm ─────────────────────────────────────────────────────────────────
function findNpm() {
  // npm.cmd on Windows in PATH
  return IS_WIN ? 'npm.cmd' : 'npm';
}

// ─── Main Orchestrator ────────────────────────────────────────────────────────
async function main() {
  console.log('[SYSTEM] === Phase 2: Checking Port Conflicts ===');
  await killPortIfNeeded(FRONTEND_PORT);
  await killPortIfNeeded(SPRINGBOOT_PORT);
  await killPortIfNeeded(FASTAPI_PORT);

  const processes = [];

  console.log('[SYSTEM] === Phase 3: Launching Services ===');

  // ── Spring Boot ───────────────────────────────────────────────────────────────
  console.log('[SYSTEM] Launching Spring Boot Backend...');
  const mvn = findMvn();
  const springProc = spawnDirect('SPRINGBOOT', mvn, ['spring-boot:run'], SPRINGBOOT_DIR, envVars);
  processes.push(springProc);

  // ── FastAPI ───────────────────────────────────────────────────────────────────
  console.log('[SYSTEM] Launching FastAPI Prediction Engine...');
  const python = findPython();
  const fastapiProc = spawnDirect(
    'FASTAPI',
    python,
    ['-m', 'uvicorn', 'main:app', '--host', '0.0.0.0', '--port', String(FASTAPI_PORT), '--log-level', 'info'],
    FASTAPI_DIR,
    { PYTHONUNBUFFERED: '1' }
  );
  processes.push(fastapiProc);

  console.log('[SYSTEM] === Phase 4: Waiting for Health Checks ===');

  const sbUrl = 'http://localhost:' + SPRINGBOOT_PORT + '/api/v1/health';
  console.log('[SYSTEM] Waiting for Spring Boot backend to be healthy at ' + sbUrl + '...');
  const springHealthy = await waitForHealth('Spring Boot', sbUrl);
  if (!springHealthy) {
    console.error('[SYSTEM] ❌ Spring Boot failed to become healthy. Check logs above.');
    process.exit(1);
  }
  console.log('[SYSTEM] ✅ Spring Boot backend is healthy!');

  const faUrl = 'http://localhost:' + FASTAPI_PORT + '/health';
  console.log('[SYSTEM] Waiting for FastAPI prediction engine to be healthy at ' + faUrl + '...');
  const fastapiHealthy = await waitForHealth('FastAPI', faUrl, 30);
  if (!fastapiHealthy) {
    console.warn('[SYSTEM] ⚠️  FastAPI did not respond. Continuing...');
  } else {
    console.log('[SYSTEM] ✅ FastAPI engine is healthy!');
  }

  // ── Vite Frontend ─────────────────────────────────────────────────────────────
  console.log('[SYSTEM] === Phase 5: Starting Vite Frontend ===');
  console.log('[SYSTEM] Launching Vite React Frontend...');
  const npm = findNpm();
  const frontendProc = spawnDirect('FRONTEND', npm, ['run', 'dev'], FRONTEND_DIR, { FORCE_COLOR: '1' });
  processes.push(frontendProc);

  await new Promise(r => setTimeout(r, 4000));
  const appUrl = 'http://localhost:' + FRONTEND_PORT + '/';
  console.log('[SYSTEM] Opening default browser to ' + appUrl + '...');
  try {
    if (IS_WIN) {
      // Use cmd.exe just for opening the browser — that's safe
      spawn('cmd', ['/c', 'start', '', appUrl], { shell: false, detached: true, stdio: 'ignore' }).unref();
    } else if (process.platform === 'darwin') {
      spawn('open', [appUrl], { detached: true, stdio: 'ignore' }).unref();
    } else {
      spawn('xdg-open', [appUrl], { detached: true, stdio: 'ignore' }).unref();
    }
  } catch (_) {}

  console.log('\n[SYSTEM] ═══════════════════════════════════════════════');
  console.log('[SYSTEM]  ✅ RiskVision AI Platform is running!');
  console.log('[SYSTEM]  Frontend    → http://localhost:' + FRONTEND_PORT);
  console.log('[SYSTEM]  Spring Boot → http://localhost:' + SPRINGBOOT_PORT);
  console.log('[SYSTEM]  FastAPI     → http://localhost:' + FASTAPI_PORT);
  console.log('[SYSTEM]  Press Ctrl+C to stop all services.');
  console.log('[SYSTEM] ═══════════════════════════════════════════════\n');

  const shutdown = (signal) => {
    console.log('\n[SYSTEM] Received ' + signal + '. Shutting down...');
    processes.forEach(p => { try { p.kill(); } catch (_) {} });
    setTimeout(() => process.exit(0), 2000);
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));

  // Keep alive
  setInterval(() => {}, 60000);
}

main().catch(err => {
  console.error('[SYSTEM] Fatal error in dev-runner:', err);
  process.exit(1);
});
