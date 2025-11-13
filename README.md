# QueueCTL-Background-Job-Queue-System-Java-
QueueCTL is a CLI-based background job queue system built in Java. It lets you enqueue jobs (shell commands), run multiple workers, automatically retry failed jobs with exponential backoff, and move permanently failing jobs to a Dead Letter Queue (DLQ). All jobs are stored in an SQLite database so they persist across restarts
