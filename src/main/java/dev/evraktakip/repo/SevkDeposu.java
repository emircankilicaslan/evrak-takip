package dev.evraktakip.repo;

import dev.evraktakip.domain.Evrak;
import dev.evraktakip.domain.Sevk;
import dev.evraktakip.domain.SevkDurumu;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SevkDeposu extends JpaRepository<Sevk, Long> {
    List<Sevk> findByEvrakOrderByIdAsc(Evrak evrak);

    Optional<Sevk> findFirstByEvrakOrderByIdDesc(Evrak evrak);

    List<Sevk> findByEvrakAndDurumIn(Evrak evrak, Collection<SevkDurumu> durumlar);
}
