package com.aliator.cashregister.seed;

import com.aliator.cashregister.domain.MenuItem;
import com.aliator.cashregister.repo.MenuRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DemoDataSeeder implements CommandLineRunner {
    private final MenuRepository repository;
    public DemoDataSeeder(MenuRepository repository) { this.repository = repository; }

    @Override public void run(String... args) {
        if (repository.count() > 0) return;
        repository.saveAll(List.of(
            new MenuItem("Signature Ramen", "Mains", "Rich broth, handmade noodles, marinated egg", "🍜", 1280, 1),
            new MenuItem("Chicken Katsu Curry", "Mains", "Golden crispy chicken with Japanese curry", "🍛", 1380, 2),
            new MenuItem("Teriyaki Donburi", "Mains", "Grilled chicken, rice, seasonal vegetables", "🍚", 1180, 3),
            new MenuItem("Salmon Poke Bowl", "Mains", "Fresh salmon, sesame rice and avocado", "🍣", 1580, 4),
            new MenuItem("Gyoza (6 pcs)", "Sides", "Pan-fried dumplings, soy-vinegar dipping sauce", "🥟", 580, 5),
            new MenuItem("Karaage", "Sides", "Japanese fried chicken, yuzu mayo", "🍗", 680, 6),
            new MenuItem("Edamame", "Sides", "Lightly salted steamed soybeans", "🫛", 380, 7),
            new MenuItem("Matcha Latte", "Drinks", "Creamy Kyoto-style matcha with milk", "🍵", 650, 8),
            new MenuItem("Yuzu Soda", "Drinks", "Sparkling citrus with fresh yuzu", "🍋", 520, 9),
            new MenuItem("Iced Coffee", "Drinks", "Slow-brewed coffee over ice", "🧊", 450, 10),
            new MenuItem("Matcha Cheesecake", "Desserts", "Smooth matcha cheesecake", "🍰", 680, 11),
            new MenuItem("Mochi Trio", "Desserts", "Three seasonal mochi flavours", "🍡", 490, 12)
        ));
    }
}
