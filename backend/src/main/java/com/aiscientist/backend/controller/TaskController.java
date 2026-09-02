package com.aiscientist.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 科研任务控制器（骨架）。
 * 接口字段定义由后端组设计时确定，见 docs/api-design.md。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    /** 提交科研任务 */
    @PostMapping
    public Map<String, Object> createTask(@RequestBody Map<String, Object> request) {
        // TODO: 创建任务记录，转发至 ai-service 启动管线
        return Map.of("taskId", "todo");
    }

    /** 任务列表 */
    @GetMapping
    public List<Map<String, Object>> listTasks() {
        // TODO: 分页查询任务列表
        return List.of();
    }

    /** 任务详情 */
    @GetMapping("/{id}")
    public Map<String, Object> getTask(@PathVariable Long id) {
        // TODO: 查询任务详情与状态
        return Map.of("id", id);
    }

    /** 获取生成的研究计划 */
    @GetMapping("/{id}/report")
    public Map<String, Object> getReport(@PathVariable Long id) {
        // TODO: 返回 10 字段《科学假设与研究计划》
        return Map.of("id", id);
    }

    /** SSE 流：Agent 思考过程实时推送（转发自 ai-service） */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        // TODO: 建立与 ai-service 的 SSE 连接并转发事件
        return new SseEmitter();
    }

    /** 人在回路：提交人类审阅意见，恢复管线 */
    @PostMapping("/{id}/intervene")
    public Map<String, Object> intervene(@PathVariable Long id, @RequestBody Map<String, Object> feedback) {
        // TODO: 转发审阅意见至 ai-service 并恢复管线
        return Map.of("id", id, "resumed", true);
    }
}
