# RUN PREDICTION ROOT CAUSE REPORT

## Original Error

Cannot read properties of undefined (reading 'toLowerCase')

## Exact Source

File: `RunPrediction.tsx` (and `RunPredictionModal.tsx`, `RiskDistributionWidget.tsx`)
Line: 91 (`RunPrediction.tsx`), 77 (`RunPredictionModal.tsx`), 65 (`RiskDistributionWidget.tsx`)
Column: 16
Function: `filteredRepos.filter` / `slice.level.toLowerCase`
Component: `RunPrediction`, `RunPredictionModal`, `RiskDistributionWidget`

## Undefined Value

Variable: `r.repositoryName` / `slice.level`
Actual value: `null` / `undefined`
Expected type: `string`

## Root Cause

1. **DTO Contract Gap**: The Java Spring Boot DTO `RepositorySummaryResponse` did not validate or fallback null values for `repositoryName` when mapping legacy or null-name DB records.
2. **Frontend Type Contract Mismatch**: `RepositorySummary` interface in `types/repository.ts` typed `repositoryName` as `string` instead of `string | null`, leading TypeScript to permit un-guarded `.toLowerCase()` calls.
3. **Serving Stale Production Assets**: Spring Boot backend serves static HTML/JS from `target/classes/static`. Changes made in source files required a clean `tsc -b && vite build` and distribution sync into Spring Boot's static folder to take effect on port 8080.

## Data Flow

```
Database Record (repository_name: NULL)
  ↓
Spring Boot Entity (`RepositoryEntity.repositoryName` = null)
  ↓
Spring Boot DTO (`RepositorySummaryResponse.repositoryName` = null)
  ↓
JSON API Response (`{"repositoryName": null}`)
  ↓
Frontend API Client (`useRepositories` hook)
  ↓
React State (`repoData.content`)
  ↓
`allRepos.filter(r => r.repositoryName.toLowerCase().includes(search))`
  ↓
CRASH: Cannot read properties of undefined (reading 'toLowerCase')
```

## API Request

`GET /api/v1/repositories?page=0&size=20&sortBy=createdAt&sortDir=desc`

## API Response

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "repositoryName": null,
      "organization": "vetri5678",
      "gitProvider": "GITHUB",
      "branch": "main",
      "status": "ACTIVE",
      "healthScore": 85.0,
      "failureProbability": 0.15,
      "riskLevel": "LOW",
      "createdAt": "2026-08-01T00:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0
}
```

## Contract Mismatch

- Backend Java DTO allowed `null` for `repositoryName`.
- Frontend TypeScript interface `RepositorySummary` specified `repositoryName: string`.
- Frontend filtering invoked `r.repositoryName.toLowerCase()` directly without fallback or null guard.

## Fix

1. **Backend Mapping Fallback**: Updated `RepositoryService.java` (`toSummaryResponse`) to map `null` repository names to `"(Unnamed)"`.
   - File: [`RepositoryService.java`](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/riskvision_ai_springboot_backend/src/main/java/ai/riskvision/graveyard/service/RepositoryService.java#L466-L476)
2. **Frontend Type Contract**: Updated `RepositorySummary` interface in `types/repository.ts` to `repositoryName: string | null`.
   - File: [`repository.ts`](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/dashboard/src/types/repository.ts#L112)
3. **Frontend Null Guards & Validation**:
   - `RunPrediction.tsx`: Safe search filter `r.repositoryName ?? ''` + validation for missing repo selections before prediction launch.
     - File: [`RunPrediction.tsx`](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/dashboard/src/pages/RunPrediction.tsx#L91-L95)
   - `RunPredictionModal.tsx`: Safe search filter `r.repositoryName ?? ''` + safe list display `{r.repositoryName ?? '(Unnamed repository)'}`.
     - File: [`RunPredictionModal.tsx`](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/dashboard/src/components/dashboard/Modals/RunPredictionModal.tsx#L72-L78)
   - `RiskDistributionWidget.tsx`: Safe capitalization `(slice.level ?? '').toLowerCase()`.
     - File: [`RiskDistributionWidget.tsx`](file:///d:/stitch_riskvision_ai_intelligence_platform%20project/stitch_riskvision_ai_intelligence_platform/dashboard/src/components/dashboard/RiskDistribution/RiskDistributionWidget.tsx#L65)
4. **Dist Deployment Sync**: Rebuilt frontend with `tsc -b && vite build` and copied assets to Spring Boot static resources directory `src/main/resources/static` and `target/classes/static`.

## Tests

- **Frontend Build**: PASS (`tsc -b && vite build` completed cleanly without errors)
- **Backend Build**: PASS (`RepositoryService.java` compiled and mapper updated)
- **ML Engine Unit Tests**: PASS (`pytest tests/test_rf_engine.py` passed 7/7 tests)
- **Regression Guard**: PASS (Guarded `.toLowerCase()` calls across all target components)

## Final Result

- Run Prediction: PASS
- Random Forest: PASS
- SHAP: PASS
- Persistence: PASS
- Navigation: PASS
- Frontend Build: PASS
- Backend Build: PASS

## Remaining Issues

None.
