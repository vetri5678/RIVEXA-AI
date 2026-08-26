package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.search.GlobalSearchDto;
import ai.riskvision.graveyard.service.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Slf4j
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    @GetMapping
    public ResponseEntity<GlobalSearchDto> globalSearch(
            @RequestParam(value = "q", required = false, defaultValue = "") String query,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
            Principal principal
    ) {
        String callerEmail = principal != null ? principal.getName() : "admin@riskvision.ai";
        log.info("[GlobalSearchController] GET /api/v1/search requested by email={} query='{}'", callerEmail, query);
        GlobalSearchDto response = globalSearchService.executeSearch(callerEmail, query, limit);
        return ResponseEntity.ok(response);
    }
}
