package com.aliator.cashregister.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "order_lines")
public class OrderLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long menuItemId;
    @Column(nullable = false, length = 120)
    private String itemName;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private int unitPriceYen;
    @Column(length = 180)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private RestaurantOrder order;

    protected OrderLine() {}

    public void setOrder(RestaurantOrder order) { this.order = order; }

    public OrderLine(MenuItem item, int quantity, String note) {
        this.menuItemId = item.getId();
        this.itemName = item.getName();
        this.quantity = quantity;
        this.unitPriceYen = item.getPriceYen();
        this.note = note == null ? "" : note.trim();
    }

    public Long getId() { return id; }
    public Long getMenuItemId() { return menuItemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public int getUnitPriceYen() { return unitPriceYen; }
    public String getNote() { return note; }
}
