package com.challenge.aiscientist.llm;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class ApiCallLogService {
    private final List<ApiCallLog> logs = new CopyOnWriteArrayList<>();

    public void add(ApiCallLog log) {
        logs.add(log);
        if (logs.size() > 500) logs.remove(0);
    }

    public List<ApiCallLog> latest() { return List.copyOf(logs); }
}
