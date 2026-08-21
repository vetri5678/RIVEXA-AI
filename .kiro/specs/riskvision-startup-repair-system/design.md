# RiskVision AI Startup Repair System Technical Design

## Overview

This document provides a comprehensive technical design for the RiskVision AI startup repair system, documenting the current working architecture that successfully resolves critical issues with database connectivity, port conflicts, and process lifecycle management. The system orchestrates three primary services (Frontend, Spring Boot Backend, FastAPI ML Engine) through an intelligent development runner that ensures reliable startup, health monitoring, and graceful shutdown.

The repair system addresses the core challenges of multi-service startup coordination, database connection pool management, and port conflict resolution that were previously causing system failures. The design emphasizes robustness, observability, and automated recovery mechanisms.

## Glossary

- **Dev-Runner**: The central orchestration process (dev-runner.js) that manages the lifecycle of all three services
- **Health Polling**: Systematic endpoint checking with exponential backoff to verify service readiness
- **HikariCP**: High-performance JDBC connection pool used by Spring Boot for database connections
- **Port Conflict Resolution**: Automated detection and termination of processes occupying required ports
- **Service Lifecycle Management**: The complete process of spawning, monitoring, and shutting down services
- **Database Pre-Connectivity Check**: Network-level validation of database accessibility before service startup
- **Auto-Recovery Training**: FastAPI's capability to automatically train ML models when artifacts are missing
- **Process Group Management**: Hierarchical process termination to ensure clean shutdown of all child processes

## Current Working Architecture

### System Overview

The RiskVision AI platform operates as a distributed system with three interconnected services:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │  Spring Boot    │    │   FastAPI       │
│   (React/Vite)  │───▶│   Backend       │───▶│   ML Engine     │
│   Port: 5176    │    │   Port: 8080    │    │   Port: 8000    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   Supabase      │
                    │   PostgreSQL    │
                    │   Port: 5432    │
                    └─────────────────┘
```

### Key Components and Interactions

#### 1. Development Runner (dev-runner.js)

**Purpose**: Central orchestration and lifecycle management

**Key Responsibilities**:
- Environment variable parsing and validation
- System tool verification (Node.js, Python, Java, Maven)
- Database connectivity pre-checking
- Port conflict detection and automatic resolution
- Sequential service startup with dependency management
- Health endpoint polling with exponential backoff
- Graceful shutdown with process group termination

**Architecture Pattern**: Command and Control with Observer Pattern for health monitoring

#### 2. Frontend Service (React/Vite)

**Configuration**:
```javascript
// vite.config.js
server: {
  port: 5176,
  strictPort: true,
  proxy: {
    '/api/v1/pipeline': 'http://localhost:8000', // FastAPI routes
    '/api': 'http://localhost:8080',            // Spring Boot routes
    '/oauth2': 'http://localhost:8080'          // OAuth routes
  }
}
```

**Interaction Patterns**:
- Reverse proxy routing to backend services
- Static asset serving with Vite development server
- Hot module replacement for development efficiency

#### 3. Spring Boot Backend Service

**Database Configuration**:
```properties
# HikariCP Connection Pool (Optimized for Reliability)
spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=1
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.initialization-fail-timeout=60000
spring.datasource.hikari.leak-detection-threshold=300000
```

**Key Features**:
- OAuth2 authentication with Google/GitHub providers
- RESTful API endpoints for application logic
- JPA/Hibernate for database operations
- JWT token management with shared secret
- Comprehensive health endpoints (`/api/v1/health`)

#### 4. FastAPI ML Engine Service

**Capabilities**:
- XGBoost model training and prediction
- Automatic model artifact discovery
- Auto-recovery training when models are missing
- Pipeline orchestration for 12-stage ML workflow
- Health and readiness endpoints

**Startup Sequence**:
1. Database initialization and admin user bootstrap
2. ML model artifact discovery
3. Auto-recovery training if no valid models found
4. Service readiness confirmation

## Process Lifecycle Management

### Startup Sequence

The dev-runner follows a strict 5-phase startup process:

#### Phase 1: Environment & Tool Audit
```javascript
auditEnvironment() {
  // Parse .env files from all service directories
  // Validate critical environment variables
  // Report configuration status
}

