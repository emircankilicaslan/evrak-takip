package dev.evraktakip.servis;

import dev.evraktakip.domain.BasvuruDurumu;

/** Bilgi edinme listesi süzgeci. geciken: süresi geçmiş ve henüz sonuçlanmamış başvurular. */
public record BilgiEdinmeFiltre(String ara, BasvuruDurumu durum, Long birimId, Boolean geciken) {

    public static final BilgiEdinmeFiltre BOS = new BilgiEdinmeFiltre(null, null, null, null);
}
