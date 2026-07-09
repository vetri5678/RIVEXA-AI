package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.dto.DashboardOverviewDTO;
import ai.riskvision.graveyard.model.PredictionRecord;
import ai.riskvision.graveyard.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService service;

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverviewDTO> getOverview() {
        return ResponseEntity.ok(service.getOverview());
    }

    @GetMapping("/high-risk")
    public ResponseEntity<List<PredictionRecord>> getHighRiskProjects() {
        return ResponseEntity.ok(service.getHighRiskProjects());
    }
}
