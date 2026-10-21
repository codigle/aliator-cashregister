package com.aliator.cashregister.repo;

import com.aliator.cashregister.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MenuRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByActiveTrueOrderBySortOrderAscIdAsc();
}
