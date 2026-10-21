package com.aliator.cashregister.v7;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(7)
public class V7ResetSchema implements CommandLineRunner {
    private final JdbcTemplate db;
    public V7ResetSchema(JdbcTemplate db) { this.db = db; }
    @Override public void run(String... args) {
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr7_password_resets (
                email VARCHAR(254) PRIMARY KEY,
                otp_hash CHAR(64) NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                expires_at TIMESTAMPTZ NOT NULL,
                last_sent_at TIMESTAMPTZ NOT NULL,
                hour_started TIMESTAMPTZ NOT NULL,
                hourly_count INTEGER NOT NULL DEFAULT 1
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr7_reset_rate_ip (
                ip_hash CHAR(64) PRIMARY KEY,
                window_started TIMESTAMPTZ NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 1
            )
            """);
    }
}
