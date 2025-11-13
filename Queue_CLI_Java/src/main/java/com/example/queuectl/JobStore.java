
package com.example.queuectl;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JobStore {
    public final Connection conn;

    public JobStore(String dbPath) throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        this.conn = DriverManager.getConnection(url);
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL;");
        }
        init();
    }

    private void init() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS jobs (" +
                    "id TEXT PRIMARY KEY, command TEXT NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0," +
                    "max_retries INTEGER NOT NULL DEFAULT 3, created_at TEXT, updated_at TEXT, next_run_at TEXT, last_error TEXT)");
        }
    }

    public synchronized boolean insertJob(Job job) throws SQLException {
        String sql = "INSERT INTO jobs(id,command,state,attempts,max_retries,created_at,updated_at,next_run_at,last_error) VALUES(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, job.id);
            p.setString(2, job.command);
            p.setString(3, job.state);
            p.setInt(4, job.attempts);
            p.setInt(5, job.maxRetries);
            p.setString(6, job.createdAt);
            p.setString(7, job.updatedAt);
            p.setString(8, job.nextRunAt);
            p.setString(9, job.lastError);
            p.execute();
            return true;
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE") || e.getMessage().contains("constraint")) return false;
            throw e;
        }
    }

    public Optional<Job> claimPendingJob() throws SQLException {
        conn.setAutoCommit(false);
        try (PreparedStatement select = conn.prepareStatement(
                "SELECT id,command,attempts,max_retries FROM jobs WHERE state='pending' AND (next_run_at IS NULL OR next_run_at <= ?) ORDER BY created_at LIMIT 1")) {
            select.setString(1, Instant.now().toString());
            ResultSet rs = select.executeQuery();
            if (!rs.next()) {
                conn.commit();
                conn.setAutoCommit(true);
                return Optional.empty();
            }
            String id = rs.getString(1);
            String cmd = rs.getString(2);
            int attempts = rs.getInt(3);
            int maxRetries = rs.getInt(4);

            try (PreparedStatement upd = conn.prepareStatement("UPDATE jobs SET state='processing', attempts = attempts + 1, updated_at = ? WHERE id = ? AND state = 'pending'")) {
                upd.setString(1, Instant.now().toString());
                upd.setString(2, id);
                int updated = upd.executeUpdate();
                if (updated == 0) {
                    conn.commit();
                    conn.setAutoCommit(true);
                    return Optional.empty();
                }
            }
            conn.commit();
            conn.setAutoCommit(true);
            Job job = new Job(id, cmd, "processing", attempts + 1, maxRetries, Instant.now().toString(), Instant.now().toString(), null, null);
            return Optional.of(job);
        } catch (SQLException ex) {
            conn.rollback();
            conn.setAutoCommit(true);
            throw ex;
        }
    }

    public void markCompleted(String id, String output) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("UPDATE jobs SET state='completed', updated_at=?, last_error=? WHERE id=?")) {
            p.setString(1, Instant.now().toString());
            p.setString(2, output);
            p.setString(3, id);
            p.execute();
        }
    }

    public void markFailedRetry(String id, String error, long delaySeconds) throws SQLException {
        Instant next = Instant.now().plus(delaySeconds, ChronoUnit.SECONDS);
        try (PreparedStatement p = conn.prepareStatement("UPDATE jobs SET state='pending', updated_at=?, next_run_at=?, last_error=? WHERE id=?")) {
            p.setString(1, Instant.now().toString());
            p.setString(2, next.toString());
            p.setString(3, error);
            p.setString(4, id);
            p.execute();
        }
    }

    public void markDead(String id, String error) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("UPDATE jobs SET state='dead', updated_at=?, last_error=? WHERE id=?")) {
            p.setString(1, Instant.now().toString());
            p.setString(2, error);
            p.setString(3, id);
            p.execute();
        }
    }

    public List<Job> listByState(String state) throws SQLException {
        List<Job> out = new ArrayList<>();
        String q = state == null ? "SELECT id,command,state,attempts,max_retries,created_at,updated_at,next_run_at,last_error FROM jobs ORDER BY created_at" :
                "SELECT id,command,state,attempts,max_retries,created_at,updated_at,next_run_at,last_error FROM jobs WHERE state=? ORDER BY created_at";
        try (PreparedStatement p = conn.prepareStatement(q)) {
            if (state != null) p.setString(1, state);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                out.add(new Job(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)));
            }
        }
        return out;
    }

    public Optional<Job> getDeadJob(String id) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("SELECT id,command,state,attempts,max_retries,created_at,updated_at,next_run_at,last_error FROM jobs WHERE id = ? AND state = 'dead'")) {
            p.setString(1, id);
            ResultSet rs = p.executeQuery();
            if (rs.next()) {
                return Optional.of(new Job(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)));
            }
            return Optional.empty();
        }
    }

    public void retryDeadJob(String id) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("UPDATE jobs SET state='pending', attempts=0, updated_at=?, next_run_at=? WHERE id=? AND state='dead'")) {
            String now = Instant.now().toString();
            p.setString(1, now);
            p.setString(2, now);
            p.setString(3, id);
            p.execute();
        }
    }
}
