package dev.evraktakip.servis;

import dev.evraktakip.domain.Sayac;
import dev.evraktakip.repo.SayacDeposu;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Yıl ve türe göre kesintisiz sıra numarası üretir (ör. 2026-GEL → 1, 2, 3 ...). */
@Service
public class SayacServisi {
    private final SayacDeposu sayaclar;

    public SayacServisi(SayacDeposu sayaclar) {
        this.sayaclar = sayaclar;
    }

    /** Çağıranın işleminde çalışır: kayıt geri alınırsa numara da geri döner, numara atlanmaz. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long sonraki(String anahtar) {
        Sayac sayac = sayaclar.kilitle(anahtar).orElseGet(() -> sayaclar.saveAndFlush(new Sayac(anahtar)));
        return sayac.artir();
    }

    public static String bicim(String onEk, int yil, long sira) {
        return "%d-%s-%06d".formatted(yil, onEk, sira);
    }
}
