package com.example.queuectl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class Worker implements Runnable {
    private final JobStore store;
    private final ConfigStore config;
    private final int idx;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public Worker(int idx, JobStore store, ConfigStore config) {
        this.idx = idx;
        this.store = store;
        this.config = config;
    }

    public void shutdown() {
        running.set(false);
    }

    @Override
    public void run() {
        try {
            int backoffBase = Integer.parseInt(config.get("backoff_base","2"));
            System.out.println("[worker-"+idx+"] started backoff_base="+backoffBase+" pid="+ProcessHandle.current().pid());
            while (running.get()) {
                Optional<Job> maybe = store.claimPendingJob();
                if (maybe.isEmpty()) {
                    Thread.sleep(800);
                    continue;
                }
                Job job = maybe.get();
                System.out.println("[worker-"+idx+"] executing job " + job.id + " attempt=" + job.attempts + " cmd=" + job.command);
                ProcessBuilder pb = new ProcessBuilder();
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pb.command("cmd.exe", "/c", job.command);
                } else {
                    pb.command("bash", "-lc", job.command);
                }
                pb.redirectErrorStream(true);
                try {
                    Process proc = pb.start();
                    StringBuilder output = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    }
                    int rc = proc.waitFor();
                    String outStr = output.toString().trim();
                    if (rc == 0) {
                        store.markCompleted(job.id, outStr);
                        System.out.println("[worker-"+idx+"] job " + job.id + " completed: " + outStr);
                    } else {
                        if (job.attempts >= job.maxRetries) {
                            store.markDead(job.id, outStr.isEmpty() ? ("rc="+rc) : outStr);
                            System.out.println("[worker-"+idx+"] job " + job.id + " moved to DLQ after attempts=" + job.attempts);
                        } else {
                            long delay = (long) Math.pow(backoffBase, job.attempts);
                            store.markFailedRetry(job.id, outStr.isEmpty() ? ("rc="+rc) : outStr, delay);
                            System.out.println("[worker-"+idx+"] job " + job.id + " failed rc=" + rc + ", retrying after " + delay + "s");
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (job.attempts >= job.maxRetries) {
                        store.markDead(job.id, ex.toString());
                        System.out.println("[worker-"+idx+"] job " + job.id + " moved to DLQ due to exception");
                    } else {
                        long delay = (long) Math.pow(Integer.parseInt(config.get("backoff_base","2")), job.attempts);
                        store.markFailedRetry(job.id, ex.toString(), delay);
                        System.out.println("[worker-"+idx+"] job " + job.id + " exception, retrying after " + delay + "s");
                    }
                }
            }
            System.out.println("[worker-"+idx+"] exiting");
        } catch (SQLException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}

