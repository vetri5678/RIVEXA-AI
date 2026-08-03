import { spawn, exec } from 'child_process';
import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

// ── Configuration ──
const PORTS = {
  frontend: 5176,
  springboot: 8080,
  fastapi: 8000
};

const SERVICES = {
  springboot: {
    name: 'Spring Boot Backend',
    cwd: path.join(rootDir, 'stitch_riskvision_ai_intelligence_platform', 'riskvision_ai_springboot_backend'),
    command: 'mvn',
    args: ['spring-boot:run'],
    healthCheck: 'http://localhost:8080/api/v1/health'
  },
  fastapi: {
    name: 'FastAPI Prediction Engine',
    cwd: path.join(rootDir, 'stitch_riskvision_ai_intelligence_platform', 'riskvision_ai_backend'),
    command: path.join(rootDir, 'stitch_riskvision_ai_intelligence_platform', 'riskvision_ai_backend', '.venv', 'Scripts', 'python.exe'),
    args: ['-m', 'uvicorn', 'main:app', '--host', '0.0.0.0', '--port', '8000'],
    healthCheck: 'http://localhost:8000/'
  },
  frontend: {
    name: 'Vite React Frontend',
    cwd: rootDir,
    command: 'npx',
    args: ['vite', 'stitch_riskvision_ai_intelligence_platform']
  }
};

const children = [];

// ── Helper: Log formatting ──
function log(service, message, type = 'info') {
  const colors = {
    system: '\x1b[35m', // Magenta
    springboot: '\x1b[32m', // Green
    fastapi: '\x1b[36m', // Cyan
    frontend: '\x1b[34m', // Blue
    reset: '\x1b[0m',
    error: '\x1b[31m', // Red
    warn: '\x1b[33m' // Yellow
  };
  const prefixColor = colors[service] || colors.reset;
  const textColor = type === 'error' ? colors.error : type === 'warn' ? colors.warn : colors.reset;
  const lines = message.toString().trim().split('\n');
  for (const line of lines) {
    console.log(`${prefixColor}[${service.toUpperCase()}]${colors.reset} ${textColor}${line}${colors.reset}`);
  }
}

// ── Helper: Check if a port is in use ──
function checkPort(port) {
  return new Promise((resolve) => {
    const server = http.createServer().listen(port, 'localhost', () => {
      server.close(() => resolve(false));
    });
    server.on('error', () => {
      resolve(true);
    });
  });
}

// ── Helper: Graceful exit ──
function cleanup() {
  log('system', 'Shutting down all background services...', 'warn');
  for (const child of children) {
    if (child.pid) {
      log('system', `Terminating child process ${child.pid} (${child.name})...`);
      try {
        process.kill(-child.pid); // Kill process group
      } catch (e) {
        try {
          child.kill('SIGINT');
        } catch (e2) {}
      }
    }
  }
  process.exit(0);
}

process.on('SIGINT', cleanup);
process.on('SIGTERM', cleanup);

// ── Phase 1: Environment Validation ──
async function validateEnvironment() {
  log('system', '=== Phase 1: Environment Variable Check ===');
  
  // Load environment variables from Spring Boot .env
  const dotenvPath = path.join(rootDir, 'stitch_riskvision_ai_intelligence_platform', 'riskvision_ai_springboot_backend', '.env');
  if (fs.existsSync(dotenvPath)) {
    log('system', `Loading environment from: ${dotenvPath}`);
    const content = fs.readFileSync(dotenvPath, 'utf8');
    for (const line of content.split('\n')) {
      const match = line.match(/^\s*([\w.\-]+)\s*=\s*(.*)\s*$/);
      if (match) {
        const key = match[1];
        let val = match[2].trim();
        if (val.startsWith('"') && val.endsWith('"')) {
          val = val.substring(1, val.length - 1);
        }
        process.env[key] = val;
      }
    }
  }

  const requiredEnv = [
    'GOOGLE_CLIENT_ID',
    'GOOGLE_CLIENT_SECRET',
    'SECRET_KEY',
    'SUPABASE_DB_HOST',
    'SUPABASE_DB_NAME',
    'SUPABASE_DB_USER',
    'SUPABASE_DB_PASSWORD'
  ];

  let missing = [];
  for (const env of requiredEnv) {
    if (!process.env[env] || process.env[env].includes('change-me') || process.env[env].includes('your-actual')) {
      missing.push(env);
    }
  }

  if (missing.length > 0) {
    log('system', '🚨 [ENV ERROR] The following critical environment variables are missing or use default placeholders:', 'error');
    for (const key of missing) {
      log('system', `   - ${key}`, 'error');
    }
    log('system', 'Please configure real credentials in riskvision_ai_springboot_backend/.env', 'warn');
  } else {
    log('system', '✅ Environment validation completed (all credentials present).');
  }
}

