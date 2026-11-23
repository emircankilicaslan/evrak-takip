package dev.evraktakip.servis;

import dev.evraktakip.disaaktar.CsvYazici;
import dev.evraktakip.disaaktar.ExcelYazici;
import dev.evraktakip.domain.BasvuruDurumu;
import dev.evraktakip.domain.EvrakDurumu;
import dev.evraktakip.domain.EvrakYonu;
import dev.evraktakip.domain.Gizlilik;
import dev.evraktakip.domain.Kullanici;
import dev.evraktakip.servis.Hatalar.IsKuraliIhlali;
import dev.evraktakip.web.Dto.BasvuruYaniti;
import dev.evraktakip.web.Dto.EvrakYaniti;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Evrak listesi, bilgi edinme listesi ve denetim kaydını Excel veya CSV olarak üretir.
 * Her dışa aktarma denetim kaydına yazılır. Bilgi edinme dışa aktarımında başvuran adı maskelidir (KVKK).
 */
@Service
public class DisaAktarimServisi {
    /** Tek dosyadaki en çok satır; daha fazlası için süzgeç kullanılmalıdır. */
    public static final int EN_COK_SATIR = 10_000;

    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String CSV = "text/csv; charset=UTF-8";

    public record Dosya(byte[] icerik, String ad, String icerikTuru) {}

    private static final Map<EvrakDurumu, String> EVRAK_DURUM = Map.of(
            EvrakDurumu.TASLAK, "Taslak",
            EvrakDurumu.IMZA_BEKLIYOR, "İmza bekliyor",
            EvrakDurumu.KAYITLI, "Kayıtlı",
            EvrakDurumu.HAVALEDE, "Havalede",
            EvrakDurumu.TAMAMLANDI, "Tamamlandı",
            EvrakDurumu.ARSIVLENDI, "Arşivlendi");
    private static final Map<EvrakYonu, String> YON = Map.of(EvrakYonu.GELEN, "Gelen", EvrakYonu.GIDEN, "Giden");
    private static final Map<Gizlilik, String> GIZLILIK = Map.of(
            Gizlilik.NORMAL, "Normal", Gizlilik.HIZMETE_OZEL, "Hizmete özel", Gizlilik.GIZLI, "Gizli");
    private static final Map<BasvuruDurumu, String> BASVURU_DURUM = Map.of(
            BasvuruDurumu.ALINDI, "Alındı",
            BasvuruDurumu.INCELEMEDE, "İncelemede",
            BasvuruDurumu.CEVAPLANDI, "Cevaplandı",
            BasvuruDurumu.REDDEDILDI, "Reddedildi",
            BasvuruDurumu.YONLENDIRILDI, "Yönlendirildi");

    private final EvrakServisi evraklar;
    private final BilgiEdinmeServisi basvurular;
    private final DenetimServisi denetim;
    private final Clock clock;

    public DisaAktarimServisi(EvrakServisi evraklar, BilgiEdinmeServisi basvurular, DenetimServisi denetim,
                              Clock clock) {
        this.evraklar = evraklar;
        this.basvurular = basvurular;
        this.denetim = denetim;
        this.clock = clock;
    }

    public Dosya evraklar(Kullanici k, EvrakFiltre filtre, EvrakSiralama siralama, String bicim) {
        var sayfa = PageRequest.of(0, EN_COK_SATIR, siralama.sort());
        List<EvrakYaniti> liste = evraklar.listele(k, filtre, sayfa).getContent();

        List<String> baslik = List.of("Kayıt no", "Yön", "Konu", "İlgili kurum", "Evrak tarihi", "Gizlilik", "Birim",
                "Durum", "Cevap süresi sonu", "Gecikmiş", "Oluşturan");
        List<List<Object>> satirlar = new ArrayList<>();
        for (EvrakYaniti e : liste) {
            satirlar.add(java.util.Arrays.asList(e.kayitNo(), YON.get(e.yon()), e.konu(), e.ilgiliKurum(),
                    e.evrakTarihi(), GIZLILIK.get(e.gizlilik()), e.birim(), EVRAK_DURUM.get(e.durum()),
                    e.cevapSuresiSonu(), e.gecikmis() ? "Evet" : "Hayır", e.olusturan()));
        }
        Dosya d = uret("evraklar", "Evraklar", baslik, satirlar, bicim);
        denetim.kaydet(k.getKullaniciAdi(), "DISA_AKTAR", "EVRAK", null, "%s, %d kayıt".formatted(bicim, liste.size()));
        return d;
    }

