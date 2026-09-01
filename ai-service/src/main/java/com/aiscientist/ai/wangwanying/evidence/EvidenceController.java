package com.aiscientist.ai.wangwanying.evidence;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {
    private final EvidenceRepository repository;
    public EvidenceController(EvidenceRepository repository) { this.repository = repository; }
    @GetMapping public List<Evidence> search(@RequestParam(defaultValue = "") String q,
                                             @RequestParam(defaultValue = "20") int limit) {
        return repository.search(q, limit);
    }
    @PostMapping public Evidence add(@Valid @RequestBody Evidence evidence) { return repository.save(evidence); }
}
