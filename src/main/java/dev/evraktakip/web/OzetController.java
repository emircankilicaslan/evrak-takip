package dev.evraktakip.web;

import dev.evraktakip.domain.EvrakDurumu;
import dev.evraktakip.guvenlik.KullaniciDetay;
import dev.evraktakip.servis.BilgiEdinmeServisi;
import dev.evraktakip.servis.EvrakServisi;
import dev.evraktakip.web.Dto.OzetYaniti;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Özet")
@RestController
@RequestMapping("/api/ozet")
public class OzetController {
    private final EvrakServisi evraklar;
    private final BilgiEdinmeServisi basvurular;

    public OzetController(EvrakServisi evraklar, BilgiEdinmeServisi basvurular) {
        this.evraklar = evraklar;
        this.basvurular = basvurular;
    }

    /** Kullanıcının görebildiği kayıtlar üzerinden sayılar; yetkisiz alanlar sıfır döner. */
    @GetMapping
    public OzetYaniti ozet(@AuthenticationPrincipal KullaniciDetay k) {
        var durumlar = evraklar.durumSayilari(k.kullanici());
        var be = basvurular.ozet(k.kullanici());
        return new OzetYaniti(durumlar, evraklar.gecikenSayisi(k.kullanici()),
                durumlar.get(EvrakDurumu.IMZA_BEKLIYOR), be.acik(), be.geciken(), be.yaklasan());
    }
}
