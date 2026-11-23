package dev.evraktakip.repo;

import dev.evraktakip.domain.ResmiTatil;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResmiTatilDeposu extends JpaRepository<ResmiTatil, LocalDate> {}
