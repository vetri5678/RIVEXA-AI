# RiskVision AI Startup Reliability Bugfix

## 1. Bug Condition

### 1.1 Defect Description
**Current Behavior**: When running `npm run dev`, the RiskVision AI platform experiences startup failures where Spring Boot and/or Vite have startup errors or process-management issues, causing the entire application to eventually shut down instead of maintaining all services in a running state.

**Observable Symptoms**:
- FastAPI starts successfully 
- Spring Boot or Vite encounter startup/process-management errors
- Application shuts down instead of keeping services running
- Multi-service orchestration fails to maintain stable state
- Frontend URL (http://localhost:5176) becomes inaccessible

### 1.2 Bug Condition (C(X))
```pseudocode
function isBugCondition(startupAttempt):
    return (
        startupAttempt.command == "npm run dev" AND
        (
            startupAttempt.springBootStartup == FAILED OR
            startupAttempt.viteStartup == FAILED OR
            startupAttempt.processManagement == UNSTABLE OR
            startupAttempt.healthChecks == FAILING OR
            startupAttempt.portConflicts == TRUE OR
            startupAttempt.shutdownOccurred == TRUE
        )
    )
```

**Triggering Conditions**:
- Command: `npm run dev`
- Environment: Windows development setup
- Required services: FastAPI (8000), Spring Boot (8080), Vite (5176)
- Process orchestration through dev-runner.js

## 2. Expected Behavior

### 2.1 Correct Startup Sequence
**Should Happen**: All three services start successfully and remain running in the correct order with proper health validation.

### 2.2 Expected Behavior Properties (P(result))
```pseudocode
function expectedBehavior(startupResult):
    return (
        startupResult.fastApiStatus == HEALTHY_200 AND
        startupResult.springBootStatus == READY AND
        startupResult.viteStatus == RUNNING_ON_5176 AND
        startupResult.frontendLoads == TRUE AND
        startupResult.apisWork == TRUE AND
        startupResult.processesStayRunning == TRUE AND
        startupResult.cleanShutdownOnCtrlC == TRUE AND
        startupResult.userFacingUrl == "http://localhost:5176" AND
        startupResult.noUnrelatedProcessKilling == TRUE AND
        startupResult.noHiddenErrors == TRUE AND
        startupResult.noFakeHealthEndpoints == TRUE AND
        startupResult.noSilentPortChanges == TRUE
    )
```

### 2.3 Startup Order Requirements
1. **FastAPI** starts and reaches HEALTH 200 status
2. **Health check** validates FastAPI availability  
3. **Spring Boot** starts and reaches READY status
4. **Health check** validates Spring Boot availability
5. **Vite** starts and serves frontend on port 5176
6. **All services remain running** until CTRL+C signal
7. **Clean shutdown** terminates all services gracefully

### 2.4 Error Handling Requirements
- `sun.misc.Unsafe` warnings should NOT be treated as fatal errors unless actually causing Spring Boot failure
- Real errors must be surfaced and diagnosed, not hidden
- Health endpoints must be genuine, not fake/mocked
- Port conflicts must be resolved without killing unrelated processes
- Silent port changes are not acceptable

## 3. Preservation Requirements

### 3.1 Non-Buggy Behavior to Preserve
**Preserve When**: Startup attempts that don't involve the multi-service orchestration scenario

```pseudocode
function preservationCondition(operation):
    return NOT (
        operation.type == MULTI_SERVICE_STARTUP AND
        operation.orchestrator == DEV_RUNNER_JS
    )
```

### 3.2 Behaviors to Maintain
- **Individual service startup**: Each service should still start correctly when launched independently
- **Configuration integrity**: Existing application.properties, package.json, and vite.config.js settings should remain functional
- **Environment variable handling**: Current .env loading and processing should continue working
- **Dependency management**: Maven and npm dependencies should remain intact
- **Security configurations**: OAuth2, JWT, CORS settings should be preserved
- **Database connectivity**: HikariCP and Supabase configurations should continue functioning
- **API endpoints**: All existing REST endpoints should remain available and functional

### 3.3 Preservation Validation
```pseudocode
function preservationBehavior(operation):
    return (
        operation.individualServiceStartup == FUNCTIONAL AND
        operation.configurationIntegrity == MAINTAINED AND
        operation.environmentVariables == WORKING AND
        operation.dependencies == INTACT AND
        operation.securityConfig == PRESERVED AND
        operation.databaseConnectivity == FUNCTIONAL AND
        operation.apiEndpoints == AVAILABLE
    )
```

## 4. Root Cause Analysis Areas

### 4.1 Process Management
- Windows child process spawning and lifecycle management
- Shell vs non-shell execution in spawn() calls
- Process termination and cleanup handling
- Signal propagation and graceful shutdown

### 4.2 Health Check System
- HTTP timeout configurations
- Retry logic and failure detection
- Service dependency ordering
- False positive/negative detection

### 4.3 Port Management
- Port conflict detection and resolution
- Process identification and termination
- Port binding race conditions
- Network interface binding issues

### 4.4 Configuration Synchronization
- Cross-service configuration consistency
- Environment variable propagation
- Service discovery and connectivity
- Startup timing coordination

## 5. Success Criteria

### 5.1 Functional Requirements
- [ ] `npm run dev` successfully starts all three services
- [ ] FastAPI responds with HTTP 200 on `/health` endpoint
- [ ] Spring Boot reaches ready state without fatal errors
- [ ] Vite serves frontend on http://localhost:5176
- [ ] Frontend loads and displays correctly in browser
- [ ] API calls from frontend to backend work correctly
- [ ] All services remain running until manual termination
- [ ] CTRL+C cleanly shuts down all services

### 5.2 Quality Requirements
- [ ] No fake health endpoints or hidden errors
- [ ] No killing of unrelated processes
- [ ] No silent port changes from required ports (5176, 8080, 8000)
- [ ] Proper error reporting and visibility
- [ ] Robust handling of Windows process management
- [ ] Reliable service dependency orchestration

### 5.3 Preservation Requirements
- [ ] Individual service startup still works independently
- [ ] Existing configurations remain functional
- [ ] Security and database settings preserved
- [ ] API functionality maintained
- [ ] No breaking changes to existing features