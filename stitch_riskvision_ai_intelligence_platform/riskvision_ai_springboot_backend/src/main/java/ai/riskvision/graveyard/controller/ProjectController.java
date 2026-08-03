package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.entity.ProjectEntity;
import ai.riskvision.graveyard.service.AuthService;
import ai.riskvision.graveyard.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectService projectService;
    private final AuthService authService;

    @GetMapping({"/api/v1/projects", "/api/projects", "/api/v1/projects/my", "/api/projects/my"})
    public ResponseEntity<?> getMyProjects(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDesc", defaultValue = "true") boolean sortDesc,
            Principal principal) {
        
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Page<ProjectEntity> projects = projectService.getMyProjects(
                principal.getName(), search, status, page, size, sortBy, sortDesc);
        
        return ResponseEntity.ok(projects);
    }

    @GetMapping({"/api/v1/me", "/api/me"})
    public ResponseEntity<?> getMe(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return ResponseEntity.ok(authService.getMe(principal.getName()));
    }
}