    public Dosya basvurular(Kullanici k, BilgiEdinmeFiltre filtre, BilgiEdinmeSiralama siralama, String bicim) {
        var sayfa = PageRequest.of(0, EN_COK_SATIR, siralama.sort());
        List<BasvuruYaniti> liste = basvurular.listele(k, filtre, sayfa).getContent();

        List<String> baslik = List.of("Başvuru no", "Başvuran (maskeli)", "Başvuru tarihi", "Durum", "Birim",
                "Süre sonu", "Uzatıldı", "Gecikmiş", "Sonuç tarihi", "İtiraz son tarihi");
        List<List<Object>> satirlar = new ArrayList<>();
        for (BasvuruYaniti b : liste) {
            satirlar.add(java.util.Arrays.asList(b.basvuruNo(), b.basvuranAd(), b.basvuruTarihi(),
                    BASVURU_DURUM.get(b.durum()), b.birim(), b.sureSonu(), b.uzatmaSayisi() > 0 ? "Evet" : "Hayır",
                    b.gecikmis() ? "Evet" : "Hayır", b.sonucTarihi(), b.itirazSonTarihi()));
        }
        Dosya d = uret("bilgi-edinme", "Bilgi edinme", baslik, satirlar, bicim);
        denetim.kaydet(k.getKullaniciAdi(), "DISA_AKTAR", "BILGI_EDINME", null,
                "%s, %d kayıt".formatted(bicim, liste.size()));
        return d;
    }

    public Dosya denetim(Kullanici k, String bicim) {
        var sayfa = PageRequest.of(0, EN_COK_SATIR, Sort.by(Sort.Direction.DESC, "id"));
        var kayitlar = denetim.listele(null, null, sayfa).getContent();

        List<String> baslik = List.of("Zaman", "Kullanıcı", "İşlem", "Kayıt türü", "Kayıt no", "Ayrıntı");
        List<List<Object>> satirlar = new ArrayList<>();
        for (var kayit : kayitlar) {
            satirlar.add(java.util.Arrays.asList(kayit.getZaman(), kayit.getKullaniciAdi(), kayit.getIslem(),
                    kayit.getVarlikTuru(), kayit.getVarlikId(), kayit.getAyrinti()));
        }
        Dosya d = uret("denetim-kaydi", "Denetim kaydı", baslik, satirlar, bicim);
        denetim.kaydet(k.getKullaniciAdi(), "DISA_AKTAR", "DENETIM_KAYDI", null,
                "%s, %d kayıt".formatted(bicim, kayitlar.size()));
        return d;
    }

    private Dosya uret(String dosyaAdi, String sayfaAdi, List<String> baslik, List<List<Object>> satirlar, String bicim) {
        String gun = LocalDate.now(clock).toString();
        return switch (bicim == null ? "" : bicim.toLowerCase()) {
            case "xlsx" -> new Dosya(ExcelYazici.uret(sayfaAdi, baslik, satirlar), dosyaAdi + "-" + gun + ".xlsx", XLSX);
            case "csv" -> new Dosya(CsvYazici.uret(baslik, metneCevir(satirlar)), dosyaAdi + "-" + gun + ".csv", CSV);
            default -> throw new IsKuraliIhlali("Biçim xlsx veya csv olmalıdır");
        };
    }

    /** CSV için hücreler metne çevrilir; tarihler gün.ay.yıl biçiminde yazılır. */
    private static List<List<String>> metneCevir(List<List<Object>> satirlar) {
        List<List<String>> sonuc = new ArrayList<>();
        for (List<Object> satir : satirlar) {
            List<String> s = new ArrayList<>();
            for (Object v : satir) {
                if (v == null) {
                    s.add("");
                } else if (v instanceof LocalDate d) {
                    s.add("%02d.%02d.%d".formatted(d.getDayOfMonth(), d.getMonthValue(), d.getYear()));
                } else if (v instanceof java.time.LocalDateTime z) {
                    s.add("%02d.%02d.%d %02d:%02d".formatted(z.getDayOfMonth(), z.getMonthValue(), z.getYear(),
                            z.getHour(), z.getMinute()));
                } else {
                    s.add(v.toString());
                }
            }
            sonuc.add(s);
        }
        return sonuc;
    }
}
