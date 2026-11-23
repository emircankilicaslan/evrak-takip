package dev.evraktakip.domain;

import jakarta.persistence.*;

/** Dosya planı kodu ve saklama süresi. saklamaYili boşsa evrak süresiz saklanır. */
@Entity
@Table(name = "dosya_plani")
public class DosyaPlani {
    @Id
    private String kod;

    @Column(nullable = false)
    private String ad;

    @Column(name = "saklama_yili")
    private Integer saklamaYili;

    protected DosyaPlani() {}

    public String getKod() { return kod; }
    public String getAd() { return ad; }
    public Integer getSaklamaYili() { return saklamaYili; }
}
