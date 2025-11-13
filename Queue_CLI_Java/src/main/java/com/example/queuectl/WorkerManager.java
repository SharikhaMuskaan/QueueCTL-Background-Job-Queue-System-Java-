package com.example.queuectl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class WorkerManager {
    private final JobStore store;
    private final ConfigStore config;
    private final ExecutorService executor;
    private final List<Worker> workers = new ArrayList<>();
    private final List<Future<?>> futures = new ArrayList<>();

    public WorkerManager(JobStore store, ConfigStore config, int count) {
        this.store = store;
        this.config = config;
        this.executor = Executors.newFixedThreadPool(count);
        for (int i = 0; i < count; i++) {
            Worker w = new Worker(i+1, store, config);
            workers.add(w);
        }
    }

    public void start() {
        for (Worker w : workers) {
            futures.add(executor.submit(w));
        }
    }

    public void shutdownGraceful() {
        System.out.println("Shutting down workers gracefully...");
        for (Worker w : workers) {
            w.shutdown();
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
