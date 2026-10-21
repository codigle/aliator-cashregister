package com.aliator.cashregister.v9;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Additive tenant-aware structures; never rewrites orders or payments. */
@Component
@Order(950)
public class V9Schema implements CommandLineRunner {
    private final JdbcTemplate db;
    public V9Schema(JdbcTemplate db) {this.db=db;}
    @Override public void run(String... args) {
        String[] ddl={
          """
          CREATE TABLE IF NOT EXISTS cr9_menu_settings (
               restaurant_id BIGINT PRIMARY KEY REFERENCES cr2_restaurants(id),
               source_language VARCHAR(2) NOT NULL DEFAULT 'ja',
               menu_languages VARCHAR(40) NOT NULL DEFAULT 'ja,en',
               auto_translate BOOLEAN NOT NULL DEFAULT FALSE,
               updated_at TIMESTAMPTZ NOT NULL DEFAULT now())"""
  ,
          """
          CREATE TABLE IF NOT EXISTS cr9_menu_details (
               menu_item_id BIGINT PRIMARY KEY REFERENCES cr2_menu_items(id) ON DELETE CASCADE,
               ingredients_ja VARCHAR(600) NOT NULL DEFAULT '',
               allergens_ja VARCHAR(300) NOT NULL DEFAULT '',
               preparation_ja VARCHAR(600) NOT NULL DEFAULT '',
               spice_level INTEGER NOT NULL DEFAULT 0 CHECK(spice_level BETWEEN 0 AND 5))"""
  ,
          """
          CREATE TABLE IF NOT EXISTS cr9_option_groups (
               id BIGSERIAL PRIMARY KEY,
               menu_item_id BIGINT NOT NULL REFERENCES cr2_menu_items(id) ON DELETE CASCADE,
               label_ja VARCHAR(90) NOT NULL, label_en VARCHAR(90) NOT NULL,
               min_choices INTEGER NOT NULL DEFAULT 0, max_choices INTEGER NOT NULL DEFAULT 1,
               active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INTEGER NOT NULL DEFAULT 0,
               CHECK(min_choices>=0 AND max_choices BETWEEN 1 AND 8 AND min_choices<=max_choices))"""
  ,
          """
          CREATE TABLE IF NOT EXISTS cr9_option_values (
               id BIGSERIAL PRIMARY KEY,
               group_id BIGINT NOT NULL REFERENCES cr9_option_groups(id) ON DELETE CASCADE,
               label_ja VARCHAR(100) NOT NULL, label_en VARCHAR(100) NOT NULL,
               delta_yen INTEGER NOT NULL DEFAULT 0 CHECK(delta_yen BETWEEN -2000 AND 10000),
               active BOOLEAN NOT NULL DEFAULT TRUE, sort_order INTEGER NOT NULL DEFAULT 0)"""
  ,
          """
          CREATE TABLE IF NOT EXISTS cr9_translations (
               menu_item_id BIGINT NOT NULL REFERENCES cr2_menu_items(id) ON DELETE CASCADE,
               language VARCHAR(2) NOT NULL CHECK(language IN ('ja','en','zh','ko','tr')),
               name VARCHAR(180) NOT NULL DEFAULT '',description VARCHAR(750) NOT NULL DEFAULT '',
               ingredients VARCHAR(900) NOT NULL DEFAULT '',allergens VARCHAR(400) NOT NULL DEFAULT '',
               translated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
               PRIMARY KEY(menu_item_id,language))"""
  ,
          """
          CREATE TABLE IF NOT EXISTS cr9_translation_jobs (
               menu_item_id BIGINT NOT NULL REFERENCES cr2_menu_items(id) ON DELETE CASCADE,
               language VARCHAR(2) NOT NULL CHECK(language IN ('ja','en','zh','ko','tr')),
               state VARCHAR(12) NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0,
               error_code VARCHAR(100) NOT NULL DEFAULT '', updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
               PRIMARY KEY(menu_item_id,language))"""
  ,
          """
          CREATE TABLE IF NOT EXISTS cr9_demo_pay (
               id UUID PRIMARY KEY, restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id),
               table_id BIGINT NOT NULL REFERENCES cr2_tables(id),
               dining_session_id BIGINT NOT NULL REFERENCES cr2_dining_sessions(id),
               token_hash CHAR(64) NOT NULL UNIQUE, amount_yen INTEGER NOT NULL CHECK(amount_yen>0),
               status VARCHAR(30) NOT NULL DEFAULT 'OPEN', attempts INTEGER NOT NULL DEFAULT 0,
               cashier_id BIGINT REFERENCES cr2_users(id), payment_id BIGINT REFERENCES cr3_payments(id),
               expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
               updated_at TIMESTAMPTZ NOT NULL DEFAULT now())"""
  ,
          "CREATE INDEX IF NOT EXISTS cr9_groups_menu ON cr9_option_groups(menu_item_id)",
          "CREATE INDEX IF NOT EXISTS cr9_demo_pay_tenant ON cr9_demo_pay(restaurant_id,dining_session_id,status)",
          "CREATE INDEX IF NOT EXISTS cr9_translation_queue ON cr9_translation_jobs(state,updated_at)"
        };
        for(String sql:ddl)db.execute(sql);
        System.out.println("V9_SCHEMA=READY");
    }
}
