package dev.evraktakip.servis;

import dev.evraktakip.config.EvrakProperties;
import dev.evraktakip.domain.*;
import dev.evraktakip.kural.BilgiEdinmeSureleri;
import dev.evraktakip.kural.IsGunuTakvimi;
import dev.evraktakip.kural.TcKimlik;
import dev.evraktakip.repo.BilgiEdinmeDeposu;
import dev.evraktakip.repo.BirimDeposu;
import dev.evraktakip.servis.Hatalar.IsKuraliIhlali;
import dev.evraktakip.servis.Hatalar.KayitBulunamadi;
import dev.evraktakip.web.Dto.BasvuruIstegi;
import dev.evraktakip.web.Dto.BasvuruYaniti;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4982 sayılı Bilgi Edinme Hakkı Kanunu başvuruları: kayıt, birime havale, süre takibi,
 * gerekçeli tek seferlik uzatma, cevap/ret ve yönlendirme. Süreler iş günü takvimine göre hesaplanır;
 * değerler yapılandırmadan gelir (örnektir).
 *
 * <p>KVKK: kimlik numarasının yalnızca maskeli hali saklanır, listelerde başvuran adı maskelenir,
 * ayrıntı ekranının her açılışı denetim kaydına yazılır.
 */
@Service
@Transactional
public class BilgiEdinmeServisi {
    private static final String VARLIK = "BILGI_EDINME";
    /** "Yaklaşan" sayılan kalan iş günü eşiği. */
    private static final int YAKLASAN_ISGUNU = 3;

    public record AcikOzet(long acik, long geciken, long yaklasan) {}

    private final BilgiEdinmeDeposu basvurular;
    private final BirimDeposu birimler;
    private final TakvimServisi takvimServisi;
    private final SayacServisi sayaclar;
    private final DenetimServisi denetim;
    private final EvrakProperties.Kurallar kurallar;
    private final Clock clock;

    public BilgiEdinmeServisi(BilgiEdinmeDeposu basvurular, BirimDeposu birimler, TakvimServisi takvimServisi,
                              SayacServisi sayaclar, DenetimServisi denetim, EvrakProperties props, Clock clock) {
        this.basvurular = basvurular;
        this.birimler = birimler;
        this.takvimServisi = takvimServisi;
        this.sayaclar = sayaclar;
        this.denetim = denetim;
        this.kurallar = props.kurallar();
        this.clock = clock;
    }

    // ---------------------------------------------------------------- okuma

    @Transactional(readOnly = true)
    public Page<BasvuruYaniti> listele(Kullanici k, BilgiEdinmeFiltre filtre, Pageable sayfa) {
        okumaYetkisi(k);
        IsGunuTakvimi takvim = takvimServisi.takvim();
        LocalDate bugun = bugun();
        return basvurular.findAll(gorunur(k).and(suzgec(filtre, bugun)), sayfa)
                .map(b -> yanit(b, takvim, bugun, false));
    }

    /** KVKK: kişisel veri içeren ayrıntının her görüntülenmesi denetim kaydına işlenir. */
    public BasvuruYaniti detay(Kullanici k, Long id) {
        okumaYetkisi(k);
        BilgiEdinme b = bul(k, id);
        denetim.kaydet(k.getKullaniciAdi(), "GORUNTULE", VARLIK, b.getId(), b.getBasvuruNo());
        return yanit(b, takvimServisi.takvim(), bugun(), true);
    }

    @Transactional(readOnly = true)
    public AcikOzet ozet(Kullanici k) {
        LocalDate bugun = bugun();
        LocalDate yaklasanSon = takvimServisi.takvim().isGunuEkle(bugun, YAKLASAN_ISGUNU);
        Specification<BilgiEdinme> acik = acikOlanlar();
        long acikSayi = basvurular.count(gorunur(k).and(acik));
        long geciken = basvurular.count(gorunur(k).and(acik).and(
                (kok, sorgu, cb) -> cb.lessThan(kok.<LocalDate>get("sureSonu"), bugun)));
        long yaklasan = basvurular.count(gorunur(k).and(acik).and(
                (kok, sorgu, cb) -> cb.between(kok.<LocalDate>get("sureSonu"), bugun, yaklasanSon)));
        return new AcikOzet(acikSayi, geciken, yaklasan);
    }

