import { spawn, exec, execSync } from 'child_process';
import http from 'http';
import net from 'net';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import readline from 'readline';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = __dirname;
const isWin = process.platform === 'win32';

// ── Color Utilities ──
const colors = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  dim: '\x1b[2m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m',
  cyan: '\x1b[36m',
  white: '\x1b[37m'
};

function log(service, message, type = 'info') {
  const serviceColors = {
    system: colors.magenta,
    springboot: colors.green,
    fastapi: colors.cyan,
    frontend: colors.blue,
    env: colors.yellow
  };
  const prefixColor = serviceColors[service] || colors.reset;
  const textColor = type === 'error' ? colors.red : type === 'warn' ? colors.yellow : colors.reset;
  const lines = message.toString().split('\n');
  for (const line of lines) {
    if (line.trim().length > 0) {
      console.log(`${prefixColor}[${service.toUpperCase()}]${colors.reset} ${textColor}${line}${colors.reset}`);
    }
  }
}

// ── Path & Executable Resolvers ──
function resolveMavenCmd() {
  // 1. Maven wrapper in backend or root
  const backendWrapper = path.join(rootDir, 'stitch_riskvision_ai_intelligence_platform', 'riskvision_ai_springboot_backend', isWin ? 'mvnw.cmd' : 'mvnw');
  if (fs.existsSync(backendWrapper)) {
    return backendWrapper;
  }
  const rootWrapper = path.join(rootDir, isWin ? 'mvnw.cmd' : 'mvnw');
  if (fs.existsSync(rootWrapper)) {
    return rootWrapper;
  }
  // 2. Configured project Maven
  const localMvnCmd = path.join(rootDir, 'maven-bin', 'apache-maven-3.9.6', 'bin', isWin ? 'mvn.cmd' : 'mvn');
  if (fs.existsSync(localMvnCmd)) {
    return localMvnCmd;
  }
  // 3. Fallback to system Maven
  return isWin ? 'mvn.cmd' : 'mvn';
}

function resolvePythonCmd() {
  const localVenvPython = path.join(
    rootDir,
    'stitch_riskvision_ai_intelligence_platform',
    'riskvision_ai_backend',
    '.venv',
    'Scripts',
    'python.exe'
  );
  if (fs.existsSync(localVenvPython)) {
    return localVenvPython;
  }
  return isWin ? 'python.exe' : 'python3';
}

function resolveViteCmd() {
  // Prefer root node_modules vite — uses the root vite.config.js
  // which now has root: 'stitch_riskvision_ai_intelligence_platform'
  // to correctly serve the Vanilla JS landing page first.
  const localVite = path.join(rootDir, 'node_modules', '.bin', isWin ? 'vite.cmd' : 'vite');
  if (fs.existsSync(localVite)) {
    return localVite;
  }
  // Fallback: dashboard's own local vite binary
  const dashboardVite = path.join(rootDir, 'stitch_riskvision_ai_intelligence_platform', 'dashboard', 'node_modules', '.bin', isWin ? 'vite.cmd' : 'vite');
  if (fs.existsSync(dashboardVite)) {
    return dashboardVite;
  }
  return isWin ? 'npx.cmd' : 'npx';
}

const CONFIG = {
  ports: {
    fastapi: 8000,
    springboot: 8080,
    frontend: 5176
  },
  directories: {
    fastapi: path.join(rootDir, 'stitch_riskvision_ai_intelligence_platform', 'riskvision_ai_backend'),
    springboot: path.join(rootDir, 'stitch_riskvision_ai_intelligence_platform', 'riskvision_ai_springboot_backend'),
    frontend: path.join(rootDir, 'stitch_riskvision_ai_intelligence_platform', 'dashboard')
  },
  models: {
    xgboost: path.join(
      rootDir,
      'stitch_riskvision_ai_intelligence_platform',
      'riskvision_ai_backend',
      'models',
      'xgboost_model.joblib'
    ),
    xgboostPkl: path.join(
      rootDir,
      'stitch_riskvision_ai_intelligence_platform',
      'riskvision_ai_backend',
      'models',
      'xgboost_model.pkl'
    )
  }
};

