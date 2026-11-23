package dev.evraktakip.domain;

public enum SevkDurumu {
    BEKLIYOR,
    ALINDI,
    /** Evrak başka bir kişiye/birime yeniden havale edildi. */
    DEVREDILDI,
    TAMAMLANDI
}
