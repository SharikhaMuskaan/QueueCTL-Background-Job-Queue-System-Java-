package com.example.queuectl;

import java.util.List;
import java.util.Optional;

public class Main {

    static void usage() {
        System.out.println("queuectl commands:");
        System.out.println("  enqueue '{\"id\":\"job1\",\"command\":\"echo hi\",\"max_retries\":3}'");
        System.out.println("  worker start <count>");
        System.out.println("  status");
        System.out.println("  list [state]");
        System.out.println("  dlq list");
        System.out.println("  dlq retry <jobId>");
        System.out.println("  config set <key> <value>");
        System.out.println("  config get <key>");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        String cmd = args[0];

        switch (cmd) {
            case "enqueue": {
                if (args.length < 2) {
                    System.err.println("enqueue requires a JSON arg");
                    return;
                }
                String jobJson = args[1];
                var mapper = new java.util.HashMap<String,String>();
                String j = jobJson.trim();
                j = j.replaceAll("^\\{","").replaceAll("\\}$","");
                for (String part : j.split(",")) {
                    String[] kv = part.split(":",2);
                    if (kv.length<2) continue;
                    String k = kv[0].trim().replaceAll("^\"|\"$","");
                    String v = kv[1].trim().replaceAll("^\"|\"$","");
                    mapper.put(k,v);
                }
                String id = mapper.getOrDefault("id", java.util.UUID.randomUUID().toString());
                String command = mapper.get("command");
                if (command == null) {
                    System.err.println("Missing 'command' field");
                    return;
                }
                int maxRetries = Integer.parseInt(mapper.getOrDefault("max_retries","3"));
                Job job = new Job(id, command, "pending", 0, maxRetries, Job.nowIso(), Job.nowIso(), Job.nowIso(), null);
                JobStore s = new JobStore("queue.db");
                boolean ok = s.insertJob(job);
                if (ok) System.out.println("Enqueued " + id);
                else System.out.println("Job id exists: " + id);
                break;
            }
            case "worker": {
                if (args.length < 2) { System.err.println("worker requires subcommand start"); return; }
                String sub = args[1];
                if ("start".equals(sub)) {
                    int count = 1;
                    if (args.length >= 3) {
                        try { count = Integer.parseInt(args[2]); } catch (Exception e) { System.err.println("invalid count"); return; }
                    }
                    JobStore store = new JobStore("queue.db");
                    ConfigStore cfg = new ConfigStore(store.conn);
                    cfg.initDefaults();
                    WorkerManager mgr = new WorkerManager(store, cfg, count);
                    Runtime.getRuntime().addShutdownHook(new Thread(mgr::shutdownGraceful));
                    mgr.start();
                    System.out.println("Workers started. Press Ctrl+C to stop.");
                    try { Thread.currentThread().join(); } catch (InterruptedException ignored) {}
                } else {
                    System.err.println("unknown worker subcommand: " + sub);
                }
                break;
            }
            case "status": {
                JobStore s = new JobStore("queue.db");
                var all = s.listByState(null);
                long pending=0, processing=0, completed=0, dead=0;
                for (var j : all) {
                    switch (j.state) {
                        case "pending": pending++; break;
                        case "processing": processing++; break;
                        case "completed": completed++; break;
                        case "dead": dead++; break;
                    }
                }
                System.out.println("pending: "+pending);
                System.out.println("processing: "+processing);
                System.out.println("completed: "+completed);
                System.out.println("dead: "+dead);
                break;
            }
            case "list": {
                String state = null;
                if (args.length >= 2) state = args[1];
                JobStore s = new JobStore("queue.db");
                List<Job> rows = s.listByState(state);
                for (var r : rows) System.out.println(r.toString());
                break;
            }
            case "dlq": {
                if (args.length < 2) { System.err.println("dlq requires list|retry"); return; }
                String sub = args[1];
                JobStore s = new JobStore("queue.db");
                if ("list".equals(sub)) {
                    var dead = s.listByState("dead");
                    for (var j : dead) System.out.println(j.toString());
                } else if ("retry".equals(sub)) {
                    if (args.length < 3) { System.err.println("dlq retry requires jobId"); return; }
                    String jobId = args[2];
                    Optional<Job> maybe = s.getDeadJob(jobId);
                    if (maybe.isEmpty()) {
                        System.out.println("No such job in DLQ");
                        return;
                    }
                    s.retryDeadJob(jobId);
                    System.out.println("Job " + jobId + " moved to pending");
                } else {
                    System.err.println("unknown dlq sub: " + sub);
                }
                break;
            }
            case "config": {
                if (args.length < 2) { System.err.println("config requires get|set"); return; }
                String sub = args[1];
                JobStore s = new JobStore("queue.db");
                ConfigStore cfg = new ConfigStore(s.conn);
                cfg.initDefaults();
                if ("set".equals(sub)) {
                    if (args.length < 4) { System.err.println("config set <key> <value>"); return; }
                    cfg.upsert(args[2], args[3]);
                    System.out.println("Set " + args[2] + " = " + args[3]);
                } else if ("get".equals(sub)) {
                    if (args.length < 3) { System.err.println("config get <key>"); return; }
                    String val = cfg.get(args[2], null);
                    System.out.println(val == null ? "(not set)" : val);
                } else {
                    System.err.println("unknown config sub: " + sub);
                }
                break;
            }
            default:
                System.err.println("unknown command: " + cmd);
                usage();
        }
    }
}