const SERVICES = {
  fastapi: {
    name: 'FastAPI Prediction Engine',
    cwd: CONFIG.directories.fastapi,
    command: resolvePythonCmd(),
    args: ['-m', 'uvicorn', 'main:app', '--host', '0.0.0.0', '--port', '8000'],
    healthCheck: 'http://127.0.0.1:8000/health',
    useShell: false,
    timeoutMs: 120000,
    env: { PORT: '8000' }
  },
  springboot: {
    name: 'Spring Boot Backend',
    cwd: CONFIG.directories.springboot,
    command: resolveMavenCmd(),
    args: ['spring-boot:run', '-Dspring-boot.run.jvmArguments=-Dserver.port=8080', '-Dspring-boot.run.profiles=dev'],
    healthCheck: 'http://127.0.0.1:8080/api/v1/health',
    useShell: false,
    timeoutMs: 300000,
    env: { 
      PORT: '8080', 
      SERVER_PORT: '8080',
      SPRING_PROFILES_ACTIVE: 'dev'
    }
  },
  frontend: {
    name: 'Vite Frontend (Landing Page)',
    // Run Vite from rootDir so it uses the root vite.config.js,
    // which has root: 'stitch_riskvision_ai_intelligence_platform'
    // ensuring the immersive Vanilla JS landing page is served at /.
    cwd: rootDir,
    command: resolveViteCmd(),
    args: ['--host', '127.0.0.1', '--port', '5176'],
    healthCheck: 'http://127.0.0.1:5176',
    useShell: false,
    timeoutMs: 60000,
    env: { PORT: '5176', VITE_PORT: '5176' }
  }
};

const activeChildren = [];
let isShuttingDown = false;

// ── Process Cleanup Handler ──
function shutdown(exitCode = 0) {
  if (isShuttingDown) return;
  isShuttingDown = true;

  console.log('\n');
  log('system', 'Shutting down all development services...', 'warn');

  let remaining = activeChildren.length;
  if (remaining === 0) {
    log('system', 'Shutdown complete.');
    process.exit(exitCode);
  }

  for (const child of activeChildren) {
    if (child.pid) {
      log('system', `Stopping ${child.serviceName} (PID ${child.pid})...`);
      const port = CONFIG.ports[child.serviceKey];
      if (isWin) {
        exec(`taskkill /F /T /PID ${child.pid}`, (err, stdout, stderr) => {
          if (err) {
            log(child.serviceKey || 'system', `⚠️ Tree-kill note for ${child.serviceName} (PID ${child.pid}): ${stderr.trim() || err.message}`, 'warn');
          } else {
            log(child.serviceKey || 'system', `✅ Successfully terminated process tree for ${child.serviceName} (PID ${child.pid}).`);
          }
          const releasePromise = port ? waitForPortRelease(port) : Promise.resolve(true);
          releasePromise.then(() => {
            remaining--;
            if (remaining <= 0) {
              log('system', 'Shutdown complete.');
              process.exit(exitCode);
            }
          });
        });
      } else {
        try {
          process.kill(-child.pid, 'SIGINT');
        } catch (e) {
          try {
            child.kill('SIGINT');
          } catch (e2) {}
        }
        log(child.serviceKey || 'system', 'Stopped');
        const releasePromise = port ? waitForPortRelease(port) : Promise.resolve(true);
        releasePromise.then(() => {
          remaining--;
          if (remaining <= 0) {
            log('system', 'Shutdown complete.');
            process.exit(exitCode);
          }
        });
      }
    } else {
      remaining--;
      if (remaining <= 0) {
        log('system', 'Shutdown complete.');
        process.exit(exitCode);
      }
    }
  }

  // Fallback timeout for process termination
  setTimeout(() => {
    log('system', 'Force exit after timeout.');
    process.exit(exitCode);
  }, 4000);
}

process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));
process.on('exit', () => shutdown(0));

if (isWin) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout
  });
  rl.on('SIGINT', () => {
    process.emit('SIGINT');
  });
}

