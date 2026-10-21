package com.aliator.cashregister.v2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import org.springframework.core.annotation.Order;

@Component
@Order(1)
public class V2Schema implements CommandLineRunner {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(11);
    private final String demoPassword;
    V2Schema(JdbcTemplate db, @Value("${app.demo-owner-password:}") String demoPassword) {
        this.db = db; this.demoPassword = demoPassword;
    }
    @Override public void run(String... args) {
        // Additive, namespaced schema. Legacy v1 tables are never altered by this runner.
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS cr2_restaurants (id BIGSERIAL PRIMARY KEY, name VARCHAR(140) NOT NULL, slug VARCHAR(90) NOT NULL UNIQUE, currency VARCHAR(3) NOT NULL DEFAULT 'JPY', created_at TIMESTAMPTZ NOT NULL DEFAULT now())",
            "CREATE TABLE IF NOT EXISTS cr2_users (id BIGSERIAL PRIMARY KEY, restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id), email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL, role VARCHAR(20) NOT NULL DEFAULT 'OWNER', created_at TIMESTAMPTZ NOT NULL DEFAULT now())",
            "CREATE TABLE IF NOT EXISTS cr2_staff_sessions (token_hash CHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES cr2_users(id) ON DELETE CASCADE, expires_at TIMESTAMPTZ NOT NULL)",
            "CREATE TABLE IF NOT EXISTS cr2_tables (id BIGSERIAL PRIMARY KEY, restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id), code VARCHAR(30) NOT NULL, label VARCHAR(80) NOT NULL, active BOOLEAN NOT NULL DEFAULT true, qr_token VARCHAR(90) NOT NULL UNIQUE, UNIQUE(restaurant_id, code))",
            "CREATE TABLE IF NOT EXISTS cr2_menu_items (id BIGSERIAL PRIMARY KEY, restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id), category VARCHAR(80) NOT NULL, name VARCHAR(140) NOT NULL, description VARCHAR(300) NOT NULL DEFAULT '', emoji VARCHAR(24) NOT NULL DEFAULT '🍽', price_yen INT NOT NULL CHECK (price_yen BETWEEN 0 AND 1000000), active BOOLEAN NOT NULL DEFAULT true, sort_order INT NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS cr2_dining_sessions (id BIGSERIAL PRIMARY KEY, restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id), table_id BIGINT NOT NULL REFERENCES cr2_tables(id), status VARCHAR(20) NOT NULL DEFAULT 'OPEN', opened_at TIMESTAMPTZ NOT NULL DEFAULT now(), closed_at TIMESTAMPTZ)",
            "CREATE UNIQUE INDEX IF NOT EXISTS cr2_one_open_session_per_table ON cr2_dining_sessions(table_id) WHERE status = 'OPEN'",
            "CREATE TABLE IF NOT EXISTS cr2_guest_sessions (token_hash CHAR(64) PRIMARY KEY, dining_session_id BIGINT NOT NULL REFERENCES cr2_dining_sessions(id), expires_at TIMESTAMPTZ NOT NULL)",
            "CREATE TABLE IF NOT EXISTS cr2_orders (id BIGSERIAL PRIMARY KEY, restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id), dining_session_id BIGINT REFERENCES cr2_dining_sessions(id), table_code VARCHAR(30) NOT NULL, request_id UUID NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'NEW', demo BOOLEAN NOT NULL DEFAULT false, total_yen INT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now())",
            "CREATE UNIQUE INDEX IF NOT EXISTS cr2_idem_guest ON cr2_orders(dining_session_id,request_id) WHERE dining_session_id IS NOT NULL",
            "CREATE TABLE IF NOT EXISTS cr2_order_lines (id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES cr2_orders(id) ON DELETE CASCADE, menu_item_id BIGINT, name VARCHAR(140) NOT NULL, qty INT NOT NULL CHECK (qty > 0), unit_price_yen INT NOT NULL, note VARCHAR(180) NOT NULL DEFAULT '')",
            "CREATE INDEX IF NOT EXISTS cr2_orders_tenant_created ON cr2_orders(restaurant_id,created_at DESC)",
            "CREATE INDEX IF NOT EXISTS cr2_menu_tenant ON cr2_menu_items(restaurant_id, active)"
        };
        for (String statement : ddl) db.execute(statement);
        if (demoPassword == null || demoPassword.length() < 16) {
            throw new IllegalStateException("Set DEMO_OWNER_PASSWORD (at least 16 characters) before enabling v2");
        }
        List<Long> demo = db.query("SELECT id FROM cr2_restaurants WHERE slug='aliator-demo'", (rs, n) -> rs.getLong(1));
        long restaurantId = demo.isEmpty()
            ? db.queryForObject("INSERT INTO cr2_restaurants(name,slug) VALUES ('Aliator Demo Restaurant','aliator-demo') RETURNING id", Long.class)
            : demo.get(0);
        Integer hasOwner = db.queryForObject("SELECT count(*) FROM cr2_users WHERE restaurant_id=?", Integer.class, restaurantId);
        if (hasOwner == 0) db.update("INSERT INTO cr2_users(restaurant_id,email,password_hash,role) VALUES (?,?,?,'OWNER')",
            restaurantId, "demo-owner@aliator.local", bcrypt.encode(demoPassword));
        Integer hasTable = db.queryForObject("SELECT count(*) FROM cr2_tables WHERE restaurant_id=?", Integer.class, restaurantId);
        if (hasTable == 0) {
            for (int i=1; i<=8; i++) db.update("INSERT INTO cr2_tables(restaurant_id,code,label,qr_token) VALUES (?,?,?,?)",
                restaurantId, String.format("T%02d", i), "Table " + i, V2Tokens.random());
        }
        Integer hasMenu = db.queryForObject("SELECT count(*) FROM cr2_menu_items WHERE restaurant_id=?", Integer.class, restaurantId);
        if (hasMenu == 0) {
            List<Object[]> menu = List.of(
              new Object[]{"Mains","Signature Ramen","Tonkotsu broth and fresh noodles","🍜",1280,1},
              new Object[]{"Mains","Chicken Katsu Curry","Crispy chicken and Japanese curry","🍛",1380,2},
              new Object[]{"Mains","Teriyaki Donburi","Grilled chicken rice bowl","🍚",1180,3},
              new Object[]{"Sides","Gyoza (6 pcs)","Pan-fried dumplings","🥟",580,4},
              new Object[]{"Sides","Karaage","Japanese fried chicken","🍗",680,5},
              new Object[]{"Drinks","Matcha Latte","Creamy matcha with milk","🍵",650,6},
              new Object[]{"Drinks","Yuzu Soda","Sparkling yuzu citrus","🍋",520,7},
              new Object[]{"Desserts","Mochi Trio","Three seasonal mochi","🍡",490,8}
            );
            for (Object[] m : menu) db.update("INSERT INTO cr2_menu_items(restaurant_id,category,name,description,emoji,price_yen,sort_order) VALUES (?,?,?,?,?,?,?)",
                restaurantId,m[0],m[1],m[2],m[3],m[4],m[5]);
        }
    }
}
