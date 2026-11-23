package dev.evraktakip.domain;

public enum Rol {
    /** Genel evrak kayıt birimi: gelen evrakı kaydeder ve ilk havaleyi yapar. */
    KAYIT_MEMURU,
    BIRIM_PERSONELI,
    /** Birim amiri (şef/müdür): havale eder, giden evrakı imzalar. */
    BIRIM_AMIRI,
    UST_YONETICI,
    BILGI_EDINME_SORUMLUSU,
    ARSIV_SORUMLUSU,
    DENETCI,
    ADMIN;

    /** Birim ile sınırlı roller yalnızca kendi birimlerine ait evrak ve başvuruları görür. */
    public boolean birimleSinirli() {
        return this == BIRIM_PERSONELI || this == BIRIM_AMIRI;
    }
}
