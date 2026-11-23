package dev.evraktakip.servis;

import dev.evraktakip.domain.Kullanici;
import dev.evraktakip.domain.ResmiTatil;
import dev.evraktakip.kural.IsGunuTakvimi;
import dev.evraktakip.repo.ResmiTatilDeposu;
import dev.evraktakip.servis.Hatalar.IsKuraliIhlali;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resmi tatil verisinden iş günü takvimi üretir; tatil ekleme yönetici işlemidir. */
@Service
public class TakvimServisi {
    private final ResmiTatilDeposu tatiller;
    private final DenetimServisi denetim;

    public TakvimServisi(ResmiTatilDeposu tatiller, DenetimServisi denetim) {
        this.tatiller = tatiller;
        this.denetim = denetim;
    }

    @Transactional(readOnly = true)
    public IsGunuTakvimi takvim() {
        return new IsGunuTakvimi(tatiller.findAll().stream().map(ResmiTatil::getTarih).toList());
    }

    @Transactional
    public void tatilEkle(Kullanici yonetici, LocalDate tarih, String ad) {
        if (tatiller.existsById(tarih)) {
            throw new IsKuraliIhlali("Bu tarih zaten resmi tatil olarak tanımlı");
        }
        tatiller.save(new ResmiTatil(tarih, ad));
        denetim.kaydet(yonetici.getKullaniciAdi(), "TATIL_EKLE", "RESMI_TATIL", null, tarih + " " + ad);
    }
}
