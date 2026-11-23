package dev.evraktakip.servis;

import org.springframework.data.domain.Sort;

public enum BilgiEdinmeSiralama {
    YENI(Sort.by(Sort.Direction.DESC, "id")),
    ESKI(Sort.by(Sort.Direction.ASC, "id")),
    /** Süresi en yakın olan üstte. */
    SURE_YAKIN(Sort.by(Sort.Order.asc("sureSonu"), Sort.Order.desc("id")));

    private final Sort sort;

    BilgiEdinmeSiralama(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
