package dev.evraktakip.servis;

import dev.evraktakip.domain.Aciliyet;
import dev.evraktakip.domain.EvrakDurumu;
import dev.evraktakip.domain.EvrakYonu;
import dev.evraktakip.domain.Gizlilik;

/** Evrak listesi süzgeci; boş alanlar süzgece katılmaz. geciken: cevap süresi geçmiş açık gelen evrak. */
public record EvrakFiltre(
        String ara,
        EvrakYonu yon,
        EvrakDurumu durum,
        Gizlilik gizlilik,
        Aciliyet aciliyet,
        Long birimId,
        Boolean geciken) {

    public static final int EN_COK_ARAMA_UZUNLUGU = 100;

    public static final EvrakFiltre BOS = new EvrakFiltre(null, null, null, null, null, null, null);
}
