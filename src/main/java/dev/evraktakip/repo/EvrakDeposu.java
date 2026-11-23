package dev.evraktakip.repo;

import dev.evraktakip.domain.Evrak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EvrakDeposu extends JpaRepository<Evrak, Long>, JpaSpecificationExecutor<Evrak> {}
