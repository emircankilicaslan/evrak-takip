package dev.evraktakip.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Gelen evrakın bir birime veya kişiye havalesi. */
@Entity
@Table(name = "sevk")
public class Sevk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "evrak_id")
    private Evrak evrak;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sevk_eden_id")
    private Kullanici sevkEden;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "hedef_birim_id")
    private Birim hedefBirim;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hedef_kullanici_id")
    private Kullanici hedefKullanici;

    @Column(length = 500)
    private String talimat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SevkDurumu durum = SevkDurumu.BEKLIYOR;

    @Column(name = "sevk_zamani", nullable = false)
    private LocalDateTime sevkZamani;

    @Column(name = "alinma_zamani")
    private LocalDateTime alinmaZamani;

    protected Sevk() {}

    public Sevk(Evrak evrak, Kullanici sevkEden, Birim hedefBirim, Kullanici hedefKullanici,
                String talimat, LocalDateTime sevkZamani) {
        this.evrak = evrak;
        this.sevkEden = sevkEden;
        this.hedefBirim = hedefBirim;
        this.hedefKullanici = hedefKullanici;
        this.talimat = talimat;
        this.sevkZamani = sevkZamani;
    }

    public Long getId() { return id; }
    public Evrak getEvrak() { return evrak; }
    public Kullanici getSevkEden() { return sevkEden; }
    public Birim getHedefBirim() { return hedefBirim; }
    public Kullanici getHedefKullanici() { return hedefKullanici; }
    public String getTalimat() { return talimat; }
    public SevkDurumu getDurum() { return durum; }
    public LocalDateTime getSevkZamani() { return sevkZamani; }
    public LocalDateTime getAlinmaZamani() { return alinmaZamani; }

    public void teslimAlindi(LocalDateTime zaman) {
        this.durum = SevkDurumu.ALINDI;
        this.alinmaZamani = zaman;
    }

    public void durumBelirle(SevkDurumu durum) {
        this.durum = durum;
    }
}
