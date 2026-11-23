package dev.evraktakip.web;

import dev.evraktakip.guvenlik.KullaniciDetay;
import dev.evraktakip.servis.DenetimServisi;
import dev.evraktakip.servis.TakvimServisi;
import dev.evraktakip.web.Dto.DenetimYaniti;
import dev.evraktakip.web.Dto.TatilIstegi;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Denetim ve yönetim")
@RestController
@RequestMapping("/api")
public class YonetimController {
    private final DenetimServisi denetim;
    private final TakvimServisi takvim;

    public YonetimController(DenetimServisi denetim, TakvimServisi takvim) {
        this.denetim = denetim;
        this.takvim = takvim;
    }

    /** Denetim kaydı yalnızca okunur; ekleme/değiştirme/silme uç noktası yoktur. */
    @GetMapping("/denetim")
    @PreAuthorize("hasAnyRole('DENETCI','ADMIN','UST_YONETICI')")
    public Page<DenetimYaniti> denetim(@RequestParam(required = false) String varlikTuru,
                                       @RequestParam(required = false) Long varlikId,
                                       @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.DESC) Pageable sayfa) {
        return denetim.listele(varlikTuru, varlikId, sayfa).map(DenetimYaniti::of);
    }

    @PostMapping("/resmi-tatiller")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public void tatilEkle(@AuthenticationPrincipal KullaniciDetay k, @Valid @RequestBody TatilIstegi istek) {
        takvim.tatilEkle(k.kullanici(), istek.tarih(), istek.ad());
    }
}
