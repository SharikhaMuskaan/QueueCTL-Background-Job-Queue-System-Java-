
package com.example.queuectl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ConfigStore {
    private final Connection conn;
    public ConfigStore(Connection conn) {
        this.conn = conn;
    }

    public void initDefaults() throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("CREATE TABLE IF NOT EXISTS config (key TEXT PRIMARY KEY, value TEXT NOT NULL)")) {
            p.execute();
        }
        upsert("backoff_base", "2");
        upsert("max_retries", "3");
    }

    public String get(String key, String fallback) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("SELECT value FROM config WHERE key = ?")) {
            p.setString(1, key);
            ResultSet rs = p.executeQuery();
            if (rs.next()) return rs.getString(1);
            return fallback;
        }
    }

    public void upsert(String key, String value) throws SQLException {
        try (PreparedStatement p = conn.prepareStatement("INSERT INTO config(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            p.setString(1, key);
            p.setString(2, value);
            p.execute();
        }
    }
}
