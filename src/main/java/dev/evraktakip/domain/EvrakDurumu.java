package dev.evraktakip.domain;

/**
 * Gelen evrak: KAYITLI → HAVALEDE → TAMAMLANDI → ARSIVLENDI.
 * Giden evrak: TASLAK → IMZA_BEKLIYOR → TAMAMLANDI (imzalandı, sayı verildi) → ARSIVLENDI.
 */
public enum EvrakDurumu {
    TASLAK,
    IMZA_BEKLIYOR,
    KAYITLI,
    HAVALEDE,
    TAMAMLANDI,
    ARSIVLENDI;

    /** Cevap süresinin işlediği, henüz sonuçlanmamış gelen evrak durumları. */
    public boolean islemde() {
        return this == KAYITLI || this == HAVALEDE;
    }
}
