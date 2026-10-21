package com.aliator.cashregister.v6;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(6)
public class V6RegistrationSchema implements CommandLineRunner {
    private final JdbcTemplate db;
    public V6RegistrationSchema(JdbcTemplate db) { this.db = db; }
    @Override public void run(String... args) {
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr6_pending_registrations (
              email VARCHAR(254) PRIMARY KEY,
              restaurant_name VARCHAR(140) NOT NULL,
              slug VARCHAR(90) NOT NULL UNIQUE,
              password_hash VARCHAR(100) NOT NULL,
              otp_hash CHAR(64) NOT NULL,
              attempts INTEGER NOT NULL DEFAULT 0,
              expires_at TIMESTAMPTZ NOT NULL,
              last_sent_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr6_registration_rate_ip (
              ip_hash CHAR(64) PRIMARY KEY,
              window_started TIMESTAMPTZ NOT NULL DEFAULT now(),
              attempt_count INTEGER NOT NULL DEFAULT 1
            )
            """);
    }
}
