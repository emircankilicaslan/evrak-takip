package dev.evraktakip.web;

import dev.evraktakip.domain.BasvuruDurumu;
import dev.evraktakip.guvenlik.KullaniciDetay;
import dev.evraktakip.servis.BilgiEdinmeFiltre;
import dev.evraktakip.servis.BilgiEdinmeServisi;
import dev.evraktakip.servis.BilgiEdinmeSiralama;
import dev.evraktakip.web.Dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Bilgi edinme (4982)")
@RestController
@RequestMapping("/api/bilgi-edinme")
public class BilgiEdinmeController {
    private static final int EN_COK_SAYFA_BOYUTU = 100;

    private final BilgiEdinmeServisi servis;

    public BilgiEdinmeController(BilgiEdinmeServisi servis) {
        this.servis = servis;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BILGI_EDINME_SORUMLUSU','UST_YONETICI','DENETCI','BIRIM_PERSONELI','BIRIM_AMIRI')")
    public Page<BasvuruYaniti> listele(@AuthenticationPrincipal KullaniciDetay k,
                                       @RequestParam(required = false) String ara,
                                       @RequestParam(required = false) BasvuruDurumu durum,
                                       @RequestParam(required = false) Long birimId,
                                       @RequestParam(required = false) Boolean geciken,
                                       @RequestParam(defaultValue = "SURE_YAKIN") BilgiEdinmeSiralama sirala,
                                       @RequestParam(defaultValue = "0") int sayfa,
                                       @RequestParam(defaultValue = "15") int boyut) {
        var istek = PageRequest.of(Math.max(sayfa, 0), Math.min(Math.max(boyut, 1), EN_COK_SAYFA_BOYUTU), sirala.sort());
        return servis.listele(k.kullanici(), new BilgiEdinmeFiltre(ara, durum, birimId, geciken), istek);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BILGI_EDINME_SORUMLUSU','UST_YONETICI','DENETCI','BIRIM_PERSONELI','BIRIM_AMIRI')")
    public BasvuruYaniti detay(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.detay(k.kullanici(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BILGI_EDINME_SORUMLUSU','KAYIT_MEMURU')")
    public BasvuruYaniti olustur(@AuthenticationPrincipal KullaniciDetay k, @Valid @RequestBody BasvuruIstegi istek) {
        return servis.olustur(k.kullanici(), istek);
    }

    @PostMapping("/{id}/havale")
    @PreAuthorize("hasRole('BILGI_EDINME_SORUMLUSU')")
    public BasvuruYaniti havale(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                @Valid @RequestBody HavaleIstegi istek) {
        return servis.havale(k.kullanici(), id, istek.birimId());
    }

    @PostMapping("/{id}/birim-gorusu")
    @PreAuthorize("hasAnyRole('BIRIM_PERSONELI','BIRIM_AMIRI')")
    public BasvuruYaniti birimGorusu(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                     @Valid @RequestBody MetinIstegi istek) {
        return servis.birimGorusu(k.kullanici(), id, istek.metin());
    }

    @PostMapping("/{id}/uzat")
    @PreAuthorize("hasRole('BILGI_EDINME_SORUMLUSU')")
    public BasvuruYaniti uzat(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                              @Valid @RequestBody GerekceIstegi istek) {
        return servis.uzat(k.kullanici(), id, istek.gerekce());
    }

    @PostMapping("/{id}/cevapla")
    @PreAuthorize("hasRole('BILGI_EDINME_SORUMLUSU')")
    public BasvuruYaniti cevapla(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                 @Valid @RequestBody CevapIstegi istek) {
        return servis.cevapla(k.kullanici(), id, istek.metin());
    }

    @PostMapping("/{id}/reddet")
    @PreAuthorize("hasRole('BILGI_EDINME_SORUMLUSU')")
    public BasvuruYaniti reddet(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                @Valid @RequestBody RetIstegi istek) {
        return servis.reddet(k.kullanici(), id, istek.nedeni(), istek.gerekce());
    }

    @PostMapping("/{id}/yonlendir")
    @PreAuthorize("hasRole('BILGI_EDINME_SORUMLUSU')")
    public BasvuruYaniti yonlendir(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                   @Valid @RequestBody YonlendirIstegi istek) {
        return servis.yonlendir(k.kullanici(), id, istek.kurum());
    }
}