// ── Step 1: Environment Parsing & Validation ──
function parseDotEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;
  const content = fs.readFileSync(filePath, 'utf8');
  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const match = trimmed.match(/^([\w.\-]+)\s*=\s*(.*)$/);
    if (match) {
      const key = match[1];
      let val = match[2].trim();
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.substring(1, val.length - 1);
      }
      if (key !== 'PORT' && !process.env[key]) {
        process.env[key] = val;
      }
    }
  }
}

function auditEnvironment() {
  log('system', '==================================================');
  log('system', 'Phase 1: Environment & Tool Audit');
  log('system', '==================================================');

  // Parse all environment files
  parseDotEnvFile(path.join(rootDir, '.env'));
  parseDotEnvFile(path.join(CONFIG.directories.springboot, '.env'));
  parseDotEnvFile(path.join(CONFIG.directories.fastapi, '.env'));

  const checks = [
    {
      name: 'Supabase Database',
      configured: !!(process.env.SUPABASE_DB_HOST || process.env.DATABASE_URL)
    },
    {
      name: 'JWT Security Secret',
      configured: !!(process.env.SECRET_KEY && !process.env.SECRET_KEY.includes('change-me'))
    },
    {
      name: 'Google OAuth2',
      configured: !!(process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET)
    },
    {
      name: 'GitHub API & OAuth',
      configured: !!(process.env.GITHUB_TOKEN || process.env.GITHUB_PAT || process.env.GITHUB_CLIENT_ID)
    },
    {
      name: 'OpenRouter LLM',
      configured: !!(process.env.OPENROUTER_API_KEY)
    }
  ];

  for (const check of checks) {
    const status = check.configured ? `${colors.green}OK${colors.reset}` : `${colors.yellow}MISSING / DEFAULT${colors.reset}`;
    log('env', `${check.name.padEnd(25, '.')} ${status}`);
  }
}

// ── Step 2: Tool & Executable Verification ──
function verifyToolchain() {
  log('system', '--------------------------------------------------');
  log('system', 'Verifying required system executables...');

  // Check Node.js
  try {
    const nodeVer = execSync('node -v', { encoding: 'utf8' }).trim();
    log('system', `[CHECK] Node.js ........ OK (${nodeVer})`);
  } catch (e) {
    log('system', '[CHECK] Node.js ........ ❌ NOT FOUND', 'error');
    throw new Error('Node.js runtime is missing or not in PATH.');
  }

  // Check Python
  const pythonCmd = resolvePythonCmd();
  try {
    const pyVer = execSync(`"${pythonCmd}" --version`, { encoding: 'utf8' }).trim();
    log('system', `[CHECK] Python ......... OK (${pyVer}) [${path.basename(pythonCmd)}]`);
  } catch (e) {
    log('system', `[CHECK] Python ......... ❌ NOT FOUND at ${pythonCmd}`, 'error');
    throw new Error(`Python environment not found at ${pythonCmd}`);
  }

  // Check Java
  try {
    const javaVerOutput = execSync('java -version 2>&1', { encoding: 'utf8' });
    const javaVer = javaVerOutput.split('\n')[0] || 'Installed';
    log('system', `[CHECK] Java ........... OK (${javaVer.trim()})`);
  } catch (e) {
    log('system', '[CHECK] Java ........... ❌ NOT FOUND', 'error');
    throw new Error('Java Development Kit (JDK 17+) is missing or not in PATH.');
  }

  // Check Maven
  const mavenCmd = resolveMavenCmd();
  try {
    const mvnVerOutput = execSync(`"${mavenCmd}" -v`, { encoding: 'utf8' });
    const mvnVer = mvnVerOutput.split('\n')[0] || 'Installed';
    log('system', `[CHECK] Maven .......... OK (${mvnVer.trim()}) [${path.basename(mavenCmd)}]`);
  } catch (e) {
    log('system', `[CHECK] Maven .......... ❌ NOT FOUND at ${mavenCmd}`, 'error');
    throw new Error(`Apache Maven is missing or not reachable via ${mavenCmd}`);
  }
}

