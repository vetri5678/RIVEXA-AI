# Implementation Plan

- [ ] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Multi-Service Startup Failure Detection
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate startup orchestration failures
  - **Scoped PBT Approach**: Focus on the specific `npm run dev` command execution with current dev-runner.js
  - Test that `npm run dev` results in startup failure for multi-service orchestration (from Bug Condition in design)
  - The test assertions should verify all services reach healthy state and remain running (matching Expected Behavior Properties from design)
  - Run test on UNFIXED code using current dev-runner.js
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the startup reliability bug exists)
  - Document counterexamples found: which services fail, what errors occur, when shutdown happens
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Individual Service and Configuration Integrity
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED system for individual service startup (non-orchestrated scenarios)
  - Observe: Individual Maven Spring Boot startup works correctly
  - Observe: Individual FastAPI startup with uvicorn works correctly  
  - Observe: Individual Vite dev server startup works correctly
  - Observe: Configuration files (application.properties, package.json, vite.config.js) remain functional
  - Write property-based tests capturing observed behavior patterns from Preservation Requirements
  - Property-based testing generates many test cases for stronger guarantees
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3_

- [ ] 3. Fix for RiskVision AI startup reliability issues

  - [ ] 3.1 Diagnose and fix dev-runner.js process management
    - Run `npm run dev` to identify actual startup failures and root causes
    - Fix Windows child process spawning and lifecycle management issues
    - Implement proper shell vs non-shell execution handling in spawn() calls
    - Add robust process termination and cleanup handling
    - Implement correct signal propagation and graceful shutdown
    - _Bug_Condition: isBugCondition(startupAttempt) from design_
    - _Expected_Behavior: expectedBehavior(startupResult) from design_
    - _Preservation: Individual service startup and configuration integrity from design_
    - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2_

  - [ ] 3.2 Fix health check system reliability
    - Implement proper HTTP timeout configurations and retry logic
    - Fix service dependency ordering and startup timing
    - Eliminate false positive/negative health check detection
    - Ensure genuine health endpoints (no fake/mocked responses)
    - _Bug_Condition: Health check failures causing startup failure_
    - _Expected_Behavior: Reliable health validation enabling proper service sequencing_
    - _Preservation: Existing health endpoint functionality_
    - _Requirements: 2.1, 2.3, 2.4, 5.1, 5.2_

  - [ ] 3.3 Fix port management and conflict resolution
    - Implement safe port conflict detection without killing unrelated processes
    - Fix port binding race conditions and network interface issues
    - Maintain required ports (5176, 8080, 8000) without silent changes
    - Add proper process identification for port cleanup
    - _Bug_Condition: Port conflicts causing service startup failures_
    - _Expected_Behavior: Clean port management without affecting unrelated processes_
    - _Preservation: Existing port configurations and service bindings_
    - _Requirements: 2.4, 5.2_

  - [ ] 3.4 Fix configuration synchronization across services
    - Ensure consistent cross-service configuration handling
    - Fix environment variable propagation to all child processes
    - Implement proper service discovery and connectivity validation
    - Add startup timing coordination between dependent services
    - _Bug_Condition: Configuration inconsistencies causing integration failures_
    - _Expected_Behavior: Synchronized configuration enabling seamless service interaction_
    - _Preservation: Existing .env, application.properties, and package.json settings_
    - _Requirements: 3.1, 3.2, 5.1_

  - [ ] 3.5 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Multi-Service Startup Success Validation
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior for startup orchestration
    - When this test passes, it confirms all services start and remain running correctly
    - Run bug condition exploration test from step 1 with fixed dev-runner.js
    - **EXPECTED OUTCOME**: Test PASSES (confirms startup reliability bug is fixed)
    - _Requirements: Expected Behavior Properties 2.1, 2.2, 2.3 from design_

  - [ ] 3.6 Verify preservation tests still pass
    - **Property 2: Preservation** - Individual Service and Configuration Integrity Maintained
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2 with all fixes applied
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions in individual service functionality)
    - Confirm all individual services, configurations, and existing features still work correctly

- [ ] 4. Checkpoint - Ensure all tests pass and system meets success criteria
  - Verify `npm run dev` successfully starts all three services
  - Confirm FastAPI responds with HTTP 200 on `/health` endpoint  
  - Validate Spring Boot reaches ready state without fatal errors
  - Check Vite serves frontend on http://localhost:5176
  - Test frontend loads and displays correctly in browser
  - Verify API calls from frontend to backend work correctly
  - Confirm all services remain running until manual termination
  - Test CTRL+C cleanly shuts down all services
  - Ensure no fake health endpoints, hidden errors, or unrelated process killing
  - Validate required ports (5176, 8080, 8000) are maintained without silent changes
  - Ask user if any additional issues need to be addressed