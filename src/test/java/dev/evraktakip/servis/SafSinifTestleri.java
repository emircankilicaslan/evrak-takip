package dev.evraktakip.servis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.evraktakip.config.EvrakProperties;
import dev.evraktakip.domain.BasvuruDurumu;
import dev.evraktakip.domain.EvrakDurumu;
import dev.evraktakip.domain.Rol;
import dev.evraktakip.kural.BilgiEdinmeSureleri;
import dev.evraktakip.kural.IsGunuTakvimi;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Veritabanı gerektirmeyen saf sınıf testleri. */
class SafSinifTestleri {
    private static final EvrakProperties.Kurallar KURALLAR = new EvrakProperties.Kurallar(3, 15, 15, 7, 15);
    // 2026-03-04 Çarşamba resmi tatil varsayımı
    private final IsGunuTakvimi takvim = new IsGunuTakvimi(List.of(LocalDate.of(2026, 3, 4)));
    private final BilgiEdinmeSureleri sureler = new BilgiEdinmeSureleri(takvim, KURALLAR);

    // ------------------------------------------------------------ arama kalıbı

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t"})
    void bosAramaKalipUretmez(String ara) {
        assertThat(AramaYardimcisi.kalip(ara, 100)).isNull();
    }

    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource(delimiter = '|', value = {
            "Genelge|%genelge%",
            "  Boşluklu  |%boşluklu%",
            "%|%\\%%",
            "_|%\\_%",
            "a_b%c|%a\\_b\\%c%"})
    void jokerKarakterleriKacirilir(String ara, String beklenen) {
        assertThat(AramaYardimcisi.kalip(ara, 100)).isEqualTo(beklenen);
    }

    @Test
    void kacisKarakteriDeKacirilir() {
        assertThat(AramaYardimcisi.kalip("a\\b", 100)).isEqualTo("%a\\\\b%");
    }

    @Test
    void aramaUzunluguSinirlanir() {
        assertThat(AramaYardimcisi.kalip("x".repeat(500), 100)).isEqualTo("%" + "x".repeat(100) + "%");
    }

    // ------------------------------------------------------------ ad maskeleme

    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource(delimiter = '|', value = {
            "Ayşe Yılmaz|A*** Y***",
            "Mehmet|M***",
            "  Ali   Veli  Can |A*** V*** C***",
            "Zeynep Su Kaya|Z*** S*** K***"})
    void adMaskelenir(String ad, String beklenen) {
        assertThat(BilgiEdinmeServisi.adMaskele(ad)).isEqualTo(beklenen);
    }

    // --------------------------------------------------------------- numaralar

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({"GEL,2026,1,2026-GEL-000001", "GID,2026,42,2026-GID-000042", "BE,2027,123456,2027-BE-123456",
            "GEL,2026,1000000,2026-GEL-1000000"})
    void kayitNumarasiBicimi(String onEk, int yil, long sira, String beklenen) {
        assertThat(SayacServisi.bicim(onEk, yil, sira)).isEqualTo(beklenen);
    }

    // ----------------------------------------------------- bilgi edinme süreleri

    @Test
    void cevapSuresiOnBesIsGunudurTatilSayilmaz() {
        // Pzt 2 Mart: tatil (4 Mart) atlanır → 16 gün sonra 24 Mart
        assertThat(sureler.cevapSureSonu(LocalDate.of(2026, 3, 2))).isEqualTo(LocalDate.of(2026, 3, 24));
    }

    @Test
    void basvuruHaftaSonuYapilirsaSureBirSonrakiIsGunuSayilmaz() {
        // Cumartesi 7 Mart başvurusu: Pzt 9 (1) ... 15. iş günü 27 Mart
        assertThat(sureler.cevapSureSonu(LocalDate.of(2026, 3, 7))).isEqualTo(LocalDate.of(2026, 3, 27));
    }

    @Test
    void uzatmaMevcutSureSonunaEklenir() {
        LocalDate ilk = sureler.cevapSureSonu(LocalDate.of(2026, 3, 2));
        assertThat(sureler.uzatilmisSureSonu(ilk)).isEqualTo(LocalDate.of(2026, 4, 14));
    }

