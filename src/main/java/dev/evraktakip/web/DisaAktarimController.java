package dev.evraktakip.web;

import dev.evraktakip.domain.Aciliyet;
import dev.evraktakip.domain.BasvuruDurumu;
import dev.evraktakip.domain.EvrakDurumu;
import dev.evraktakip.domain.EvrakYonu;
import dev.evraktakip.domain.Gizlilik;
import dev.evraktakip.guvenlik.KullaniciDetay;
import dev.evraktakip.servis.BilgiEdinmeFiltre;
import dev.evraktakip.servis.BilgiEdinmeSiralama;
import dev.evraktakip.servis.DisaAktarimServisi;
import dev.evraktakip.servis.DisaAktarimServisi.Dosya;
import dev.evraktakip.servis.EvrakFiltre;
import dev.evraktakip.servis.EvrakSiralama;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Dışa aktarma")
@RestController
@RequestMapping("/api/disa-aktar")
public class DisaAktarimController {
    private final DisaAktarimServisi servis;

    public DisaAktarimController(DisaAktarimServisi servis) {
        this.servis = servis;
    }

    /** Kullanıcının görebildiği evraklar; gizlilik ve birim kuralları listedekiyle aynıdır. */
    @GetMapping("/evraklar")
    @PreAuthorize("hasAnyRole('KAYIT_MEMURU','BIRIM_PERSONELI','BIRIM_AMIRI','UST_YONETICI','ARSIV_SORUMLUSU','DENETCI')")
    public ResponseEntity<byte[]> evraklar(@AuthenticationPrincipal KullaniciDetay k,
                                           @RequestParam(defaultValue = "xlsx") String bicim,
                                           @RequestParam(required = false) String ara,
                                           @RequestParam(required = false) EvrakYonu yon,
                                           @RequestParam(required = false) EvrakDurumu durum,
                                           @RequestParam(required = false) Gizlilik gizlilik,
                                           @RequestParam(required = false) Aciliyet aciliyet,
                                           @RequestParam(required = false) Long birimId,
                                           @RequestParam(required = false) Boolean geciken,
                                           @RequestParam(defaultValue = "YENI") EvrakSiralama sirala) {
        var filtre = new EvrakFiltre(ara, yon, durum, gizlilik, aciliyet, birimId, geciken);
        return yanit(servis.evraklar(k.kullanici(), filtre, sirala, bicim));
    }

    @GetMapping("/bilgi-edinme")
    @PreAuthorize("hasAnyRole('BILGI_EDINME_SORUMLUSU','UST_YONETICI','DENETCI','BIRIM_PERSONELI','BIRIM_AMIRI')")
    public ResponseEntity<byte[]> basvurular(@AuthenticationPrincipal KullaniciDetay k,
                                             @RequestParam(defaultValue = "xlsx") String bicim,
                                             @RequestParam(required = false) String ara,
                                             @RequestParam(required = false) BasvuruDurumu durum,
                                             @RequestParam(required = false) Long birimId,
                                             @RequestParam(required = false) Boolean geciken,
                                             @RequestParam(defaultValue = "SURE_YAKIN") BilgiEdinmeSiralama sirala) {
        var filtre = new BilgiEdinmeFiltre(ara, durum, birimId, geciken);
        return yanit(servis.basvurular(k.kullanici(), filtre, sirala, bicim));
    }

    @GetMapping("/denetim")
    @PreAuthorize("hasAnyRole('DENETCI','ADMIN','UST_YONETICI')")
    public ResponseEntity<byte[]> denetim(@AuthenticationPrincipal KullaniciDetay k,
                                          @RequestParam(defaultValue = "xlsx") String bicim) {
        return yanit(servis.denetim(k.kullanici(), bicim));
    }

    private static ResponseEntity<byte[]> yanit(Dosya d) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.icerikTuru()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.ad()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(d.icerik());
    }
}
