package dev.evraktakip.domain;

public enum BasvuruDurumu {
    ALINDI,
    INCELEMEDE,
    CEVAPLANDI,
    REDDEDILDI,
    YONLENDIRILDI;

    public boolean acik() {
        return this == ALINDI || this == INCELEMEDE;
    }
}
