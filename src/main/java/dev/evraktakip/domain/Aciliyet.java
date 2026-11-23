package dev.evraktakip.domain;

public enum Aciliyet {
    NORMAL,
    /** Cevap süresi yapılandırmadan gelir (varsayılan 3 iş günü). */
    ACELE,
    /** Cevap süresi evrakta belirtilir; kayıt sırasında iş günü olarak girilir. */
    GUNLUDUR
}
