package dev.evraktakip.repo;

import dev.evraktakip.domain.BilgiEdinme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BilgiEdinmeDeposu extends JpaRepository<BilgiEdinme, Long>, JpaSpecificationExecutor<BilgiEdinme> {}
