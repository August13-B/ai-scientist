package com.challenge.aiscientist.llm;

import java.time.Instant;

public record ApiCallLog(Instant timestamp, String operation, String model, int status, long elapsedMs, String error) { }
