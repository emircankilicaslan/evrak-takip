package dev.evraktakip.domain;

import jakarta.persistence.*;

/** Yıl ve türe göre sıra numarası sayacı (ör. 2026-GEL). Satır kilidiyle artırılır. */
@Entity
@Table(name = "sayac")
public class Sayac {
    @Id
    private String anahtar;

    @Column(nullable = false)
    private long deger;

    protected Sayac() {}

    public Sayac(String anahtar) {
        this.anahtar = anahtar;
    }

    public long artir() {
        return ++deger;
    }
}