// ── Step 3: Database Connectivity Pre-Check ──
async function verifyDatabaseConnectivity() {
  log('system', '--------------------------------------------------');
  log('system', 'Testing Supabase database connectivity...');
  
  const dbHost = process.env.SUPABASE_DB_HOST || 'aws-0-ap-northeast-1.pooler.supabase.com';
  const dbPort = process.env.SUPABASE_DB_PORT || '5432';
  
  return new Promise((resolve) => {
    const socket = net.createConnection({
      host: dbHost,
      port: parseInt(dbPort),
      timeout: 10000
    });

    socket.on('connect', () => {
      socket.destroy();
      log('system', `✅ Database connectivity test passed: ${dbHost}:${dbPort}`);
      resolve(true);
    });

    socket.on('timeout', () => {
      socket.destroy();
      log('system', `⚠️ Database connectivity test failed: Connection timeout to ${dbHost}:${dbPort}`, 'warn');
      log('system', 'This may cause Spring Boot to fail. The application will attempt to start anyway.', 'warn');
      resolve(false);
    });

    socket.on('error', (err) => {
      log('system', `⚠️ Database connectivity test failed: ${err.message}`, 'warn');
      log('system', 'This may cause Spring Boot to fail. The application will attempt to start anyway.', 'warn');
      resolve(false);
    });
  });
}
// ── Step 4: XGBoost Model Verification ──
function verifyXGBoostModel() {
  log('system', '--------------------------------------------------');
  log('system', 'Verifying XGBoost ML model artifacts...');
  const modelExists = fs.existsSync(CONFIG.models.xgboost) || fs.existsSync(CONFIG.models.xgboostPkl);

  if (modelExists) {
    const activeModelPath = fs.existsSync(CONFIG.models.xgboost) ? CONFIG.models.xgboost : CONFIG.models.xgboostPkl;
    const stats = fs.statSync(activeModelPath);
    const sizeMb = (stats.size / (1024 * 1024)).toFixed(2);
    log('fastapi', `✅ XGBoost Model Artifact Found: ${path.basename(activeModelPath)} (${sizeMb} MB)`);
  } else {
    log('fastapi', `⚠️ XGBoost model file not found at ${CONFIG.models.xgboost}`, 'warn');
    log('fastapi', 'Note: FastAPI engine will perform automatic auto-recovery training on startup.', 'warn');
  }
}

// ── Step 5: Port Conflict Checks & Auto-Release ──
function checkPortInUse(port) {
  return new Promise((resolve) => {
    const server = http.createServer();
    server.listen(port, '0.0.0.0', () => {
      server.close(() => {
        const localServer = http.createServer();
        localServer.listen(port, '127.0.0.1', () => {
          localServer.close(() => resolve(false));
        });
        localServer.on('error', () => resolve(true));
      });
    });
    server.on('error', () => resolve(true));
  });
}

function forceKillPort(port) {
  return new Promise((resolve) => {
    if (!isWin) {
      exec(`fuser -k ${port}/tcp`, () => resolve(true));
      return;
    }
    exec(`netstat -ano | findstr :${port}`, (err, stdout) => {
      if (!stdout) return resolve(true);
      const lines = stdout.trim().split('\n');
      const pids = new Set();
      for (const line of lines) {
        const parts = line.trim().split(/\s+/);
        const localAddr = parts[1];
        if (localAddr && (localAddr.endsWith(`:${port}`) || localAddr.endsWith(`[::]:${port}`))) {
          const pid = parts[parts.length - 1];
          if (pid && !isNaN(pid) && pid !== '0') {
            pids.add(pid);
          }
        }
      }
      if (pids.size === 0) return resolve(true);

      let killed = 0;
      for (const pid of pids) {
        log('system', `Releasing port ${port} occupied by PID ${pid}...`, 'warn');
        exec(`taskkill /F /T /PID ${pid}`, (err, stdout, stderr) => {
          if (err) {
            log('system', `⚠️ Tree-kill port release note for PID ${pid}: ${stderr.trim() || err.message}`, 'warn');
          } else {
            log('system', `✅ Successfully terminated process tree PID ${pid} occupying port ${port}.`);
          }
          killed++;
          if (killed === pids.size) resolve(true);
        });
      }
    });
  });
}

