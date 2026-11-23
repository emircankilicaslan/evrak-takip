package dev.evraktakip.web;

import dev.evraktakip.domain.KullaniciDurumu;
import dev.evraktakip.domain.Rol;
import dev.evraktakip.repo.BirimDeposu;
import dev.evraktakip.repo.KullaniciDeposu;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Birimler")
@RestController
@RequestMapping("/api/birimler")
public class BirimController {
    public record BirimYaniti(Long id, String kod, String ad) {}

    public record PersonelYaniti(Long id, String kullaniciAdi, String adSoyad, Rol rol) {}

    private final BirimDeposu birimler;
    private final KullaniciDeposu kullanicilar;

    public BirimController(BirimDeposu birimler, KullaniciDeposu kullanicilar) {
        this.birimler = birimler;
        this.kullanicilar = kullanicilar;
    }

    /** Arayüzdeki birim seçimi için; oturum açmış her kullanıcı okuyabilir. */
    @GetMapping
    public List<BirimYaniti> listele() {
        return birimler.findAll(Sort.by("ad")).stream()
                .map(b -> new BirimYaniti(b.getId(), b.getKod(), b.getAd()))
                .toList();
    }

    /** Havale hedefi seçimi için birimdeki aktif personel ve amirler. */
    @GetMapping("/{id}/personel")
    @PreAuthorize("hasAnyRole('KAYIT_MEMURU','BIRIM_AMIRI','UST_YONETICI')")
    public List<PersonelYaniti> personel(@PathVariable Long id) {
        return kullanicilar.findByBirimIdAndDurumOrderByAdSoyadAsc(id, KullaniciDurumu.AKTIF).stream()
                .filter(k -> k.getRol().birimleSinirli())
                .map(k -> new PersonelYaniti(k.getId(), k.getKullaniciAdi(), k.getAdSoyad(), k.getRol()))
                .toList();
    }
}
