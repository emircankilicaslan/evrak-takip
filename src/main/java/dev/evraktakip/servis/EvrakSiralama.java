package dev.evraktakip.servis;

import org.springframework.data.domain.Sort;

/** Evrak listesi için izin verilen sıralamalar; istemciden ham alan adı kabul edilmez. */
public enum EvrakSiralama {
    YENI(Sort.by(Sort.Direction.DESC, "id")),
    ESKI(Sort.by(Sort.Direction.ASC, "id")),
    /** Cevap süresi en yakın olan üstte; süresi olmayanlar sonda. */
    SURE_YAKIN(Sort.by(Sort.Order.asc("cevapSuresiSonu").nullsLast(), Sort.Order.desc("id")));

    private final Sort sort;

    EvrakSiralama(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