function waitForPortRelease(port, timeoutMs = 3000) {
  return new Promise((resolve) => {
    const startTime = Date.now();
    const check = () => {
      exec(`netstat -ano | findstr :${port}`, (err, stdout) => {
        if (!stdout || !stdout.includes('LISTENING')) {
          log('system', `✅ Port ${port} confirmed free.`);
          resolve(true);
          return;
        }
        if (Date.now() - startTime > timeoutMs) {
          log('system', `⚠️ Port ${port} still occupied after ${timeoutMs / 1000}s wait.`, 'warn');
          resolve(false);
          return;
        }
        setTimeout(check, 200);
      });
    };
    setTimeout(check, 200);
  });
}

function getPidsOnPort(port) {
  return new Promise((resolve) => {
    if (!isWin) return resolve([]);
    exec(`netstat -ano | findstr :${port}`, (err, stdout) => {
      if (!stdout) return resolve([]);
      const lines = stdout.trim().split('\n');
      const pids = new Set();
      for (const line of lines) {
        const parts = line.trim().split(/\s+/);
        const localAddr = parts[1];
        const state = parts[parts.length - 2];
        if (localAddr && (localAddr.endsWith(`:${port}`) || localAddr.endsWith(`[::]:${port}`)) && (state === 'LISTENING' || !state)) {
          const pid = parts[parts.length - 1];
          if (pid && !isNaN(pid) && pid !== '0') {
            pids.add(pid);
          }
        }
      }
      resolve(Array.from(pids));
    });
  });
}

async function auditPorts() {
  log('system', '==================================================');
  log('system', 'Phase 2: Port Availability Audit');
  log('system', '==================================================');

  for (const [serviceKey, port] of Object.entries(CONFIG.ports)) {
    const pids = await getPidsOnPort(port);
    const inUse = pids.length > 0 || await checkPortInUse(port);
    if (inUse || pids.length > 0) {
      log('system', `⚠️ Port ${port} (${SERVICES[serviceKey].name}) is currently in use!`, 'warn');
      log('system', `Attempting to automatically release port ${port}...`, 'warn');
      await forceKillPort(port);
      await waitForPortRelease(port);
    } else {
      log('system', `✅ Port ${port} (${SERVICES[serviceKey].name}) is free.`);
    }
  }
}

// ── Step 7: Service Spawning with Line-Buffered Stream Output ──
function spawnService(key, config) {
  return new Promise((resolve, reject) => {
    let spawnCmd = config.command;
    let spawnArgs = [...config.args];
    let useShell = config.useShell;

    if (isWin && (spawnCmd.toLowerCase().endsWith('.cmd') || spawnCmd.toLowerCase().endsWith('.bat') || spawnCmd.includes(' '))) {
      spawnCmd = `"${spawnCmd}" ${spawnArgs.join(' ')}`;
      spawnArgs = [];
      useShell = true;
    }

    log('system', `Launching ${config.name}...`);
    log('system', `Directory: ${config.cwd}`);
    log('system', `Command:   ${config.command} ${config.args.join(' ')}`);

    const serviceEnv = { ...process.env, ...(config.env || {}) };

    const child = spawn(spawnCmd, spawnArgs, {
      cwd: config.cwd,
      detached: false,
      shell: useShell,
      env: serviceEnv
    });

    child.serviceKey = key;
    child.serviceName = config.name;
    child.recentLogs = [];
    child.isReady = false;
    child.hasExited = false;
    child.exitCode = null;

    activeChildren.push(child);

    // Line-buffered stdout stream listener
    let stdoutBuffer = '';
    child.stdout.on('data', (data) => {
      stdoutBuffer += data.toString();
      const lines = stdoutBuffer.split('\n');
      stdoutBuffer = lines.pop(); // Keep partial line in buffer
      for (const line of lines) {
        if (line.trim().length > 0) {
          log(key, line);
          child.recentLogs.push(line);
          if (child.recentLogs.length > 100) child.recentLogs.shift();
        }
      }
    });

    // Line-buffered stderr stream listener
    let stderrBuffer = '';
    child.stderr.on('data', (data) => {
      stderrBuffer += data.toString();
      const lines = stderrBuffer.split('\n');
      stderrBuffer = lines.pop(); // Keep partial line in buffer
      for (const line of lines) {
        if (line.trim().length > 0) {
          log(key, line, 'error');
          child.recentLogs.push(line);
          if (child.recentLogs.length > 100) child.recentLogs.shift();
        }
      }
    });

    child.on('exit', (code, signal) => {
      child.hasExited = true;
      child.exitCode = code !== null ? code : signal;
      if (!isShuttingDown) {
        if (child.isReady) {
          log(key, `❌ ${config.name} process exited unexpectedly after becoming READY with code/signal: ${child.exitCode}`, 'error');
        } else {
          log(key, `❌ ${config.name} process exited unexpectedly before becoming READY with code/signal: ${child.exitCode}`, 'error');
        }
      }
    });

    // Give process 1.5 seconds to ensure it didn't fail immediately on spawn
    setTimeout(() => {
      if (child.hasExited) {
        const errorReport = formatStartupFailure(config.name, `${config.command} ${config.args.join(' ')}`, config.cwd, child.exitCode, child.recentLogs);
        reject(new Error(errorReport));
      } else {
        resolve(child);
      }
    }, 1500);
  });
}

