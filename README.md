# QueueCTL-Background-Job-Queue-System-Java-
QueueCTL is a CLI-based background job queue system built in Java. It lets you enqueue jobs (shell commands), run multiple workers, automatically retry failed jobs with exponential backoff, and move permanently failing jobs to a Dead Letter Queue (DLQ). All jobs are stored in an SQLite database so they persist across restarts.

🚀 Features:
✔ Enqueue Jobs

Add background jobs that execute shell commands (ex: echo, sleep, powershell commands).

✔ Worker Processes

Start multiple worker threads to process jobs in parallel.

✔ Automatic Retries

Failed jobs retry automatically with exponential backoff:delay = base ^ attempts

✔ Dead Letter Queue (DLQ)

After max retries, jobs move to dead state.

✔ Graceful Shutdown

Press Ctrl+C → workers finish the current job before exiting.

✔ Persistent Storage

All jobs and configurations are stored in:queue.db (SQLite)

✔ Configurable Settings

Set max_retries or backoff_base from CLI.

✔ Bonus Feature: Job Timeout

Each job can optionally include:"timeout_seconds": 10
The job will be force-terminated after timeout.

🧰 Tech Stack

Java 17

Maven

SQLite (sqlite-jdbc)

PowerShell / Windows CMD

No frameworks — pure Java

📁 Project Structure
Queue_CLI_Java/
│
├── pom.xml
├── README.md
├── design.md
├── queue.db
│
├── src/main/java/com/example/queuectl/
│   ├── Main.java
│   ├── Job.java
│   ├── JobStore.java
│   ├── Worker.java
│   ├── WorkerManager.java
│   └── ConfigStore.java
│
└── tests/
    └── test_flows.ps1

🛠️ Setup Instructions:
1. Build the project
mvn -U clean package

2. Run the CLI
java -jar target/queuectl-1.0-SNAPSHOT.jar <command>

📝 CLI Usage
▶️ Enqueue a Job
java -jar target\queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"job1","command":"echo Hello","max_retries":3}'


▶️ Start Worker
java -jar target\queuectl-1.0-SNAPSHOT.jar worker start 1

▶️ Check Job Status
java -jar target\queuectl-1.0-SNAPSHOT.jar status

▶️ List Jobs
java -jar target\queuectl-1.0-SNAPSHOT.jar list
java -jar target\queuectl-1.0-SNAPSHOT.jar list completed
java -jar target\queuectl-1.0-SNAPSHOT.jar list dead

▶️ Dead Letter Queue
java -jar target\queuectl-1.0-SNAPSHOT.jar dlq list
java -jar target\queuectl-1.0-SNAPSHOT.jar dlq retry job1

▶️ Configuration
java -jar target\queuectl-1.0-SNAPSHOT.jar config get backoff_base
java -jar target\queuectl-1.0-SNAPSHOT.jar config set backoff_base 3

▶️ Job With Timeout (Bonus)
java -jar target\queuectl-1.0-SNAPSHOT.jar enqueue '{"id":"job-time","command":"ping -n 20 127.0.0.1","timeout_seconds":5}'


🔄 Job Lifecycle
pending → processing → completed
             ↓
           failed → retry (scheduled with backoff)
             ↓
         attempts >= max_retries
             ↓
             dead (DLQ)

🗂️ Database Inspection
sqlite3 queue.db
.tables
SELECT * FROM jobs;

🏗️ Architecture Overview
System Diagram
CLI Commands
     ↓
Main.java
     ↓
JobStore (SQLite)
     ↓
WorkerManager
     ↓
Worker Threads → ProcessBuilder → Execute Commands

📌 Assumptions

Workers run inside the same JVM (multithreading).

Commands run via Windows cmd.

JSON parsing is simplified for the assignment.

SQLite DB stored locally as queue.db.