    @Test
    void yonlendirmeSonGunuYediTakvimGunudur() {
        assertThat(sureler.yonlendirmeSonGunu(LocalDate.of(2026, 3, 2))).isEqualTo(LocalDate.of(2026, 3, 9));
        // 8 Mart Pazar başvurusu + 7 = 15 Mart Pazar → 16 Mart
        assertThat(sureler.yonlendirmeSonGunu(LocalDate.of(2026, 3, 8))).isEqualTo(LocalDate.of(2026, 3, 16));
    }

    @Test
    void itirazSonGunuOnBesTakvimGunudurTatileDenkGelirseUzar() {
        assertThat(sureler.itirazSonGunu(LocalDate.of(2026, 3, 2))).isEqualTo(LocalDate.of(2026, 3, 17));
        assertThat(sureler.itirazSonGunu(LocalDate.of(2026, 3, 6))).isEqualTo(LocalDate.of(2026, 3, 23));
    }

    @Test
    void kurallarYapilandirmadanOkunur() {
        var kisa = new BilgiEdinmeSureleri(takvim, new EvrakProperties.Kurallar(3, 5, 5, 2, 3));
        assertThat(kisa.cevapSureSonu(LocalDate.of(2026, 3, 9))).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(kisa.uzatilmisSureSonu(LocalDate.of(2026, 3, 16))).isEqualTo(LocalDate.of(2026, 3, 23));
    }

    // ------------------------------------------------------------- iş günü takvimi

    @Test
    void negatifIsGunuEklenemez() {
        assertThatThrownBy(() -> takvim.isGunuEkle(LocalDate.of(2026, 3, 2), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} iş günü")
    @CsvSource({"2026-03-02,true", "2026-03-04,false", "2026-03-07,false", "2026-03-08,false", "2026-03-09,true"})
    void isGunuTespiti(String gun, boolean beklenen) {
        assertThat(takvim.isGunu(LocalDate.parse(gun))).isEqualTo(beklenen);
    }

    @ParameterizedTest(name = "{0} + {1} iş günü = {2}")
    @CsvSource({"2026-03-02,1,2026-03-03", "2026-03-03,1,2026-03-05", "2026-03-06,1,2026-03-09",
            "2026-03-07,1,2026-03-09", "2026-03-02,10,2026-03-17"})
    void isGunuEkleme(String bas, int n, String beklenen) {
        assertThat(takvim.isGunuEkle(LocalDate.parse(bas), n)).isEqualTo(LocalDate.parse(beklenen));
    }

    // ------------------------------------------------------------------ roller

    @ParameterizedTest
    @EnumSource(value = Rol.class, names = {"BIRIM_PERSONELI", "BIRIM_AMIRI"})
    void birimRolleriBirimleSinirlidir(Rol rol) {
        assertThat(rol.birimleSinirli()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Rol.class, mode = EnumSource.Mode.EXCLUDE, names = {"BIRIM_PERSONELI", "BIRIM_AMIRI"})
    void digerRollerBirimleSinirliDegildir(Rol rol) {
        assertThat(rol.birimleSinirli()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EvrakDurumu.class, names = {"KAYITLI", "HAVALEDE"})
    void islemdekiEvrakDurumlari(EvrakDurumu durum) {
        assertThat(durum.islemde()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = EvrakDurumu.class, mode = EnumSource.Mode.EXCLUDE, names = {"KAYITLI", "HAVALEDE"})
    void sonuclanmisVeyaTaslakEvrakIslemdeDegildir(EvrakDurumu durum) {
        assertThat(durum.islemde()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = BasvuruDurumu.class, names = {"ALINDI", "INCELEMEDE"})
    void acikBasvuruDurumlari(BasvuruDurumu durum) {
        assertThat(durum.acik()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = BasvuruDurumu.class, mode = EnumSource.Mode.EXCLUDE, names = {"ALINDI", "INCELEMEDE"})
    void sonuclanmisBasvuruAcikDegildir(BasvuruDurumu durum) {
        assertThat(durum.acik()).isFalse();
    }
}
