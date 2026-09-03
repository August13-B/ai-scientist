package com.aiscientist.backend.controller;

import com.aiscientist.backend.service.TaskService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 科研任务控制器（纯转发网关：把前端请求转发到 ai-service，经 TaskService）。
 * 对应 docs/api-design.md 1.1 节接口。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** 提交科研任务：转发 ai-service 启动管线，返回 taskId */
    @PostMapping
    public Map<String, Object> createTask(@RequestBody Map<String, Object> request) {
        String question = request == null ? null
                : String.valueOf(request.getOrDefault("question", ""));
        Long taskId = taskService.createTask(question);
        return Map.of("taskId", taskId);
    }

    /** 任务列表（转发 ai-service runs） */
    @GetMapping
    public List<Map<String, Object>> listTasks() {
        return taskService.listRuns();
    }

    /** 任务详情（转发 ai-service state 快照） */
    @GetMapping("/{id}")
    public Map<String, Object> getTask(@PathVariable Long id) {
        return taskService.getTask(id);
    }

    /** Agent 级执行追踪（转发 ai-service trace，前端实时展示输入输出） */
    @GetMapping("/{id}/trace")
    public List<Map<String, Object>> trace(@PathVariable Long id) {
        return taskService.trace(id);
    }

    /** 获取生成的研究计划（转发 ai-service state.finalReport） */
    @GetMapping("/{id}/report")
    public Map<String, Object> getReport(@PathVariable Long id) {
        return taskService.getReport(id);
    }

    /** SSE 流：Agent 思考/输入输出实时推送（转发 ai-service stream） */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        return taskService.stream(id);
    }

    /** 人在回路：提交人类审阅意见，恢复管线（转发 ai-service resume） */
    @PostMapping("/{id}/intervene")
    public Map<String, Object> intervene(@PathVariable Long id,
                                         @RequestBody Map<String, Object> feedback) {
        return taskService.intervene(id, feedback);
    }
}
