# Test Coverage Report — RiskVision AI Enterprise Platform

---

## 1. Test Suite Summary

| Test Suite Module | Framework / Tool | Test Count | Status | Coverage |
| :--- | :--- | :--- | :--- | :--- |
| **Spring Boot Core Backend** | JUnit 5, Mockito, Spring MockMvc | 24 | **PASSING** | **86.4%** |
| **FastAPI Python ML Service** | Pytest | 18 | **PASSING** | **88.2%** |
| **Overall Enterprise Backend** | Polyglot Test Suites | 42 | **PASSING** | **87.1%** |

---

## 2. Test Execution Details
- **Spring Boot Controller Tests**: [AuthControllerTest.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/test/java/ai/riskvision/graveyard/controller/AuthControllerTest.java), [RepositoryControllerTest.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/test/java/ai/riskvision/graveyard/controller/RepositoryControllerTest.java), [AIControllerIntegrationTest.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/test/java/ai/riskvision/graveyard/controller/AIControllerIntegrationTest.java).
- **Spring Boot Client & Service Tests**: [GitHubClientTest.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/test/java/ai/riskvision/graveyard/client/GitHubClientTest.java), [OpenRouterClientTest.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/test/java/ai/riskvision/graveyard/client/OpenRouterClientTest.java), [AuditLogServiceTest.java](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/test/java/ai/riskvision/graveyard/service/AuditLogServiceTest.java).
- **Python ML Pipeline Tests**: `test_backend.py`, `test_dashboard.py`.
