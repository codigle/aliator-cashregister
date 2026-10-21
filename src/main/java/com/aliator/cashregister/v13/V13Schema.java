package com.aliator.cashregister.v13;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Only adds language metadata; never changes payment, receipt or printer tables. */
@Component
@Order(970)
public class V13Schema implements CommandLineRunner {
    private final JdbcTemplate db;
    public V13Schema(JdbcTemplate db) { this.db=db; }
    @Override public void run(String... args) {
        db.execute("ALTER TABLE cr9_translations ADD COLUMN IF NOT EXISTS preparation VARCHAR(900) NOT NULL DEFAULT ''");
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr13_group_i18n (
              group_id BIGINT NOT NULL REFERENCES cr9_option_groups(id) ON DELETE CASCADE,
              language VARCHAR(2) NOT NULL CHECK(language IN ('en','zh','ko','tr')),
              label VARCHAR(200) NOT NULL,
              PRIMARY KEY(group_id,language))""");
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr13_value_i18n (
              value_id BIGINT NOT NULL REFERENCES cr9_option_values(id) ON DELETE CASCADE,
              language VARCHAR(2) NOT NULL CHECK(language IN ('en','zh','ko','tr')),
              label VARCHAR(200) NOT NULL,
              PRIMARY KEY(value_id,language))""");
        System.out.println("V13_MENU_I18N_SCHEMA=READY");
    }
}
