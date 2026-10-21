package com.aliator.cashregister.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "menu_items")
public class MenuItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 80)
    private String category;
    @Column(nullable = false, length = 240)
    private String description;
    @Column(nullable = false, length = 16)
    private String emoji;
    @Column(nullable = false)
    private int priceYen;
    @Column(nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private boolean active = true;

    protected MenuItem() {}

    public MenuItem(String name, String category, String description, String emoji, int priceYen, int sortOrder) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.emoji = emoji;
        this.priceYen = priceYen;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getEmoji() { return emoji; }
    public int getPriceYen() { return priceYen; }
    public boolean isActive() { return active; }
}