function formatStartupFailure(serviceName, command, cwd, exitCode, logs) {
  const logSnippet = logs.slice(-25).join('\n').trim();

  let rootCause = 'Unknown error during process execution.';
  const fullLog = logs.join('\n');

  if (fullLog.includes('Port already in use') || fullLog.includes('Address already in use')) {
    rootCause = 'Port Conflict: Target TCP port is already in use by another process.';
  } else if (fullLog.includes('ClassNotFoundException')) {
    const match = fullLog.match(/ClassNotFoundException: ([^\s\r\n]+)/);
    rootCause = `Missing Java Class: ${match ? match[1] : 'ClassNotFoundException'}`;
  } else if (fullLog.includes('BeanCreationException')) {
    rootCause = 'Spring Bean Creation Exception: Failed to instantiate component/service bean.';
  } else if (fullLog.includes('HikariPool') && fullLog.includes('Connection is not available')) {
    rootCause = 'Database Connection Pool Exhaustion: HikariCP unable to establish database connections. Check network connectivity to Supabase.';
  } else if (fullLog.includes('SQLException') || fullLog.includes('Connection refused') || fullLog.includes('timeout')) {
    rootCause = 'Database Connection Failure: Unable to connect to database or connection pool initialization failed. Verify Supabase credentials and network connectivity.';
  } else if (fullLog.includes('ModuleNotFoundError')) {
    rootCause = 'Python Dependency Missing: ModuleNotFoundError encountered during FastAPI startup.';
  } else if (fullLog.includes('Unable to open JDBC Connection')) {
    rootCause = 'JDBC Connection Error: Database connection establishment failed. Check Supabase database host, credentials, and SSL configuration.';
  }

  return `
==================================================
❌ SERVICE STARTUP FAILURE
==================================================

Service:    ${serviceName}
Command:    ${command}
Directory:  ${cwd}
Exit Code:  ${exitCode !== null ? exitCode : 'Unknown / Signal Killed'}
Root Cause: ${rootCause}

Recent Console / Diagnostic Output:
--------------------------------------------------
${logSnippet || '(No output recorded)'}

TROUBLESHOOTING TIPS:
--------------------------------------------------
${getTroubleshootingTips(rootCause)}
==================================================`;
}

function getTroubleshootingTips(rootCause) {
  if (rootCause.includes('Database Connection')) {
    return `• Verify Supabase database is running and accessible
• Check SUPABASE_DB_HOST, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD environment variables
• Test network connectivity: telnet aws-0-ap-northeast-1.pooler.supabase.com 5432
• Ensure SSL mode is correctly configured (sslmode=require)
• Try increasing connection timeout values in HikariCP configuration`;
  } else if (rootCause.includes('Port Conflict')) {
    return `• Kill existing processes on the target port: taskkill /F /PID <pid>
• Use netstat -ano | findstr :PORT to find conflicting processes
• The dev-runner should auto-release ports but may need manual intervention`;
  } else if (rootCause.includes('Spring Bean Creation')) {
    return `• Check Spring Boot application.properties for configuration errors
• Verify all required dependencies are in classpath
• Look for missing @Component, @Service, or @Repository annotations
• Check for circular dependency issues between Spring beans`;
  }
  return `• Review the full console output above for specific error details
• Check environment variables and configuration files
• Verify all required dependencies are installed and accessible`;
}

