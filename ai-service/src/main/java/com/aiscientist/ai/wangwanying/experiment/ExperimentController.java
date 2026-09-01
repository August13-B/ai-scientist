package com.aiscientist.ai.wangwanying.experiment;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
    private final ExperimentDesignService service;
    public ExperimentController(ExperimentDesignService service) { this.service = service; }
    @PostMapping("/design") public ExperimentPlan design(@Valid @RequestBody ExperimentRequest request) {
        return service.design(request);
    }
}
