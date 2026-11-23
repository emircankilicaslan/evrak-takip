package dev.evraktakip.repo;

import dev.evraktakip.domain.DosyaPlani;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DosyaPlaniDeposu extends JpaRepository<DosyaPlani, String> {}