verifyToolchain() {
  // Verify Node.js, Python, Java, Maven availability
  // Check version compatibility
  // Validate executable paths
}
```

#### Phase 2: Pre-Connectivity Validation
```javascript
async verifyDatabaseConnectivity() {
  // TCP socket connection test to Supabase
  // 10-second timeout with graceful fallback
  // Warning generation for potential issues
}

verifyXGBoostModel() {
  // Check for trained model artifacts
  // Report model file size and status
  // Prepare for auto-recovery if needed
}
```

#### Phase 3: Port Conflict Resolution
```javascript
async auditPorts() {
  // Check ports 5176, 8080, 8000 for availability
  // Automatic process termination for conflicts
  // Graceful waiting for port release
}
```

#### Phase 4: Sequential Service Launch
```javascript
// Dependency-aware startup order:
// 1. FastAPI (ML Engine) - Port 8000
// 2. Spring Boot (Backend) - Port 8080  
// 3. React/Vite (Frontend) - Port 5176
```

#### Phase 5: Health Validation
```javascript
async pollHealth(child, healthUrl, timeoutMs) {
  // Exponential backoff polling
  // Process exit detection
  // Timeout management with detailed diagnostics
}
```

### Health Check Strategies

#### Health Endpoint Design

Each service exposes standardized health endpoints:

**FastAPI Health Response**:
```json
{
  "status": "ok",
  "service": "FastAPI Prediction Engine", 
  "model": "XGBoost",
  "modelLoaded": true
}
```

**Spring Boot Health Response**:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

#### Health Polling Algorithm

```javascript
const interval = setInterval(() => {
  // Case A: Process exited before becoming healthy
  if (child.hasExited) {
    reject(new Error(`Process exited: ${child.exitCode}`));
    return;
  }
  
  // Case B: Timeout exceeded
  if (Date.now() - startTime > timeoutMs) {
    reject(new Error(`Health check timeout`));
    return;
  }
  
  // Case C: Successful health response
  http.get(healthUrl, (res) => {
    if (res.statusCode >= 200 && res.statusCode < 400) {
      resolve(true);
    }
  });
}, backoffMs);
```

## Error Handling and Recovery Mechanisms

### Database Connection Management

#### HikariCP Configuration Strategy

The system uses conservative HikariCP settings optimized for reliability:

```properties
# Connection Pool Settings (Reliability-First)
spring.datasource.hikari.maximum-pool-size=5          # Conservative pool size
spring.datasource.hikari.minimum-idle=1               # Always maintain 1 connection
spring.datasource.hikari.connection-timeout=30000     # 30s connection timeout
spring.datasource.hikari.idle-timeout=600000          # 10m idle timeout
spring.datasource.hikari.max-lifetime=1800000         # 30m max connection lifetime
spring.datasource.hikari.keepalive-time=60000         # 1m keepalive pings
spring.datasource.hikari.leak-detection-threshold=300000  # 5m leak detection
```

#### Connection String Optimization

```properties
spring.datasource.url=jdbc:postgresql://${SUPABASE_DB_HOST}:${SUPABASE_DB_PORT}/${SUPABASE_DB_NAME}?sslmode=require&connectTimeout=30&socketTimeout=30&tcpKeepAlive=true&ApplicationName=RiskVision-SpringBoot
```

**Key Parameters**:
- `sslmode=require`: Enforced SSL for Supabase
- `connectTimeout=30`: TCP connection timeout
- `socketTimeout=30`: Socket read/write timeout  
- `tcpKeepAlive=true`: OS-level keepalive
- `ApplicationName`: Connection identification

### Port Conflict Resolution

#### Automatic Process Detection and Termination

```javascript
function forceKillPort(port) {
  // Windows-specific implementation
  exec(`netstat -ano | findstr :${port}`, (err, stdout) => {
    // Parse netstat output to find PIDs
    // Extract process IDs listening on target port
    // Terminate using taskkill /F /T /PID
  });
}
```

#### Fallback Port Selection

The system maintains port preference but can adapt:
- Primary ports: 5176 (Frontend), 8080 (Spring Boot), 8000 (FastAPI)
- Automatic conflict detection before service start
- Graceful termination of conflicting processes
- Configuration synchronization across service configs

### ML Model Auto-Recovery

#### Model Artifact Discovery

```python
def try_load_latest_artifacts():
    # Search for XGBoost model files (.joblib, .pkl)
    # Load preprocessing transformers
    # Validate model compatibility
    # Update global state with loaded artifacts