    // ------------------------------------------------------------ yazma

    public BasvuruYaniti olustur(Kullanici k, BasvuruIstegi i) {
        if (k.getRol() != Rol.BILGI_EDINME_SORUMLUSU && k.getRol() != Rol.KAYIT_MEMURU) {
            throw new AccessDeniedException("Başvuru kaydetme yetkisi yok");
        }
        LocalDate bugun = bugun();
        LocalDate basvuruTarihi = i.basvuruTarihi() == null ? bugun : i.basvuruTarihi();
        if (basvuruTarihi.isAfter(bugun)) {
            throw new IsKuraliIhlali("Başvuru tarihi bugünden sonra olamaz");
        }
        String tcMaskeli = null;
        if (i.tcKimlikNo() != null && !i.tcKimlikNo().isBlank()) {
            if (!TcKimlik.gecerliMi(i.tcKimlikNo())) {
                throw new IsKuraliIhlali("T.C. kimlik numarası geçersiz");
            }
            tcMaskeli = TcKimlik.maskele(i.tcKimlikNo());
        }
        IsGunuTakvimi takvim = takvimServisi.takvim();
        LocalDate sureSonu = sureler(takvim).cevapSureSonu(basvuruTarihi);
        String no = SayacServisi.bicim("BE", bugun.getYear(), sayaclar.sonraki(bugun.getYear() + "-BE"));
        BilgiEdinme b = basvurular.save(new BilgiEdinme(no, i.basvuranAd().strip(), tcMaskeli,
                bosIse(i.iletisim()), i.kanal(), i.konu().strip(), basvuruTarihi, sureSonu, k,
                LocalDateTime.now(clock)));
        denetim.kaydet(k.getKullaniciAdi(), "BASVURU_OLUSTUR", VARLIK, b.getId(),
                "%s, süre sonu %s".formatted(b.getBasvuruNo(), b.getSureSonu()));
        return yanit(b, takvim, bugun, false);
    }

    public BasvuruYaniti havale(Kullanici k, Long id, Long birimId) {
        BilgiEdinme b = sorumluIcinBul(k, id);
        acikMi(b);
        Birim birim = birimler.findById(birimId).orElseThrow(() -> new KayitBulunamadi("Birim bulunamadı"));
        b.havaleEdildi(birim);
        denetim.kaydet(k.getKullaniciAdi(), "HAVALE", VARLIK, b.getId(), "%s → %s".formatted(b.getBasvuruNo(), birim.getKod()));
        return yanit(b, takvimServisi.takvim(), bugun(), true);
    }

    /** İlgili birim, başvuruya ilişkin görüşünü yazar; cevabı bilgi edinme birimi verir. */
    public BasvuruYaniti birimGorusu(Kullanici k, Long id, String metin) {
        if (!k.getRol().birimleSinirli()) {
            throw new AccessDeniedException("Birim görüşü yalnızca birim personeli veya amiri tarafından yazılır");
        }
        BilgiEdinme b = bul(k, id);
        if (b.getDurum() != BasvuruDurumu.INCELEMEDE) {
            throw new IsKuraliIhlali("Görüş yalnızca incelemedeki başvuru için yazılır");
        }
        b.birimGorusuYaz(metin.strip());
        denetim.kaydet(k.getKullaniciAdi(), "BIRIM_GORUSU", VARLIK, b.getId(), b.getBasvuruNo());
        return yanit(b, takvimServisi.takvim(), bugun(), true);
    }

    /**
     * Süre, gerekçeyle yalnızca bir kez ve süre dolmadan uzatılır; ek süre mevcut süre sonuna eklenir.
     */
    public BasvuruYaniti uzat(Kullanici k, Long id, String gerekce) {
        BilgiEdinme b = sorumluIcinBul(k, id);
        acikMi(b);
        if (b.getUzatmaSayisi() >= 1) {
            throw new IsKuraliIhlali("Süre yalnızca bir kez uzatılabilir");
        }
        if (bugun().isAfter(b.getSureSonu())) {
            throw new IsKuraliIhlali("Süresi dolmuş başvurunun süresi uzatılamaz");
        }
        IsGunuTakvimi takvim = takvimServisi.takvim();
        b.uzatildi(sureler(takvim).uzatilmisSureSonu(b.getSureSonu()), gerekce.strip());
        denetim.kaydet(k.getKullaniciAdi(), "SURE_UZAT", VARLIK, b.getId(),
                "%s, yeni süre sonu %s".formatted(b.getBasvuruNo(), b.getSureSonu()));
        return yanit(b, takvim, bugun(), true);
    }

