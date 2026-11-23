package dev.evraktakip.repo;

import dev.evraktakip.domain.Kullanici;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KullaniciDeposu extends JpaRepository<Kullanici, Long> {
    Optional<Kullanici> findByKullaniciAdi(String kullaniciAdi);

    boolean existsByKullaniciAdi(String kullaniciAdi);

    java.util.List<Kullanici> findAllByOrderByKullaniciAdiAsc();

    java.util.List<Kullanici> findByBirimIdAndDurumOrderByAdSoyadAsc(Long birimId, dev.evraktakip.domain.KullaniciDurumu durum);
}
