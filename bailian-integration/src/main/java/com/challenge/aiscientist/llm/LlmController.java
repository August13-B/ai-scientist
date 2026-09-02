package com.challenge.aiscientist.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LlmController {
    private final DashScopeService dashScopeService;
    private final ApiCallLogService logService;
    public LlmController(DashScopeService dashScopeService, ApiCallLogService logService) { this.dashScopeService = dashScopeService; this.logService = logService; }
    @PostMapping("/llm/chat") public ChatResponse chat(@Valid @RequestBody ChatRequest request) { return new ChatResponse(dashScopeService.chat(request.prompt())); }
    @GetMapping("/llm/logs") public List<ApiCallLog> logs() { return logService.latest(); }
    public record ChatRequest(@NotBlank String prompt) { }
    public record ChatResponse(String content) { }
}