// ── Phase 2: Port Conflict Checks ──
async function checkConflicts() {
  log('system', '=== Phase 2: Checking Port Conflicts ===');
  for (const [name, port] of Object.entries(PORTS)) {
    const inUse = await checkPort(port);
    if (inUse) {
      log('system', `⚠️ Port ${port} is currently in use! Service: ${name}`, 'warn');
      log('system', `Attempting to automatically release port ${port}...`, 'warn');
      await forceKillPort(port);
    } else {
      log('system', `✅ Port ${port} is free and ready.`);
    }
  }
}

function forceKillPort(port) {
  return new Promise((resolve) => {
    exec(`netstat -ano | findstr :${port}`, (err, stdout) => {
      if (stdout) {
        const lines = stdout.trim().split('\n');
        const pids = new Set();
        for (const line of lines) {
          const parts = line.trim().split(/\s+/);
          const pid = parts[parts.length - 1];
          if (pid && !isNaN(pid) && pid !== '0') {
            pids.add(pid);
          }
        }
        for (const pid of pids) {
          log('system', `Killing PID ${pid} listening on port ${port}...`, 'warn');
          try {
            exec(`taskkill /F /PID ${pid}`, () => resolve(true));
            return;
          } catch (e) {}
        }
      }
      resolve(true);
    });
  });
}

// ── Phase 3: Start Services ──
function startService(key, cfg) {
  return new Promise((resolve) => {
    log('system', `Launching ${cfg.name}...`);
    let cmd = process.platform === 'win32' && cfg.command === 'mvn' ? 'mvn.cmd' : cfg.command;
    if (process.platform === 'win32' && cmd.includes(' ')) {
      cmd = `"${cmd}"`;
    }
    const child = spawn(cmd, cfg.args, {
      cwd: cfg.cwd,
      detached: true,
      shell: true
    });

    child.name = cfg.name;
    children.push(child);

    child.stdout.on('data', (data) => {
      log(key, data.toString());
    });

    child.stderr.on('data', (data) => {
      log(key, data.toString(), 'error');
    });

    child.on('exit', (code) => {
      log(key, `${cfg.name} exited with code ${code}`, code === 0 ? 'info' : 'error');
    });

    // Short timeout to assume process spawned successfully
    setTimeout(() => resolve(child), 1500);
  });
}

// ── Phase 4: Wait for Health ──
function pollHealth(url, timeoutMs = 60000) {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const interval = setInterval(() => {
      if (Date.now() - start > timeoutMs) {
        clearInterval(interval);
        reject(new Error(`Timeout waiting for health endpoint: ${url}`));
        return;
      }

      http.get(url, (res) => {
        if (res.statusCode === 200) {
          clearInterval(interval);
          resolve(true);
        }
      }).on('error', () => {
        // Silent catch while waiting
      });
    }, 2000);
  });
}

// ── Main Execution ──
async function main() {
  await validateEnvironment();
  await checkConflicts();

  log('system', '=== Phase 3: Launching Services ===');
  await startService('springboot', SERVICES.springboot);
  await startService('fastapi', SERVICES.fastapi);

  log('system', '=== Phase 4: Waiting for Health Checks ===');
  try {
    log('system', 'Waiting for Spring Boot backend to be healthy at http://localhost:8080/api/v1/health...');
    await pollHealth(SERVICES.springboot.healthCheck, 180000);
    log('system', '✅ Spring Boot backend is healthy!');

    log('system', 'Waiting for FastAPI prediction engine to be healthy at http://localhost:8000/...');
    await pollHealth(SERVICES.fastapi.healthCheck, 120000);
    log('system', '✅ FastAPI engine is healthy!');

    log('system', '=== Phase 5: Starting Vite Frontend ===');
    await startService('frontend', SERVICES.frontend);

    // Give Vite server a moment to start before opening browser
    setTimeout(() => {
      log('system', `Opening default browser to http://localhost:${PORTS.frontend}/...`);
      exec(`start http://localhost:${PORTS.frontend}`);
    }, 2000);

  } catch (err) {
    log('system', `❌ Health check failure: ${err.message}`, 'error');
    cleanup();
  }
}

main().catch((err) => {
  log('system', `Fatal startup error: ${err.message}`, 'error');
  cleanup();
});
