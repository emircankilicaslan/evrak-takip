package dev.evraktakip.web;

import dev.evraktakip.domain.Aciliyet;
import dev.evraktakip.domain.EvrakDurumu;
import dev.evraktakip.domain.EvrakYonu;
import dev.evraktakip.domain.Gizlilik;
import dev.evraktakip.guvenlik.KullaniciDetay;
import dev.evraktakip.servis.EvrakFiltre;
import dev.evraktakip.servis.EvrakServisi;
import dev.evraktakip.servis.EvrakSiralama;
import dev.evraktakip.web.Dto.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Evrak")
@RestController
@RequestMapping("/api/evraklar")
@PreAuthorize("hasAnyRole('KAYIT_MEMURU','BIRIM_PERSONELI','BIRIM_AMIRI','UST_YONETICI','ARSIV_SORUMLUSU','DENETCI')")
public class EvrakController {
    private static final int EN_COK_SAYFA_BOYUTU = 100;

    private final EvrakServisi servis;

    public EvrakController(EvrakServisi servis) {
        this.servis = servis;
    }

    /** Kullanıcının görebildiği evraklar; gizlilik ve birim kuralları serviste uygulanır. */
    @GetMapping
    public Page<EvrakYaniti> listele(@AuthenticationPrincipal KullaniciDetay k,
                                     @RequestParam(required = false) String ara,
                                     @RequestParam(required = false) EvrakYonu yon,
                                     @RequestParam(required = false) EvrakDurumu durum,
                                     @RequestParam(required = false) Gizlilik gizlilik,
                                     @RequestParam(required = false) Aciliyet aciliyet,
                                     @RequestParam(required = false) Long birimId,
                                     @RequestParam(required = false) Boolean geciken,
                                     @RequestParam(defaultValue = "YENI") EvrakSiralama sirala,
                                     @RequestParam(defaultValue = "0") int sayfa,
                                     @RequestParam(defaultValue = "15") int boyut) {
        var filtre = new EvrakFiltre(ara, yon, durum, gizlilik, aciliyet, birimId, geciken);
        var istek = PageRequest.of(Math.max(sayfa, 0), Math.min(Math.max(boyut, 1), EN_COK_SAYFA_BOYUTU), sirala.sort());
        return servis.listele(k.kullanici(), filtre, istek);
    }

    @GetMapping("/{id}")
    public EvrakDetayYaniti detay(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.detay(k.kullanici(), id);
    }

    @GetMapping("/dosya-plani")
    public List<DosyaPlaniYaniti> dosyaPlani() {
        return servis.dosyaPlani();
    }

    @GetMapping("/imha-adaylari")
    @PreAuthorize("hasAnyRole('ARSIV_SORUMLUSU','UST_YONETICI','DENETCI')")
    public Page<EvrakYaniti> imhaAdaylari(@AuthenticationPrincipal KullaniciDetay k,
                                          @RequestParam(defaultValue = "0") int sayfa,
                                          @RequestParam(defaultValue = "15") int boyut) {
        var istek = PageRequest.of(Math.max(sayfa, 0), Math.min(Math.max(boyut, 1), EN_COK_SAYFA_BOYUTU));
        return servis.imhaAdaylari(k.kullanici(), istek);
    }

    @PostMapping("/gelen")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('KAYIT_MEMURU')")
    public EvrakYaniti gelenKaydet(@AuthenticationPrincipal KullaniciDetay k, @Valid @RequestBody GelenEvrakIstegi istek) {
        return servis.gelenKaydet(k.kullanici(), istek);
    }

    @PostMapping("/giden")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BIRIM_PERSONELI','BIRIM_AMIRI')")
    public EvrakYaniti gidenOlustur(@AuthenticationPrincipal KullaniciDetay k, @Valid @RequestBody GidenEvrakIstegi istek) {
        return servis.gidenOlustur(k.kullanici(), istek);
    }

    @PostMapping("/{id}/sevk")
    @PreAuthorize("hasAnyRole('KAYIT_MEMURU','BIRIM_AMIRI','UST_YONETICI')")
    public SevkYaniti sevk(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                           @Valid @RequestBody SevkIstegi istek) {
        return servis.sevkEt(k.kullanici(), id, istek);
    }

    @PostMapping("/sevkler/{sevkId}/teslim-al")
    @PreAuthorize("hasAnyRole('BIRIM_PERSONELI','BIRIM_AMIRI')")
    public SevkYaniti teslimAl(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long sevkId) {
        return servis.teslimAl(k.kullanici(), sevkId);
    }

    @PostMapping("/{id}/tamamla")
    @PreAuthorize("hasAnyRole('BIRIM_PERSONELI','BIRIM_AMIRI')")
    public EvrakYaniti tamamla(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                               @Valid @RequestBody TamamlaIstegi istek) {
        return servis.tamamla(k.kullanici(), id, istek);
    }

    @PostMapping("/{id}/imzaya-gonder")
    @PreAuthorize("hasAnyRole('BIRIM_PERSONELI','BIRIM_AMIRI')")
    public EvrakYaniti imzayaGonder(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.imzayaGonder(k.kullanici(), id);
    }

    @PostMapping("/{id}/imzala")
    @PreAuthorize("hasRole('BIRIM_AMIRI')")
    public EvrakYaniti imzala(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id) {
        return servis.imzala(k.kullanici(), id);
    }

    @PostMapping("/{id}/imza-reddet")
    @PreAuthorize("hasRole('BIRIM_AMIRI')")
    public EvrakYaniti imzaReddet(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                                  @Valid @RequestBody GerekceIstegi istek) {
        return servis.imzaReddet(k.kullanici(), id, istek.gerekce());
    }

    @PostMapping("/{id}/arsivle")
    @PreAuthorize("hasAnyRole('ARSIV_SORUMLUSU','UST_YONETICI')")
    public EvrakYaniti arsivle(@AuthenticationPrincipal KullaniciDetay k, @PathVariable Long id,
                               @Valid @RequestBody ArsivIstegi istek) {
        return servis.arsivle(k.kullanici(), id, istek.dosyaKodu());
    }
}