    public BasvuruYaniti cevapla(Kullanici k, Long id, String metin) {
        BilgiEdinme b = sorumluIcinBul(k, id);
        acikMi(b);
        LocalDate bugun = bugun();
        IsGunuTakvimi takvim = takvimServisi.takvim();
        b.sonuclandi(BasvuruDurumu.CEVAPLANDI, metin.strip(), null, bugun, sureler(takvim).itirazSonGunu(bugun));
        denetim.kaydet(k.getKullaniciAdi(), "CEVAPLA", VARLIK, b.getId(), sonucNotu(b));
        return yanit(b, takvim, bugun, true);
    }

    public BasvuruYaniti reddet(Kullanici k, Long id, RetNedeni nedeni, String gerekce) {
        BilgiEdinme b = sorumluIcinBul(k, id);
        acikMi(b);
        LocalDate bugun = bugun();
        IsGunuTakvimi takvim = takvimServisi.takvim();
        b.sonuclandi(BasvuruDurumu.REDDEDILDI, gerekce.strip(), nedeni, bugun, sureler(takvim).itirazSonGunu(bugun));
        denetim.kaydet(k.getKullaniciAdi(), "REDDET", VARLIK, b.getId(), nedeni + ", " + sonucNotu(b));
        return yanit(b, takvim, bugun, true);
    }

    /** Yetkisiz kuruma yapılan başvuru, süresi içinde yetkili kuruma yönlendirilir. */
    public BasvuruYaniti yonlendir(Kullanici k, Long id, String kurum) {
        BilgiEdinme b = sorumluIcinBul(k, id);
        if (b.getDurum() != BasvuruDurumu.ALINDI) {
            throw new IsKuraliIhlali("Yalnızca henüz birime havale edilmemiş başvuru başka kuruma yönlendirilir");
        }
        LocalDate bugun = bugun();
        IsGunuTakvimi takvim = takvimServisi.takvim();
        LocalDate sonGun = sureler(takvim).yonlendirmeSonGunu(b.getBasvuruTarihi());
        if (bugun.isAfter(sonGun)) {
            throw new IsKuraliIhlali("Yönlendirme süresi geçti (son gün %s)".formatted(sonGun));
        }
        b.yonlendirildi(kurum.strip(), bugun);
        denetim.kaydet(k.getKullaniciAdi(), "YONLENDIR", VARLIK, b.getId(), b.getBasvuruNo());
        return yanit(b, takvim, bugun, true);
    }

    // ------------------------------------------------------------ yardımcılar

    private LocalDate bugun() {
        return LocalDate.now(clock);
    }

