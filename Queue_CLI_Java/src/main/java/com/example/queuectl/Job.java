package com.example.queuectl;

import java.time.Instant;

public class Job {
    public final String id;
    public final String command;
    public String state; // pending, processing, completed, dead
    public int attempts;
    public final int maxRetries;
    public final String createdAt;
    public String updatedAt;
    public String nextRunAt;
    public String lastError;

    public Job(String id, String command, String state, int attempts, int maxRetries, String createdAt, String updatedAt, String nextRunAt, String lastError) {
        this.id = id;
        this.command = command;
        this.state = state;
        this.attempts = attempts;
        this.maxRetries = maxRetries;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.nextRunAt = nextRunAt;
        this.lastError = lastError;
    }

    public static String nowIso() {
        return Instant.now().toString();
    }

    @Override
    public String toString() {
        return "{\"id\":\""+id+"\",\"command\":\""+command+"\",\"state\":\""+state+"\",\"attempts\":"+attempts+",\"max_retries\":"+maxRetries+",\"created_at\":\""+createdAt+"\",\"updated_at\":\""+updatedAt+"\",\"next_run_at\":\""+nextRunAt+"\"}";
    }
}
