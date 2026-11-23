package dev.evraktakip.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "evrak")
public class Evrak {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Gelen evrakta kayıtta, giden evrakta imzada verilir; o zamana dek boştur. */
    @Column(name = "kayit_no", unique = true)
    private String kayitNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvrakYonu yon;

    @Column(nullable = false, length = 300)
    private String konu;

    /** Gelen evrakta gönderen, giden evrakta alıcı kurum. */
    @Column(name = "ilgili_kurum", nullable = false, length = 200)
    private String ilgiliKurum;

    @Column(name = "evrak_tarihi", nullable = false)
    private LocalDate evrakTarihi;

    @Column(name = "dis_sayi", length = 60)
    private String disSayi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gizlilik gizlilik;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Aciliyet aciliyet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvrakDurumu durum;

    /** Evrakın şu an bulunduğu birim; kayıtta henüz havale edilmemiş gelen evrakta boştur. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "birim_id")
    private Birim birim;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "olusturan_id")
    private Kullanici olusturan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "imzalayan_id")
    private Kullanici imzalayan;

    @Column(name = "imza_zamani")
    private LocalDateTime imzaZamani;

    @Column(name = "red_gerekcesi", length = 1000)
    private String redGerekcesi;

    @Column(name = "cevap_suresi_sonu")
    private LocalDate cevapSuresiSonu;

    /** Giden evrak: cevap verdiği gelen evrakın numarası. */
    @Column(name = "ilgili_evrak_id")
    private Long ilgiliEvrakId;

    /** Gelen evrak: cevap olarak bağlanan giden evrakın numarası. */
    @Column(name = "cevap_evrak_id")
    private Long cevapEvrakId;

    @Column(name = "tamamlama_notu", length = 1000)
    private String tamamlamaNotu;

    @Column(name = "dosya_kodu")
    private String dosyaKodu;

    @Column(name = "arsiv_tarihi")
    private LocalDate arsivTarihi;

    @Column(name = "imha_tarihi")
    private LocalDate imhaTarihi;

    @Column(name = "kayit_zamani", nullable = false)
    private LocalDateTime kayitZamani;

    @Version
    private long surum;

    protected Evrak() {}

    public Evrak(EvrakYonu yon, String konu, String ilgiliKurum, LocalDate evrakTarihi, String disSayi,
                 Gizlilik gizlilik, Aciliyet aciliyet, EvrakDurumu durum, Birim birim, Kullanici olusturan,
                 LocalDateTime kayitZamani) {
        this.yon = yon;
        this.konu = konu;
        this.ilgiliKurum = ilgiliKurum;
        this.evrakTarihi = evrakTarihi;
        this.disSayi = disSayi;
        this.gizlilik = gizlilik;
        this.aciliyet = aciliyet;
        this.durum = durum;
        this.birim = birim;
        this.olusturan = olusturan;
        this.kayitZamani = kayitZamani;
    }

    public Long getId() { return id; }
    public String getKayitNo() { return kayitNo; }
    public EvrakYonu getYon() { return yon; }
    public String getKonu() { return konu; }
    public String getIlgiliKurum() { return ilgiliKurum; }
    public LocalDate getEvrakTarihi() { return evrakTarihi; }
    public String getDisSayi() { return disSayi; }
    public Gizlilik getGizlilik() { return gizlilik; }
    public Aciliyet getAciliyet() { return aciliyet; }
    public EvrakDurumu getDurum() { return durum; }
    public Birim getBirim() { return birim; }
    public Kullanici getOlusturan() { return olusturan; }
    public Kullanici getImzalayan() { return imzalayan; }
    public LocalDateTime getImzaZamani() { return imzaZamani; }
    public String getRedGerekcesi() { return redGerekcesi; }
    public LocalDate getCevapSuresiSonu() { return cevapSuresiSonu; }
    public Long getIlgiliEvrakId() { return ilgiliEvrakId; }
    public Long getCevapEvrakId() { return cevapEvrakId; }
    public String getTamamlamaNotu() { return tamamlamaNotu; }
    public String getDosyaKodu() { return dosyaKodu; }
    public LocalDate getArsivTarihi() { return arsivTarihi; }
    public LocalDate getImhaTarihi() { return imhaTarihi; }
    public LocalDateTime getKayitZamani() { return kayitZamani; }

    public void kayitNoVer(String kayitNo) { this.kayitNo = kayitNo; }

    public void cevapSuresiBelirle(LocalDate sonTarih) { this.cevapSuresiSonu = sonTarih; }

    public void ilgiliEvrakBelirle(Long ilgiliEvrakId) { this.ilgiliEvrakId = ilgiliEvrakId; }

    public void havaleEdildi(Birim hedef) {
        this.birim = hedef;
        this.durum = EvrakDurumu.HAVALEDE;
    }

    public void imzayaGonderildi() {
        this.durum = EvrakDurumu.IMZA_BEKLIYOR;
        this.redGerekcesi = null;
    }

    public void imzalandi(Kullanici imzalayan, LocalDateTime zaman) {
        this.imzalayan = imzalayan;
        this.imzaZamani = zaman;
        this.durum = EvrakDurumu.TAMAMLANDI;
    }

    public void imzaReddedildi(String gerekce) {
        this.durum = EvrakDurumu.TASLAK;
        this.redGerekcesi = gerekce;
    }

    public void tamamlandi(Long cevapEvrakId, String not) {
        this.durum = EvrakDurumu.TAMAMLANDI;
        this.cevapEvrakId = cevapEvrakId;
        this.tamamlamaNotu = not;
    }

    public void arsivlendi(String dosyaKodu, LocalDate arsivTarihi, LocalDate imhaTarihi) {
        this.durum = EvrakDurumu.ARSIVLENDI;
        this.dosyaKodu = dosyaKodu;
        this.arsivTarihi = arsivTarihi;
        this.imhaTarihi = imhaTarihi;
    }
}