// ── Step 6: Health Polling with Exponential Backoff ──
function pollHealth(child, healthUrl, timeoutMs = 120000) {
  return new Promise((resolve, reject) => {
    const startTime = Date.now();
    let attempts = 0;
    let backoffMs = 2000; // Start with 2 second intervals
    let interval = null;

    const pollCheck = () => {
      attempts++;

      // Case A: Process exited before becoming healthy
      if (child.hasExited) {
        if (interval) clearInterval(interval);
        const report = formatStartupFailure(
          child.serviceName,
          `${SERVICES[child.serviceKey].command} ${SERVICES[child.serviceKey].args.join(' ')}`,
          SERVICES[child.serviceKey].cwd,
          child.exitCode,
          child.recentLogs
        );
        reject(new Error(`${child.serviceName} process exited before becoming healthy.\nExit code: ${child.exitCode}\n${report}`));
        return;
      }

      const elapsedSec = Math.floor((Date.now() - startTime) / 1000);
      
      // More frequent progress updates for Spring Boot (which takes longer)
      const isSpringBoot = child.serviceKey === 'springboot';
      const progressInterval = isSpringBoot ? 3 : 5;
      
      if (attempts % progressInterval === 0) {
        log('system', `Polling ${child.serviceName} at ${healthUrl} (${elapsedSec}s elapsed, attempt ${attempts})...`);
      }

      // Case B: Process is alive but health check timed out
      if (Date.now() - startTime > timeoutMs) {
        if (interval) clearInterval(interval);
        reject(new Error(`${child.serviceName} process is alive but health endpoint did not become ready within configured timeout (${timeoutMs / 1000}s).\nTarget: ${healthUrl}\nLast 10 log lines:\n${child.recentLogs.slice(-10).join('\n')}`));
        return;
      }

      // Case C: Endpoint responds successfully
      const request = http.get(healthUrl, { timeout: 5000 }, (res) => {
        if (res.statusCode >= 200 && res.statusCode < 400) {
          if (interval) clearInterval(interval);
          child.isReady = true;
          log('system', `✅ [HEALTH PASSED] ${child.serviceName} responded HTTP ${res.statusCode} at ${healthUrl} (attempt ${attempts}, ${elapsedSec}s)`);
          resolve(true);
        }
      });

      request.on('timeout', () => {
        request.destroy();
        // Continue polling - this is expected during startup
      });

      request.on('error', (err) => {
        // Continue polling - connection refused is expected during startup
        if (attempts % 10 === 0 && err.code !== 'ECONNREFUSED') {
          log(child.serviceKey, `Health check error (attempt ${attempts}): ${err.message}`, 'warn');
        }
      });

      // Exponential backoff for Spring Boot after many attempts
      if (isSpringBoot && attempts > 20) {
        if (interval) clearInterval(interval);
        backoffMs = Math.min(backoffMs * 1.2, 10000); // Cap at 10s
        setTimeout(() => {
          interval = setInterval(pollCheck, backoffMs);
        }, backoffMs);
      }
    };

    interval = setInterval(pollCheck, backoffMs);
  });
}

// ── Open Browser Helper ──
function openBrowser(url) {
  log('system', `Opening default browser to ${url}...`);
  try {
    if (isWin) {
      spawn('cmd', ['/c', 'start', '', url], { shell: false, detached: true, stdio: 'ignore' }).unref();
    } else if (process.platform === 'darwin') {
      spawn('open', [url], { detached: true, stdio: 'ignore' }).unref();
    } else {
      spawn('xdg-open', [url], { detached: true, stdio: 'ignore' }).unref();
    }
  } catch (e) {
    log('system', `⚠️ Failed to open browser automatically: ${e.message}`, 'warn');
  }
}

