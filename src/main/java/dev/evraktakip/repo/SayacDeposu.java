package dev.evraktakip.repo;

import dev.evraktakip.domain.Sayac;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SayacDeposu extends JpaRepository<Sayac, String> {
    /** Satır kilidi: aynı anda gelen iki kayıt aynı numarayı alamaz. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sayac s where s.anahtar = :anahtar")
    Optional<Sayac> kilitle(@Param("anahtar") String anahtar);
}
