package dev.evraktakip.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 4982 sayılı Bilgi Edinme Hakkı Kanunu kapsamındaki başvuru. */
@Entity
@Table(name = "bilgi_edinme")
public class BilgiEdinme {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "basvuru_no", nullable = false, unique = true)
    private String basvuruNo;

    @Column(name = "basvuran_ad", nullable = false, length = 200)
    private String basvuranAd;

    /** Veri minimizasyonu (KVKK): kimlik numarasının yalnızca maskeli hali saklanır. */
    @Column(name = "tc_maskeli", length = 11)
    private String tcMaskeli;

    @Column(length = 300)
    private String iletisim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BasvuruKanali kanal;

    @Column(nullable = false, length = 1000)
    private String konu;

    @Column(name = "basvuru_tarihi", nullable = false)
    private LocalDate basvuruTarihi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BasvuruDurumu durum = BasvuruDurumu.ALINDI;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "birim_id")
    private Birim birim;

    @Column(name = "birim_gorusu", length = 1000)
    private String birimGorusu;

    @Column(name = "sure_sonu", nullable = false)
    private LocalDate sureSonu;

    @Column(name = "uzatma_sayisi", nullable = false)
    private int uzatmaSayisi;

    @Column(name = "uzatma_gerekcesi", length = 1000)
    private String uzatmaGerekcesi;

    /** Cevap metni özeti veya ret gerekçesi. */
    @Column(name = "sonuc_metni", length = 2000)
    private String sonucMetni;

    @Enumerated(EnumType.STRING)
    @Column(name = "ret_nedeni")
    private RetNedeni retNedeni;

    @Column(name = "sonuc_tarihi")
    private LocalDate sonucTarihi;

    @Column(name = "itiraz_son_tarihi")
    private LocalDate itirazSonTarihi;

    @Column(name = "yonlendirilen_kurum", length = 200)
    private String yonlendirilenKurum;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "olusturan_id")
    private Kullanici olusturan;

    @Column(name = "kayit_zamani", nullable = false)
    private LocalDateTime kayitZamani;

    @Version
    private long surum;

    protected BilgiEdinme() {}

    public BilgiEdinme(String basvuruNo, String basvuranAd, String tcMaskeli, String iletisim, BasvuruKanali kanal,
                       String konu, LocalDate basvuruTarihi, LocalDate sureSonu, Kullanici olusturan,
                       LocalDateTime kayitZamani) {
        this.basvuruNo = basvuruNo;
        this.basvuranAd = basvuranAd;
        this.tcMaskeli = tcMaskeli;
        this.iletisim = iletisim;
        this.kanal = kanal;
        this.konu = konu;
        this.basvuruTarihi = basvuruTarihi;
        this.sureSonu = sureSonu;
        this.olusturan = olusturan;
        this.kayitZamani = kayitZamani;
    }

    public Long getId() { return id; }
    public String getBasvuruNo() { return basvuruNo; }
    public String getBasvuranAd() { return basvuranAd; }
    public String getTcMaskeli() { return tcMaskeli; }
    public String getIletisim() { return iletisim; }
    public BasvuruKanali getKanal() { return kanal; }
    public String getKonu() { return konu; }
    public LocalDate getBasvuruTarihi() { return basvuruTarihi; }
    public BasvuruDurumu getDurum() { return durum; }
    public Birim getBirim() { return birim; }
    public String getBirimGorusu() { return birimGorusu; }
    public LocalDate getSureSonu() { return sureSonu; }
    public int getUzatmaSayisi() { return uzatmaSayisi; }
    public String getUzatmaGerekcesi() { return uzatmaGerekcesi; }
    public String getSonucMetni() { return sonucMetni; }
    public RetNedeni getRetNedeni() { return retNedeni; }
    public LocalDate getSonucTarihi() { return sonucTarihi; }
    public LocalDate getItirazSonTarihi() { return itirazSonTarihi; }
    public String getYonlendirilenKurum() { return yonlendirilenKurum; }
    public Kullanici getOlusturan() { return olusturan; }
    public LocalDateTime getKayitZamani() { return kayitZamani; }

    public void havaleEdildi(Birim birim) {
        this.birim = birim;
        this.durum = BasvuruDurumu.INCELEMEDE;
    }

    public void birimGorusuYaz(String gorus) {
        this.birimGorusu = gorus;
    }

    public void uzatildi(LocalDate yeniSureSonu, String gerekce) {
        this.sureSonu = yeniSureSonu;
        this.uzatmaSayisi++;
        this.uzatmaGerekcesi = gerekce;
    }

    public void sonuclandi(BasvuruDurumu durum, String metin, RetNedeni retNedeni, LocalDate tarih,
                           LocalDate itirazSonTarihi) {
        this.durum = durum;
        this.sonucMetni = metin;
        this.retNedeni = retNedeni;
        this.sonucTarihi = tarih;
        this.itirazSonTarihi = itirazSonTarihi;
    }

    public void yonlendirildi(String kurum, LocalDate tarih) {
        this.durum = BasvuruDurumu.YONLENDIRILDI;
        this.yonlendirilenKurum = kurum;
        this.sonucTarihi = tarih;
    }
}
