package dev.evraktakip.servis;

import static dev.evraktakip.domain.Rol.BIRIM_AMIRI;
import static dev.evraktakip.domain.Rol.KAYIT_MEMURU;
import static dev.evraktakip.domain.Rol.UST_YONETICI;

import dev.evraktakip.config.EvrakProperties;
import dev.evraktakip.domain.*;
import dev.evraktakip.kural.IsGunuTakvimi;
import dev.evraktakip.repo.*;
import dev.evraktakip.servis.Hatalar.IsKuraliIhlali;
import dev.evraktakip.servis.Hatalar.KayitBulunamadi;
import dev.evraktakip.web.Dto.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evrak yaşam döngüsü.
 * <ul>
 *   <li>Gelen: KAYITLI → (havale) HAVALEDE → (tamamla) TAMAMLANDI → (arşivle) ARSIVLENDI</li>
 *   <li>Giden: TASLAK → IMZA_BEKLIYOR → (imza) TAMAMLANDI → ARSIVLENDI; imza reddedilirse TASLAK</li>
 * </ul>
 * Görünürlük kuralları {@link #gorunur(Kullanici)} içinde toplanmıştır; görünmeyen kayıt 404 döner,
 * böylece GİZLİ bir evrakın varlığı bile sızmaz. Denetim kaydına konu metni yazılmaz, yalnızca kayıt numarası.
 */
@Service
@Transactional
public class EvrakServisi {
    private static final String VARLIK = "EVRAK";

    private final EvrakDeposu evraklar;
    private final SevkDeposu sevkler;
    private final BirimDeposu birimler;
    private final KullaniciDeposu kullanicilar;
    private final DosyaPlaniDeposu dosyaPlanlari;
    private final TakvimServisi takvimServisi;
    private final SayacServisi sayaclar;
    private final DenetimServisi denetim;
    private final EvrakProperties.Kurallar kurallar;
    private final Clock clock;

    public EvrakServisi(EvrakDeposu evraklar, SevkDeposu sevkler, BirimDeposu birimler,
                        KullaniciDeposu kullanicilar, DosyaPlaniDeposu dosyaPlanlari, TakvimServisi takvimServisi,
                        SayacServisi sayaclar, DenetimServisi denetim, EvrakProperties props, Clock clock) {
        this.evraklar = evraklar;
        this.sevkler = sevkler;
        this.birimler = birimler;
        this.kullanicilar = kullanicilar;
        this.dosyaPlanlari = dosyaPlanlari;
        this.takvimServisi = takvimServisi;
        this.sayaclar = sayaclar;
        this.denetim = denetim;
        this.kurallar = props.kurallar();
        this.clock = clock;
    }

    // ---------------------------------------------------------------- okuma

    @Transactional(readOnly = true)
    public Page<EvrakYaniti> listele(Kullanici k, EvrakFiltre filtre, Pageable sayfa) {
        IsGunuTakvimi takvim = takvimServisi.takvim();
        LocalDate bugun = bugun();
        return evraklar.findAll(gorunur(k).and(suzgec(filtre, bugun)), sayfa).map(e -> yanit(e, takvim, bugun));
    }

    /** Görünür evraklar üzerinden sayılar; imha adayları ayrı uç noktadadır. */
    @Transactional(readOnly = true)
    public Map<EvrakDurumu, Long> durumSayilari(Kullanici k) {
        Map<EvrakDurumu, Long> sayilar = new EnumMap<>(EvrakDurumu.class);
        for (EvrakDurumu d : EvrakDurumu.values()) {
            sayilar.put(d, evraklar.count(gorunur(k).and(durumIle(d))));
        }
        return sayilar;
    }

    @Transactional(readOnly = true)
    public long gecikenSayisi(Kullanici k) {
        return evraklar.count(gorunur(k).and(geciken(bugun())));
    }

    /** GİZLİ ve HİZMETE ÖZEL evrakın görüntülenmesi denetim kaydına işlenir. */
    public EvrakDetayYaniti detay(Kullanici k, Long id) {
        Evrak e = bul(k, id);
        if (e.getGizlilik() != Gizlilik.NORMAL) {
            denetim.kaydet(k.getKullaniciAdi(), "GORUNTULE", VARLIK, e.getId(), e.getGizlilik().name());
        }
        List<SevkYaniti> zincir = sevkler.findByEvrakOrderByIdAsc(e).stream().map(EvrakServisi::sevkYaniti).toList();
        return new EvrakDetayYaniti(yanit(e, takvimServisi.takvim(), bugun()), zincir);
    }

    @Transactional(readOnly = true)
    public Page<EvrakYaniti> gelenKutusu(Kullanici k, Pageable sayfa) {
        IsGunuTakvimi takvim = takvimServisi.takvim();
        LocalDate bugun = bugun();
        return evraklar.findAll(gorunur(k).and(bendeBekleyen(k)), sayfa).map(e -> yanit(e, takvim, bugun));
    }

    @Transactional(readOnly = true)
    public long gelenKutusuSayisi(Kullanici k) {
        return k.getRol().birimleSinirli() ? evraklar.count(gorunur(k).and(bendeBekleyen(k))) : 0;
    }

    /** Son altı ay (kayıt zamanına göre) ve birim bazında açık evrak; yalnızca görünür kayıtlar sayılır. */
    @Transactional(readOnly = true)
    public GrafikYaniti grafik(Kullanici k) {
        java.time.YearMonth bu = java.time.YearMonth.from(bugun());
        List<AylikSayi> aylik = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            java.time.YearMonth ay = bu.minusMonths(i);
            LocalDateTime bas = ay.atDay(1).atStartOfDay();
            LocalDateTime son = ay.plusMonths(1).atDay(1).atStartOfDay();
            Specification<Evrak> aralik = (kok, sorgu, cb) -> cb.and(
                    cb.greaterThanOrEqualTo(kok.<LocalDateTime>get("kayitZamani"), bas),
                    cb.lessThan(kok.<LocalDateTime>get("kayitZamani"), son));
            long gelen = evraklar.count(gorunur(k).and(aralik).and(yonIle(EvrakYonu.GELEN)));
            long giden = evraklar.count(gorunur(k).and(aralik).and(yonIle(EvrakYonu.GIDEN)));
            aylik.add(new AylikSayi(ay.toString(), gelen, giden));
        }
        List<BirimYuku> yuk = new ArrayList<>();
        for (Birim b : birimler.findAll(Sort.by("ad"))) {
            Specification<Evrak> birimde = (kok, sorgu, cb) -> cb.and(
                    cb.equal(kok.get("durum"), EvrakDurumu.HAVALEDE),
                    cb.equal(kok.join("birim").get("id"), b.getId()));
            yuk.add(new BirimYuku(b.getAd(), evraklar.count(gorunur(k).and(birimde))));
        }
        return new GrafikYaniti(aylik, yuk);
    }

    @Transactional(readOnly = true)
    public Page<EvrakYaniti> imhaAdaylari(Kullanici k, Pageable sayfa) {
        LocalDate bugun = bugun();
        IsGunuTakvimi takvim = takvimServisi.takvim();
        Specification<Evrak> imha = (kok, sorgu, cb) -> cb.and(
                cb.equal(kok.get("durum"), EvrakDurumu.ARSIVLENDI),
                cb.isNotNull(kok.get("imhaTarihi")),
                cb.lessThanOrEqualTo(kok.<LocalDate>get("imhaTarihi"), bugun));
        return evraklar.findAll(gorunur(k).and(imha), sayfa).map(e -> yanit(e, takvim, bugun));
    }

    @Transactional(readOnly = true)
    public List<DosyaPlaniYaniti> dosyaPlani() {
        return dosyaPlanlari.findAll(Sort.by("kod")).stream()
                .map(p -> new DosyaPlaniYaniti(p.getKod(), p.getAd(), p.getSaklamaYili()))
                .toList();
    }

    // ------------------------------------------------------------ gelen evrak

    public EvrakYaniti gelenKaydet(Kullanici k, GelenEvrakIstegi i) {
        LocalDate bugun = bugun();
        if (i.evrakTarihi().isAfter(bugun)) {
            throw new IsKuraliIhlali("Evrak tarihi bugünden sonra olamaz");
        }
        IsGunuTakvimi takvim = takvimServisi.takvim();
        Evrak e = new Evrak(EvrakYonu.GELEN, i.konu().strip(), i.ilgiliKurum().strip(), i.evrakTarihi(),
                bosIse(i.disSayi()), i.gizlilik(), i.aciliyet(), EvrakDurumu.KAYITLI, null, k,
                LocalDateTime.now(clock));
        e.cevapSuresiBelirle(cevapSuresi(i.aciliyet(), i.cevapGunu(), bugun, takvim));
        e.kayitNoVer(SayacServisi.bicim("GEL", bugun.getYear(), sayaclar.sonraki(bugun.getYear() + "-GEL")));
        Evrak kayit = evraklar.save(e);
        denetim.kaydet(k.getKullaniciAdi(), "GELEN_KAYIT", VARLIK, kayit.getId(),
                "%s, %s".formatted(kayit.getKayitNo(), kayit.getGizlilik()));
        return yanit(kayit, takvim, bugun);
    }

    public SevkYaniti sevkEt(Kullanici k, Long id, SevkIstegi i) {
        Evrak e = bul(k, id);
        Rol rol = k.getRol();
        if (rol != KAYIT_MEMURU && rol != BIRIM_AMIRI && rol != UST_YONETICI) {
            throw new AccessDeniedException("Havale yetkisi yok");
        }
        if (e.getYon() != EvrakYonu.GELEN || !e.getDurum().islemde()) {
            throw new IsKuraliIhlali("Yalnızca işlemdeki gelen evrak havale edilebilir");
        }
        if (rol == KAYIT_MEMURU && e.getDurum() != EvrakDurumu.KAYITLI) {
            throw new IsKuraliIhlali("Kayıt memuru yalnızca ilk havaleyi yapar; sonrası birim amirinindir");
        }
        if (rol == BIRIM_AMIRI && !birimde(k, e)) {
            throw new IsKuraliIhlali("Evrak artık biriminizde değil");
        }
        if ((i.hedefBirimId() == null) == (i.hedefKullaniciId() == null)) {
            throw new IsKuraliIhlali("Hedef birim veya hedef kullanıcıdan yalnızca biri verilmelidir");
        }

        Birim hedefBirim;
        Kullanici hedefKullanici = null;
        if (i.hedefKullaniciId() != null) {
            hedefKullanici = kullanicilar.findById(i.hedefKullaniciId())
                    .orElseThrow(() -> new KayitBulunamadi("Hedef kullanıcı bulunamadı"));
            if (hedefKullanici.getDurum() != KullaniciDurumu.AKTIF || !hedefKullanici.getRol().birimleSinirli()
                    || hedefKullanici.getBirim() == null) {
                throw new IsKuraliIhlali("Hedef kullanıcı, birimde görevli aktif bir personel veya amir olmalıdır");
            }
            if (hedefKullanici.getId().equals(k.getId())) {
                throw new IsKuraliIhlali("Evrak kendinize havale edilemez");
            }
            hedefBirim = hedefKullanici.getBirim();
        } else {
            if (e.getGizlilik() == Gizlilik.GIZLI) {
                throw new IsKuraliIhlali("GİZLİ evrak yalnızca bir kullanıcıya havale edilebilir");
            }
            hedefBirim = birimler.findById(i.hedefBirimId())
                    .orElseThrow(() -> new KayitBulunamadi("Birim bulunamadı"));
        }

        for (Sevk eski : sevkler.findByEvrakAndDurumIn(e, List.of(SevkDurumu.BEKLIYOR, SevkDurumu.ALINDI))) {
            eski.durumBelirle(SevkDurumu.DEVREDILDI);
        }
        Sevk sevk = sevkler.save(new Sevk(e, k, hedefBirim, hedefKullanici, bosIse(i.talimat()),
                LocalDateTime.now(clock)));
        e.havaleEdildi(hedefBirim);
        denetim.kaydet(k.getKullaniciAdi(), "SEVK", VARLIK, e.getId(), "%s → %s".formatted(e.getKayitNo(),
                hedefKullanici != null ? hedefKullanici.getKullaniciAdi() : hedefBirim.getKod()));
        return sevkYaniti(sevk);
    }

    public SevkYaniti teslimAl(Kullanici k, Long sevkId) {
        Sevk s = sevkler.findById(sevkId).orElseThrow(() -> new KayitBulunamadi("Sevk bulunamadı: " + sevkId));
        Evrak e = bul(k, s.getEvrak().getId());
        if (s.getDurum() != SevkDurumu.BEKLIYOR) {
            throw new IsKuraliIhlali("Sevk zaten teslim alınmış veya başka bir kişiye devredilmiş");
        }
        boolean yetkili = s.getHedefKullanici() != null
                ? s.getHedefKullanici().getId().equals(k.getId())
                : k.getRol() == BIRIM_AMIRI && k.getBirim() != null
                        && k.getBirim().getId().equals(s.getHedefBirim().getId());
        if (!yetkili) {
            throw new AccessDeniedException("Bu sevki teslim alma yetkiniz yok");
        }
        s.teslimAlindi(LocalDateTime.now(clock));
        denetim.kaydet(k.getKullaniciAdi(), "TESLIM_AL", VARLIK, e.getId(), e.getKayitNo());
        return sevkYaniti(s);
    }

    /**
     * Gelen evrakı tamamlar. ACELE ve GÜNLÜDÜR evrakta cevap evrakı bağlanmalı ya da tamamlama notu yazılmalıdır;
     * cevap evrakı, bu evraka cevap veren ve imzalanmış bir giden evrak olmalıdır.
     */
    public EvrakYaniti tamamla(Kullanici k, Long id, TamamlaIstegi i) {
        Evrak e = bul(k, id);
        if (!k.getRol().birimleSinirli()) {
            throw new AccessDeniedException("Evrakı yalnızca birim personeli veya amiri tamamlayabilir");
        }
        if (e.getYon() != EvrakYonu.GELEN || e.getDurum() != EvrakDurumu.HAVALEDE) {
            throw new IsKuraliIhlali("Yalnızca havale edilmiş gelen evrak tamamlanabilir");
        }
        if (!birimde(k, e)) {
            throw new IsKuraliIhlali("Evrak biriminizde değil");
        }
        Sevk son = sevkler.findFirstByEvrakOrderByIdDesc(e)
                .orElseThrow(() -> new IsKuraliIhlali("Evrakın havale kaydı yok"));
        if (son.getDurum() != SevkDurumu.ALINDI) {
            throw new IsKuraliIhlali("Evrak teslim alınmadan tamamlanamaz");
        }
        boolean yetkili = k.getRol() == BIRIM_AMIRI
                || (son.getHedefKullanici() != null && son.getHedefKullanici().getId().equals(k.getId()));
        if (!yetkili) {
            throw new AccessDeniedException("Evrakı yalnızca havale edilen kişi veya birim amiri tamamlayabilir");
        }

        Long cevapId = i.cevapEvrakId();
        String notu = bosIse(i.notu());
        if (cevapId != null) {
            Evrak cevap = bul(k, cevapId);
            boolean imzali = cevap.getDurum() == EvrakDurumu.TAMAMLANDI || cevap.getDurum() == EvrakDurumu.ARSIVLENDI;
            if (cevap.getYon() != EvrakYonu.GIDEN || !imzali || !Objects.equals(cevap.getIlgiliEvrakId(), e.getId())) {
                throw new IsKuraliIhlali("Cevap evrakı, bu evraka cevap veren imzalanmış bir giden evrak olmalıdır");
            }
        } else if (notu == null && e.getAciliyet() != Aciliyet.NORMAL) {
            throw new IsKuraliIhlali("ACELE veya GÜNLÜDÜR evrakı tamamlamak için cevap evrakı bağlayın "
                    + "ya da tamamlama notu yazın");
        }
        e.tamamlandi(cevapId, notu);
        son.durumBelirle(SevkDurumu.TAMAMLANDI);
        denetim.kaydet(k.getKullaniciAdi(), "TAMAMLA", VARLIK, e.getId(),
                cevapId != null ? "%s, cevap evrak %d".formatted(e.getKayitNo(), cevapId) : e.getKayitNo());
        return yanit(e, takvimServisi.takvim(), bugun());
    }

    // ------------------------------------------------------------ giden evrak

    public EvrakYaniti gidenOlustur(Kullanici k, GidenEvrakIstegi i) {
        if (k.getBirim() == null) {
            throw new IsKuraliIhlali("Giden evrak hazırlamak için bir birime bağlı olmalısınız");
        }
        if (i.ilgiliEvrakId() != null) {
            Evrak ilgili = bul(k, i.ilgiliEvrakId());
            if (ilgili.getYon() != EvrakYonu.GELEN || ilgili.getDurum() != EvrakDurumu.HAVALEDE
                    || !birimde(k, ilgili)) {
                throw new IsKuraliIhlali("Cevap yazılacak evrak, biriminizdeki havale edilmiş bir gelen evrak olmalıdır");
            }
        }
        Evrak e = new Evrak(EvrakYonu.GIDEN, i.konu().strip(), i.ilgiliKurum().strip(), bugun(), null,
                i.gizlilik(), Aciliyet.NORMAL, EvrakDurumu.TASLAK, k.getBirim(), k, LocalDateTime.now(clock));
        e.ilgiliEvrakBelirle(i.ilgiliEvrakId());
        Evrak kayit = evraklar.save(e);
        denetim.kaydet(k.getKullaniciAdi(), "GIDEN_TASLAK", VARLIK, kayit.getId(),
                i.ilgiliEvrakId() != null ? "cevap, ilgili evrak " + i.ilgiliEvrakId() : null);
        return yanit(kayit, takvimServisi.takvim(), bugun());
    }

    public EvrakYaniti imzayaGonder(Kullanici k, Long id) {
        Evrak e = bul(k, id);
        if (e.getYon() != EvrakYonu.GIDEN || e.getDurum() != EvrakDurumu.TASLAK) {
            throw new IsKuraliIhlali("Yalnızca taslak durumundaki giden evrak imzaya gönderilebilir");
        }
        if (!e.getOlusturan().getId().equals(k.getId())) {
            throw new AccessDeniedException("Taslağı yalnızca hazırlayan kişi imzaya gönderebilir");
        }
        e.imzayaGonderildi();
        denetim.kaydet(k.getKullaniciAdi(), "IMZAYA_GONDER", VARLIK, e.getId(), null);
        return yanit(e, takvimServisi.takvim(), bugun());
    }

    /** Görevler ayrılığı: evrakı hazırlayan kişi imzalayamaz. İmzada resmi sayı (kayıt numarası) verilir. */
    public EvrakYaniti imzala(Kullanici k, Long id) {
        Evrak e = imzaIcinBul(k, id);
        if (e.getOlusturan().getId().equals(k.getId())) {
            throw new IsKuraliIhlali("Evrakı hazırlayan kişi imzalayamaz (görevler ayrılığı)");
        }
        int yil = bugun().getYear();
        e.kayitNoVer(SayacServisi.bicim("GID", yil, sayaclar.sonraki(yil + "-GID")));
        e.imzalandi(k, LocalDateTime.now(clock));
        denetim.kaydet(k.getKullaniciAdi(), "IMZALA", VARLIK, e.getId(), e.getKayitNo());
        return yanit(e, takvimServisi.takvim(), bugun());
    }

    public EvrakYaniti imzaReddet(Kullanici k, Long id, String gerekce) {
        Evrak e = imzaIcinBul(k, id);
        e.imzaReddedildi(gerekce.strip());
        denetim.kaydet(k.getKullaniciAdi(), "IMZA_REDDET", VARLIK, e.getId(), null);
        return yanit(e, takvimServisi.takvim(), bugun());
    }

    private Evrak imzaIcinBul(Kullanici k, Long id) {
        Evrak e = bul(k, id);
        if (k.getRol() != BIRIM_AMIRI) {
            throw new AccessDeniedException("İmza yetkisi yalnızca birim amirindedir");
        }
        if (e.getYon() != EvrakYonu.GIDEN || e.getDurum() != EvrakDurumu.IMZA_BEKLIYOR) {
            throw new IsKuraliIhlali("Evrak imza beklemiyor");
        }
        if (!birimde(k, e)) {
            throw new IsKuraliIhlali("Evrak biriminize ait değil");
        }
        return e;
    }

    // ----------------------------------------------------------------- arşiv

    public EvrakYaniti arsivle(Kullanici k, Long id, String dosyaKodu) {
        Evrak e = bul(k, id);
        if (k.getRol() != Rol.ARSIV_SORUMLUSU && k.getRol() != UST_YONETICI) {
            throw new AccessDeniedException("Arşivleme yetkisi yok");
        }
        if (e.getDurum() != EvrakDurumu.TAMAMLANDI) {
            throw new IsKuraliIhlali("Yalnızca tamamlanmış evrak arşivlenebilir");
        }
        DosyaPlani plan = dosyaPlanlari.findById(dosyaKodu.strip())
                .orElseThrow(() -> new IsKuraliIhlali("Dosya planı kodu bulunamadı: " + dosyaKodu));
        LocalDate bugun = bugun();
        LocalDate imha = plan.getSaklamaYili() == null ? null : bugun.plusYears(plan.getSaklamaYili());
        e.arsivlendi(plan.getKod(), bugun, imha);
        denetim.kaydet(k.getKullaniciAdi(), "ARSIVLE", VARLIK, e.getId(),
                "%s, dosya kodu %s".formatted(e.getKayitNo(), plan.getKod()));
        return yanit(e, takvimServisi.takvim(), bugun);
    }

    // ------------------------------------------------------------ yardımcılar

    private LocalDate bugun() {
        return LocalDate.now(clock);
    }

    private static String bosIse(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static boolean birimde(Kullanici k, Evrak e) {
        return k.getBirim() != null && e.getBirim() != null && k.getBirim().getId().equals(e.getBirim().getId());
    }

    private LocalDate cevapSuresi(Aciliyet aciliyet, Integer cevapGunu, LocalDate bugun, IsGunuTakvimi takvim) {
        return switch (aciliyet) {
            case NORMAL -> {
                if (cevapGunu != null) {
                    throw new IsKuraliIhlali("Cevap süresi yalnızca GÜNLÜDÜR evrak için girilir");
                }
                yield null;
            }
            case ACELE -> {
                if (cevapGunu != null) {
                    throw new IsKuraliIhlali("ACELE evrakta cevap süresi yapılandırmadan gelir");
                }
                yield takvim.isGunuEkle(bugun, kurallar.aceleCevapIsGunu());
            }
            case GUNLUDUR -> {
                if (cevapGunu == null) {
                    throw new IsKuraliIhlali("GÜNLÜDÜR evrakta cevap süresi (iş günü) girilmelidir");
                }
                yield takvim.isGunuEkle(bugun, cevapGunu);
            }
        };
    }

    /** Kullanıcının göremediği evrak "bulunamadı" sayılır. */
    private Evrak bul(Kullanici k, Long id) {
        Specification<Evrak> kimlikle = (kok, sorgu, cb) -> cb.equal(kok.get("id"), id);
        return evraklar.findOne(gorunur(k).and(kimlikle))
                .orElseThrow(() -> new KayitBulunamadi("Evrak bulunamadı: " + id));
    }

    /**
     * Görünürlük:
     * <ul>
     *   <li>Üst yönetici: tümü.</li>
     *   <li>Yönetici (ADMIN) ve bilgi edinme sorumlusu: evrak içeriğine erişemez.</li>
     *   <li>Birim personeli/amiri: kendi birimindeki GİZLİ olmayan evrak; GİZLİ evrak yalnızca işlem zincirindeyse.</li>
     *   <li>Diğer roller: GİZLİ olmayan evrak (GİZLİ ise işlem zincirindeyse); arşiv sorumlusu yalnızca
     *       tamamlanmış/arşivlenmiş evrakı görür.</li>
     *   <li>Taslağı yalnızca hazırlayan ve birim amiri görür.</li>
     * </ul>
     */
    private Specification<Evrak> gorunur(Kullanici k) {
        return (kok, sorgu, cb) -> {
            Rol rol = k.getRol();
            if (rol == UST_YONETICI) {
                return cb.conjunction();
            }
            if (rol == Rol.ADMIN || rol == Rol.BILGI_EDINME_SORUMLUSU) {
                return cb.disjunction();
            }
            Join<Evrak, Birim> birim = kok.join("birim", JoinType.LEFT);
            Join<Evrak, Kullanici> olusturan = kok.join("olusturan");
            Predicate gizliDegil = cb.notEqual(kok.get("gizlilik"), Gizlilik.GIZLI);
            Predicate katilimci = cb.or(cb.equal(olusturan.get("id"), k.getId()), sevkZincirinde(kok, sorgu, cb, k));

            Predicate gorunurluk;
            if (rol.birimleSinirli()) {
                Predicate kendiBirim = k.getBirim() == null
                        ? cb.disjunction()
                        : cb.equal(birim.get("id"), k.getBirim().getId());
                gorunurluk = cb.or(cb.and(kendiBirim, gizliDegil), katilimci);
            } else {
                gorunurluk = cb.or(gizliDegil, katilimci);
            }
            if (rol == Rol.ARSIV_SORUMLUSU) {
                gorunurluk = cb.and(gorunurluk,
                        kok.get("durum").in(EvrakDurumu.TAMAMLANDI, EvrakDurumu.ARSIVLENDI));
            }
            Predicate taslak = rol == BIRIM_AMIRI
                    ? cb.conjunction()
                    : cb.or(cb.notEqual(kok.get("durum"), EvrakDurumu.TASLAK), cb.equal(olusturan.get("id"), k.getId()));
            return cb.and(gorunurluk, taslak);
        };
    }

    private static Predicate sevkZincirinde(Root<Evrak> kok, CriteriaQuery<?> sorgu, CriteriaBuilder cb, Kullanici k) {
        Subquery<Long> alt = sorgu.subquery(Long.class);
        Root<Sevk> s = alt.from(Sevk.class);
        Join<Sevk, Kullanici> hedef = s.join("hedefKullanici", JoinType.LEFT);
        Join<Sevk, Kullanici> eden = s.join("sevkEden", JoinType.LEFT);
        alt.select(s.get("id")).where(
                cb.equal(s.get("evrak"), kok),
                cb.or(cb.equal(hedef.get("id"), k.getId()), cb.equal(eden.get("id"), k.getId())));
        return cb.exists(alt);
    }

    private static Specification<Evrak> yonIle(EvrakYonu yon) {
        return (kok, sorgu, cb) -> cb.equal(kok.get("yon"), yon);
    }

    /** HAVALEDE evrak; açık (bekliyor/alındı) sevk bana, amirsem birimime çevrilmiş. */
    private static Specification<Evrak> bendeBekleyen(Kullanici k) {
        return (kok, sorgu, cb) -> {
            Subquery<Long> alt = sorgu.subquery(Long.class);
            Root<Sevk> s = alt.from(Sevk.class);
            Join<Sevk, Kullanici> hedef = s.join("hedefKullanici", JoinType.LEFT);
            Join<Sevk, Birim> hedefBirim = s.join("hedefBirim");
            Predicate kisiye = cb.equal(hedef.get("id"), k.getId());
            Predicate birime = k.getRol() == BIRIM_AMIRI && k.getBirim() != null
                    ? cb.and(cb.isNull(s.get("hedefKullanici")), cb.equal(hedefBirim.get("id"), k.getBirim().getId()))
                    : cb.disjunction();
            alt.select(s.get("id")).where(
                    cb.equal(s.get("evrak"), kok),
                    s.get("durum").in(SevkDurumu.BEKLIYOR, SevkDurumu.ALINDI),
                    cb.or(kisiye, birime));
            return cb.and(cb.equal(kok.get("durum"), EvrakDurumu.HAVALEDE), cb.exists(alt));
        };
    }

    private static Specification<Evrak> durumIle(EvrakDurumu durum) {
        return (kok, sorgu, cb) -> cb.equal(kok.get("durum"), durum);
    }

    private static Specification<Evrak> geciken(LocalDate bugun) {
        return (kok, sorgu, cb) -> cb.and(
                cb.isNotNull(kok.get("cevapSuresiSonu")),
                cb.lessThan(kok.<LocalDate>get("cevapSuresiSonu"), bugun),
                kok.get("durum").in(EvrakDurumu.KAYITLI, EvrakDurumu.HAVALEDE));
    }

    private static Specification<Evrak> suzgec(EvrakFiltre f, LocalDate bugun) {
        return (kok, sorgu, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.yon() != null) {
                p.add(cb.equal(kok.get("yon"), f.yon()));
            }
            if (f.durum() != null) {
                p.add(cb.equal(kok.get("durum"), f.durum()));
            }
            if (f.gizlilik() != null) {
                p.add(cb.equal(kok.get("gizlilik"), f.gizlilik()));
            }
            if (f.aciliyet() != null) {
                p.add(cb.equal(kok.get("aciliyet"), f.aciliyet()));
            }
            if (f.birimId() != null) {
                Join<Evrak, Birim> birim = kok.join("birim", JoinType.LEFT);
                p.add(cb.equal(birim.get("id"), f.birimId()));
            }
            if (Boolean.TRUE.equals(f.geciken())) {
                p.add(geciken(bugun).toPredicate(kok, sorgu, cb));
            }
            String kalip = AramaYardimcisi.kalip(f.ara(), EvrakFiltre.EN_COK_ARAMA_UZUNLUGU);
            if (kalip != null) {
                p.add(cb.or(
                        cb.like(cb.lower(kok.<String>get("konu")), kalip, AramaYardimcisi.KACIS),
                        cb.like(cb.lower(kok.<String>get("ilgiliKurum")), kalip, AramaYardimcisi.KACIS),
                        cb.like(cb.lower(kok.<String>get("kayitNo")), kalip, AramaYardimcisi.KACIS),
                        cb.like(cb.lower(kok.<String>get("disSayi")), kalip, AramaYardimcisi.KACIS)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static EvrakYaniti yanit(Evrak e, IsGunuTakvimi takvim, LocalDate bugun) {
        Integer kalan = null;
        boolean gecikmis = false;
        if (e.getCevapSuresiSonu() != null && e.getDurum().islemde()) {
            kalan = takvim.isGunuFarki(bugun, e.getCevapSuresiSonu());
            gecikmis = e.getCevapSuresiSonu().isBefore(bugun);
        }
        Birim b = e.getBirim();
        return new EvrakYaniti(e.getId(), e.getKayitNo(), e.getYon(), e.getKonu(), e.getIlgiliKurum(),
                e.getEvrakTarihi(), e.getDisSayi(), e.getGizlilik(), e.getAciliyet(), e.getDurum(),
                b == null ? null : b.getId(), b == null ? null : b.getAd(),
                e.getOlusturan().getKullaniciAdi(),
                e.getImzalayan() == null ? null : e.getImzalayan().getKullaniciAdi(),
                e.getImzaZamani(), e.getRedGerekcesi(), e.getCevapSuresiSonu(), kalan, gecikmis,
                e.getIlgiliEvrakId(), e.getCevapEvrakId(), e.getTamamlamaNotu(), e.getDosyaKodu(),
                e.getArsivTarihi(), e.getImhaTarihi(), e.getKayitZamani());
    }

    private static SevkYaniti sevkYaniti(Sevk s) {
        return new SevkYaniti(s.getId(), s.getSevkEden().getKullaniciAdi(), s.getHedefBirim().getAd(),
                s.getHedefKullanici() == null ? null : s.getHedefKullanici().getKullaniciAdi(),
                s.getTalimat(), s.getDurum(), s.getSevkZamani(), s.getAlinmaZamani());
    }
}