```

#### Auto-Recovery Training

```python
def auto_recover_training():
    # Triggered when no valid model artifacts found
    # Uses bundled training dataset
    # Executes full 9-stage training pipeline
    # Saves model artifacts for future use
    # Reports training completion status
```

## Configuration Management

### Environment Variable Hierarchy

The system loads configuration from multiple sources with precedence:

1. **System Environment Variables** (highest priority)
2. **Service-Specific .env Files**
3. **Default Values in Configuration Files**

#### Configuration File Locations

```
├── .env                                    # Root environment
├── stitch_riskvision_ai_intelligence_platform/
│   ├── riskvision_ai_springboot_backend/
│   │   └── .env                           # Spring Boot config
│   └── riskvision_ai_backend/
│       └── .env                           # FastAPI config
```

### Critical Configuration Parameters

#### Database Configuration
```bash
SUPABASE_DB_HOST=aws-0-ap-northeast-1.pooler.supabase.com
SUPABASE_DB_PORT=5432
SUPABASE_DB_NAME=postgres
SUPABASE_DB_USER=postgres.xyz
SUPABASE_DB_PASSWORD=your_secure_password
```

#### Security Configuration
```bash
SECRET_KEY=64_character_random_hex_string
JWT_EXPIRE_LENGTH=1800000                  # 30 minutes
JWT_REFRESH_EXPIRE_LENGTH=604800000        # 7 days
```

#### OAuth2 Configuration
```bash
GOOGLE_CLIENT_ID=your_google_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_google_client_secret
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret
```

### Service Discovery and Communication

#### Internal Service URLs

Services communicate using localhost URLs with environment variable overrides:

```javascript
// Frontend (vite.config.js)
proxy: {
  '/api/v1/pipeline': process.env.VITE_PYTHON_BACKEND_URL || 'http://localhost:8000',
  '/api': process.env.VITE_SPRINGBOOT_URL || 'http://localhost:8080'
}
```

```properties
# Spring Boot (application.properties)
ml.service.url=${ML_SERVICE_URL:http://localhost:8000}
app.frontend.url=${FRONTEND_URL:http://localhost:5176}
```

## Process Monitoring and Observability

### Structured Logging

#### Log Format Standardization

```javascript
function log(service, message, type = 'info') {
  const colors = {
    system: '\x1b[35m',     // Magenta
    springboot: '\x1b[32m', // Green  
    fastapi: '\x1b[36m',    // Cyan
    frontend: '\x1b[34m'    // Blue
  };
  console.log(`${colors[service]}[${service.toUpperCase()}]${colors.reset} ${message}`);
}
```

#### Health Check Progress Reporting

```javascript
// Adaptive progress reporting based on service type
const isSpringBoot = child.serviceKey === 'springboot';
const progressInterval = isSpringBoot ? 3 : 5; // More frequent for slower services

if (attempts % progressInterval === 0) {
  log('system', `Polling ${child.serviceName} at ${healthUrl} (${elapsedSec}s elapsed, attempt ${attempts})`);
}
```

### Error Diagnostics

#### Startup Failure Analysis

```javascript
function formatStartupFailure(serviceName, command, cwd, exitCode, logs) {
  // Analyze log patterns to identify root causes:
  // - Port conflicts
  // - Database connection failures
  // - Missing dependencies
  // - Configuration errors
  
  // Provide targeted troubleshooting guidance
  return detailed_diagnostic_report;
}
```

#### Root Cause Detection Patterns

The system recognizes common failure patterns:

1. **Database Connection Issues**:
   - `HikariPool` + `Connection is not available`
   - `SQLException` or `Connection refused`
   - `Unable to open JDBC Connection`

2. **Port Conflicts**:
   - `Port already in use`
   - `Address already in use`

3. **Dependency Issues**:
   - `ClassNotFoundException`
   - `ModuleNotFoundError`
   - `BeanCreationException`

### Performance Metrics

#### Startup Time Optimization

- **FastAPI Engine**: ~15-30 seconds (includes model loading)
- **Spring Boot Backend**: ~45-90 seconds (includes database migration)
- **Frontend Server**: ~5-10 seconds (Vite dev server)

#### Resource Utilization

- **Database Connections**: Max 5 concurrent (HikariCP pool)
- **Memory Usage**: ~2GB total across all services
- **CPU Usage**: Moderate during startup, low during operation

## Security Considerations

### Database Security

```properties
# SSL enforcement for Supabase connection
spring.datasource.url=...?sslmode=require

# Connection pool leak detection
spring.datasource.hikari.leak-detection-threshold=300000
```

### Authentication and Authorization

#### JWT Token Management
- Shared secret between Spring Boot and FastAPI
- 30-minute access token expiry
- 7-day refresh token expiry
- Secure HTTP-only cookie storage

#### OAuth2 Integration
- Google OAuth2 for user authentication
- GitHub OAuth2 for repository access
- PKCE flow for mobile/SPA security

### CORS Configuration

```properties
# Restrictive CORS policy - no wildcards
spring.web.cors.allowed-origins=http://localhost:5176,http://127.0.0.1:5176,http://localhost:8080
```

## Deployment and Maintenance

### Development Environment Setup

#### Prerequisites Verification

The dev-runner automatically verifies:
- Node.js 18+ with npm/yarn
- Python 3.9+ with virtual environment
- Java 17+ JDK  
- Apache Maven 3.6+
- Network connectivity to Supabase

#### Service Dependencies

```bash
# Installation order
npm install                           # Frontend dependencies
pip install -r requirements.txt      # Python ML dependencies  
mvn clean compile                     # Java backend dependencies
```

### Production Considerations

#### Database Connection Pooling

For production deployment, consider:
- Increased HikariCP pool size (10-20 connections)
- Connection validation queries
- Distributed connection pooling with PgBouncer
- Read replica configuration for analytics queries

#### Service Discovery

Production architecture should implement:
- Container orchestration (Docker/Kubernetes)
- Service mesh for inter-service communication
- Health check integration with load balancers
- Circuit breaker patterns for service resilience

#### Configuration Management

- Kubernetes ConfigMaps for non-sensitive configuration
- Kubernetes Secrets for database credentials and JWT keys
- Environment-specific configuration overlays
- Automated configuration validation

## Future Enhancements

### Monitoring and Alerting

1. **Metrics Collection**: Prometheus metrics for all services
2. **Distributed Tracing**: OpenTelemetry for request tracing
3. **Log Aggregation**: ELK stack for centralized logging
4. **Health Dashboard**: Real-time service status monitoring

### Scalability Improvements

1. **Horizontal Scaling**: FastAPI and Spring Boot service replication
2. **Database Sharding**: Partition strategy for large datasets
3. **Caching Layer**: Redis for session and API response caching
4. **CDN Integration**: Static asset distribution optimization

### Reliability Enhancements

1. **Circuit Breaker Pattern**: Hystrix integration for service calls
2. **Retry Mechanisms**: Exponential backoff for transient failures
3. **Graceful Degradation**: Fallback modes for service unavailability
4. **Automated Recovery**: Self-healing mechanisms for common failures

## Conclusion

The RiskVision AI startup repair system successfully addresses the critical challenges of multi-service coordination through intelligent orchestration, robust error handling, and automated recovery mechanisms. The system demonstrates production-ready patterns for database connection management, health monitoring, and process lifecycle management while maintaining development efficiency and observability.

The architecture emphasizes reliability and maintainability, providing a solid foundation for the RiskVision AI platform's continued development and eventual production deployment.