// ── Main Execution Workflow ──
async function main() {
  console.log('\n');
  log('system', '====================================================');
  log('system', ' RiskVision AI — Integrated Development Runner');
  log('system', '====================================================');

  try {
    auditEnvironment();
    verifyToolchain();
    await verifyDatabaseConnectivity();
    verifyXGBoostModel();
    await auditPorts();

    log('system', '====================================================');
    log('system', 'Phase 3: Service Startup Sequence (Concurrent)');
    log('system', '====================================================');

    // 1. Start FastAPI Prediction Engine (Non-blocking)
    const fastapiPromise = (async () => {
      try {
        log('system', '[1/3] Launching FastAPI ML Engine (:8000)...');
        const fastapiChild = await spawnService('fastapi', SERVICES.fastapi);
        log('system', 'Waiting for FastAPI prediction engine to become healthy...');
        await pollHealth(fastapiChild, SERVICES.fastapi.healthCheck, SERVICES.fastapi.timeoutMs);
        log('fastapi', `HEALTH PASSED — ${SERVICES.fastapi.name} ready on http://localhost:${CONFIG.ports.fastapi}`);
      } catch (err) {
        log('fastapi', `❌ Prediction engine failed to start:\n${err.message}`, 'error');
        shutdown(1);
      }
    })();

    // 2. Start Spring Boot Backend (Non-blocking)
    const springbootPromise = (async () => {
      try {
        log('system', '[2/3] Launching Spring Boot Backend (:8080)...');
        const springbootChild = await spawnService('springboot', SERVICES.springboot);
        log('system', 'Waiting for Spring Boot backend to complete initialization...');
        await pollHealth(springbootChild, SERVICES.springboot.healthCheck, SERVICES.springboot.timeoutMs);
        log('springboot', `HEALTH PASSED — ${SERVICES.springboot.name} ready on http://localhost:${CONFIG.ports.springboot}`);
      } catch (err) {
        log('springboot', `❌ Spring Boot backend failed to start:\n${err.message}`, 'error');
        shutdown(1);
      }
    })();

    // 3. Start React/Vite Frontend (Non-blocking, with EADDRINUSE retry)
    const frontendPromise = (async () => {
      const maxViteRetries = 3;
      let frontendChild = null;
      for (let viteAttempt = 1; viteAttempt <= maxViteRetries; viteAttempt++) {
        try {
          log('system', `[3/3] Launching Vite Frontend (:${CONFIG.ports.frontend})... (attempt ${viteAttempt}/${maxViteRetries})`);
          frontendChild = await spawnService('frontend', SERVICES.frontend);
          log('system', 'Waiting for Vite frontend server...');
          await pollHealth(frontendChild, SERVICES.frontend.healthCheck, SERVICES.frontend.timeoutMs);
          log('frontend', `HEALTH PASSED — ${SERVICES.frontend.name} ready on http://localhost:${CONFIG.ports.frontend}`);
          
          // Open browser at the landing page root — js/app.js handles routing to #/login
          const appUrl = `http://localhost:${CONFIG.ports.frontend}/`;
          openBrowser(appUrl);
          break; // Success — exit retry loop
        } catch (viteErr) {
          const isPortConflict = viteErr.message && (viteErr.message.includes('Port') || viteErr.message.includes('EADDRINUSE') || viteErr.message.includes('already in use'));
          if (isPortConflict && viteAttempt < maxViteRetries) {
            log('system', `⚠️ Vite failed on port ${CONFIG.ports.frontend} (attempt ${viteAttempt}/${maxViteRetries}). Re-releasing port and retrying...`, 'warn');
            const idx = activeChildren.indexOf(frontendChild);
            if (idx !== -1) activeChildren.splice(idx, 1);
            await forceKillPort(CONFIG.ports.frontend);
            await waitForPortRelease(CONFIG.ports.frontend);
          } else {
            log('frontend', `❌ React/Vite frontend failed to start:\n${viteErr.message}`, 'error');
            shutdown(1);
          }
        }
      }
    })();

    log('system', '====================================================');
    log('system', ' ALL SERVICES INITIATED');
    log('system', '====================================================');
    log('system', ' Services are starting up concurrently.');
    log('system', ' Press Ctrl+C to stop all services cleanly.');
    log('system', '====================================================');

  } catch (err) {
    log('system', '\n❌ CRITICAL STARTUP FAILURE:', 'error');
    console.error(err.message);
    shutdown(1);
  }
}

main();
