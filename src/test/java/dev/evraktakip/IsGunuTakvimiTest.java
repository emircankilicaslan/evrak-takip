package dev.evraktakip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.evraktakip.kural.IsGunuTakvimi;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IsGunuTakvimiTest {
    // 2026-03-02 Pazartesi
    private final IsGunuTakvimi takvim = new IsGunuTakvimi(List.of(LocalDate.of(2026, 3, 4)));

    @Test
    void haftaIciIsGunudur() {
        assertThat(takvim.isGunu(LocalDate.of(2026, 3, 2))).isTrue();
    }

    @Test
    void haftaSonuIsGunuDegildir() {
        assertThat(takvim.isGunu(LocalDate.of(2026, 3, 7))).isFalse(); // Cumartesi
        assertThat(takvim.isGunu(LocalDate.of(2026, 3, 8))).isFalse(); // Pazar
    }

    @Test
    void resmiTatilIsGunuDegildir() {
        assertThat(takvim.isGunu(LocalDate.of(2026, 3, 4))).isFalse();
    }

    @Test
    void bitisHaftaSonunaDenkGelirsePazartesiyeUzar() {
        // 2026-03-02 + 5 gün = 2026-03-07 Cumartesi -> 2026-03-09 Pazartesi
        assertThat(takvim.sureSonu(LocalDate.of(2026, 3, 2), 5)).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void bitisTatileDenkGelirseSonrakiIsGunuOlur() {
        // 2026-03-02 + 2 gün = 2026-03-04 (tatil) -> 2026-03-05
        assertThat(takvim.sureSonu(LocalDate.of(2026, 3, 2), 2)).isEqualTo(LocalDate.of(2026, 3, 5));
    }

    @Test
    void bitisIsGunuyseDegismez() {
        assertThat(takvim.sureSonu(LocalDate.of(2026, 3, 2), 15)).isEqualTo(LocalDate.of(2026, 3, 17));
    }

    @Test
    void ardisikTatilVeHaftaSonuBirlikteAtlanir() {
        var t = new IsGunuTakvimi(List.of(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 10)));
        // 2026-03-07 Cumartesi, 08 Pazar, 09-10 tatil -> 11 Çarşamba
        assertThat(t.ilkIsGunu(LocalDate.of(2026, 3, 7))).isEqualTo(LocalDate.of(2026, 3, 11));
    }

    @Test
    void isGunuEkleHaftaSonuVeTatiliSayarak() {
        // Pzt 2: Sal 3 (1), Çar 4 tatil, Per 5 (2), Cum 6 (3), Pzt 9 (4), Sal 10 (5)
        assertThat(takvim.isGunuEkle(LocalDate.of(2026, 3, 2), 5)).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(takvim.isGunuEkle(LocalDate.of(2026, 3, 2), 0)).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void onBesIsGunuUcHaftaEder() {
        var tatilsiz = new IsGunuTakvimi(List.of());
        assertThat(tatilsiz.isGunuEkle(LocalDate.of(2026, 3, 2), 15)).isEqualTo(LocalDate.of(2026, 3, 23));
    }

    @Test
    void isGunuFarkiKalanVeGecenSureyiGosterir() {
        assertThat(takvim.isGunuFarki(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 10))).isEqualTo(5);
        assertThat(takvim.isGunuFarki(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 2))).isEqualTo(-5);
        assertThat(takvim.isGunuFarki(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2))).isZero();
    }
}
