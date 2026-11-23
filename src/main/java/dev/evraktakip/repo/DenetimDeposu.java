package dev.evraktakip.repo;

import dev.evraktakip.domain.DenetimKaydi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DenetimDeposu extends JpaRepository<DenetimKaydi, Long> {
    Page<DenetimKaydi> findByVarlikTuruAndVarlikId(String varlikTuru, Long varlikId, Pageable pageable);
}
