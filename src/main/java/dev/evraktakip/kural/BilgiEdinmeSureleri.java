package dev.evraktakip.kural;

import dev.evraktakip.config.EvrakProperties;
import java.time.LocalDate;

/**
 * Bilgi edinme başvurusuna ilişkin süre hesapları. Süre değerleri yapılandırmadan gelir
 * (varsayılanlar ÖRNEKTİR; kurum kendi mevzuat yorumuna göre ayarlar). Saf sınıf: veritabanı bilmez.
 */
public class BilgiEdinmeSureleri {
    private final IsGunuTakvimi takvim;
    private final EvrakProperties.Kurallar kurallar;

    public BilgiEdinmeSureleri(IsGunuTakvimi takvim, EvrakProperties.Kurallar kurallar) {
        this.takvim = takvim;
        this.kurallar = kurallar;
    }

    /** Başvuru tarihinden sonraki cevap süresi iş günü kadar. */
    public LocalDate cevapSureSonu(LocalDate basvuruTarihi) {
        return takvim.isGunuEkle(basvuruTarihi, kurallar.bilgiEdinmeCevapIsGunu());
    }

    /** Uzatma, mevcut süre sonuna eklenir (bugüne değil). */
    public LocalDate uzatilmisSureSonu(LocalDate mevcutSureSonu) {
        return takvim.isGunuEkle(mevcutSureSonu, kurallar.bilgiEdinmeUzatmaIsGunu());
    }

    /** Yetkisiz kuruma yapılan başvurunun yönlendirilebileceği son gün (takvim günü; tatile denk gelirse uzar). */
    public LocalDate yonlendirmeSonGunu(LocalDate basvuruTarihi) {
        return takvim.sureSonu(basvuruTarihi, kurallar.bilgiEdinmeYonlendirmeGun());
    }

    /** Cevap veya ret tarihinden itibaren itiraz süresi (takvim günü; tatile denk gelirse uzar). */
    public LocalDate itirazSonGunu(LocalDate sonucTarihi) {
        return takvim.sureSonu(sonucTarihi, kurallar.bilgiEdinmeItirazGun());
    }
}