    private static String bosIse(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private BilgiEdinmeSureleri sureler(IsGunuTakvimi takvim) {
        return new BilgiEdinmeSureleri(takvim, kurallar);
    }

    private static String sonucNotu(BilgiEdinme b) {
        return "%s, itiraz son tarihi %s".formatted(b.getBasvuruNo(), b.getItirazSonTarihi());
    }

    private static void acikMi(BilgiEdinme b) {
        if (!b.getDurum().acik()) {
            throw new IsKuraliIhlali("Başvuru sonuçlanmış; işlem yapılamaz");
        }
    }

    private static void okumaYetkisi(Kullanici k) {
        Rol r = k.getRol();
        boolean izinli = r == Rol.BILGI_EDINME_SORUMLUSU || r == Rol.UST_YONETICI || r == Rol.DENETCI
                || r.birimleSinirli();
        if (!izinli) {
            throw new AccessDeniedException("Bilgi edinme başvurularını görme yetkisi yok");
        }
    }

    private BilgiEdinme sorumluIcinBul(Kullanici k, Long id) {
        if (k.getRol() != Rol.BILGI_EDINME_SORUMLUSU) {
            throw new AccessDeniedException("Bu işlem yalnızca bilgi edinme sorumlusuna açıktır");
        }
        return bul(k, id);
    }

    private BilgiEdinme bul(Kullanici k, Long id) {
        Specification<BilgiEdinme> kimlikle = (kok, sorgu, cb) -> cb.equal(kok.get("id"), id);
        return basvurular.findOne(gorunur(k).and(kimlikle))
                .orElseThrow(() -> new KayitBulunamadi("Başvuru bulunamadı: " + id));
    }

    /** Birim ile sınırlı roller yalnızca kendi birimine havale edilen başvuruları görür. */
    private Specification<BilgiEdinme> gorunur(Kullanici k) {
        return (kok, sorgu, cb) -> {
            Rol rol = k.getRol();
            if (rol == Rol.BILGI_EDINME_SORUMLUSU || rol == Rol.UST_YONETICI || rol == Rol.DENETCI) {
                return cb.conjunction();
            }
            if (rol.birimleSinirli() && k.getBirim() != null) {
                Join<BilgiEdinme, Birim> birim = kok.join("birim", JoinType.LEFT);
                return cb.equal(birim.get("id"), k.getBirim().getId());
            }
            return cb.disjunction();
        };
    }

    private static Specification<BilgiEdinme> acikOlanlar() {
        return (kok, sorgu, cb) -> kok.get("durum").in(BasvuruDurumu.ALINDI, BasvuruDurumu.INCELEMEDE);
    }

    private static Specification<BilgiEdinme> suzgec(BilgiEdinmeFiltre f, LocalDate bugun) {
        return (kok, sorgu, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.durum() != null) {
                p.add(cb.equal(kok.get("durum"), f.durum()));
            }
            if (f.birimId() != null) {
                Join<BilgiEdinme, Birim> birim = kok.join("birim", JoinType.LEFT);
                p.add(cb.equal(birim.get("id"), f.birimId()));
            }
            if (Boolean.TRUE.equals(f.geciken())) {
                p.add(acikOlanlar().toPredicate(kok, sorgu, cb));
                p.add(cb.lessThan(kok.<LocalDate>get("sureSonu"), bugun));
            }
            String kalip = AramaYardimcisi.kalip(f.ara(), 100);
            if (kalip != null) {
                p.add(cb.or(
                        cb.like(cb.lower(kok.<String>get("basvuruNo")), kalip, AramaYardimcisi.KACIS),
                        cb.like(cb.lower(kok.<String>get("basvuranAd")), kalip, AramaYardimcisi.KACIS),
                        cb.like(cb.lower(kok.<String>get("konu")), kalip, AramaYardimcisi.KACIS)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /** "Ayşe Yılmaz" → "A*** Y***". */
    static String adMaskele(String ad) {
        StringBuilder sb = new StringBuilder();
        for (String parca : ad.strip().split("\\s+")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(parca.charAt(0)).append("***");
        }
        return sb.toString();
    }

    private static BasvuruYaniti yanit(BilgiEdinme b, IsGunuTakvimi takvim, LocalDate bugun, boolean ayrintili) {
        Integer kalan = null;
        boolean gecikmis = false;
        if (b.getDurum().acik()) {
            kalan = takvim.isGunuFarki(bugun, b.getSureSonu());
            gecikmis = b.getSureSonu().isBefore(bugun);
        }
        boolean gecikmeliSonuc = b.getSonucTarihi() != null && b.getDurum() != BasvuruDurumu.YONLENDIRILDI
                && b.getSonucTarihi().isAfter(b.getSureSonu());
        Birim birim = b.getBirim();
        return new BasvuruYaniti(b.getId(), b.getBasvuruNo(),
                ayrintili ? b.getBasvuranAd() : adMaskele(b.getBasvuranAd()),
                b.getTcMaskeli(), ayrintili ? b.getIletisim() : null, b.getKanal(), b.getKonu(),
                b.getBasvuruTarihi(), b.getDurum(), birim == null ? null : birim.getId(),
                birim == null ? null : birim.getAd(), b.getBirimGorusu(), b.getSureSonu(), kalan, gecikmis,
                b.getUzatmaSayisi(), b.getUzatmaGerekcesi(), b.getSonucMetni(), b.getRetNedeni(),
                b.getSonucTarihi(), gecikmeliSonuc, b.getItirazSonTarihi(), b.getYonlendirilenKurum(),
                b.getKayitZamani());
    }
}
