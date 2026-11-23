package dev.evraktakip.domain;

/** Gizlilik derecesi. GIZLI evrak yalnızca işlem zincirindeki kişilere ve üst yöneticiye görünür. */
public enum Gizlilik {
    NORMAL,
    HIZMETE_OZEL,
    GIZLI
}
