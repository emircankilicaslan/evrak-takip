package dev.evraktakip;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Rol × uç nokta yetki matrisi ve oturumsuz erişim. */
class YetkiMatrisiTest extends EntegrasyonTemeli {

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            // evrak listesi: yönetici ve bilgi edinme sorumlusu evrak içeriğine erişemez
            "t_kayit,/api/evraklar,200", "t_personel,/api/evraklar,200", "t_amir,/api/evraklar,200",
            "t_yonetici,/api/evraklar,200", "t_arsiv,/api/evraklar,200", "t_denetci,/api/evraklar,200",
            "t_admin,/api/evraklar,403", "t_bilgi,/api/evraklar,403",
            // dosya planı
            "t_kayit,/api/evraklar/dosya-plani,200", "t_arsiv,/api/evraklar/dosya-plani,200",
            "t_admin,/api/evraklar/dosya-plani,403", "t_bilgi,/api/evraklar/dosya-plani,403",
            // imha adayları
            "t_arsiv,/api/evraklar/imha-adaylari,200", "t_yonetici,/api/evraklar/imha-adaylari,200",
            "t_denetci,/api/evraklar/imha-adaylari,200", "t_kayit,/api/evraklar/imha-adaylari,403",
            "t_personel,/api/evraklar/imha-adaylari,403", "t_amir,/api/evraklar/imha-adaylari,403",
            "t_bilgi,/api/evraklar/imha-adaylari,403", "t_admin,/api/evraklar/imha-adaylari,403",
            // gelen kutusu
            "t_personel,/api/evraklar/gelen-kutusu,200", "t_amir,/api/evraklar/gelen-kutusu,200",
            "t_kayit,/api/evraklar/gelen-kutusu,403", "t_yonetici,/api/evraklar/gelen-kutusu,403",
            // bilgi edinme listesi
            "t_bilgi,/api/bilgi-edinme,200", "t_yonetici,/api/bilgi-edinme,200", "t_denetci,/api/bilgi-edinme,200",
            "t_personel,/api/bilgi-edinme,200", "t_amir,/api/bilgi-edinme,200",
            "t_kayit,/api/bilgi-edinme,403", "t_arsiv,/api/bilgi-edinme,403", "t_admin,/api/bilgi-edinme,403",
            // denetim kaydı
            "t_denetci,/api/denetim,200", "t_admin,/api/denetim,200", "t_yonetici,/api/denetim,200",
            "t_kayit,/api/denetim,403", "t_personel,/api/denetim,403", "t_amir,/api/denetim,403",
            "t_bilgi,/api/denetim,403", "t_arsiv,/api/denetim,403",
            // özet ve grafik
            "t_kayit,/api/ozet,200", "t_admin,/api/ozet,200", "t_bilgi,/api/ozet,200",
            "t_yonetici,/api/ozet/grafik,200", "t_personel,/api/ozet/grafik,200",
            "t_admin,/api/ozet/grafik,403", "t_bilgi,/api/ozet/grafik,403",
            // birimler
            "t_kayit,/api/birimler,200", "t_admin,/api/birimler,200", "t_bilgi,/api/birimler,200",
            // kullanıcı yönetimi
            "t_admin,/api/kullanicilar,200", "t_yonetici,/api/kullanicilar,403", "t_denetci,/api/kullanicilar,403",
            // dışa aktarma
            "t_kayit,/api/disa-aktar/evraklar?bicim=csv,200", "t_admin,/api/disa-aktar/evraklar?bicim=csv,403",
            "t_bilgi,/api/disa-aktar/bilgi-edinme?bicim=csv,200", "t_kayit,/api/disa-aktar/bilgi-edinme?bicim=csv,403"
    })
    void getYetkiMatrisi(String kullanici, String yol, int beklenen) throws Exception {
        al(kullanici, yol).andExpect(status().is(beklenen));
    }

    @ParameterizedTest(name = "{0} personel listesi -> {1}")
    @CsvSource({"t_kayit,200", "t_amir,200", "t_yonetici,200", "t_personel,403", "t_bilgi,403", "t_admin,403", "t_denetci,403"})
    void birimPersoneliListesiYalnizcaHavaleYetkililerineAciktir(String kullanici, int beklenen) throws Exception {
        al(kullanici, "/api/birimler/" + bidb.getId() + "/personel").andExpect(status().is(beklenen));
    }

    @ParameterizedTest(name = "{0} resmi tatil ekleme -> {1}")
    @CsvSource({"t_admin,201", "t_yonetici,403", "t_kayit,403", "t_personel,403", "t_bilgi,403", "t_denetci,403"})
    void resmiTatilYalnizcaYoneticiEkler(String kullanici, int beklenen) throws Exception {
        gonder(kullanici, "/api/resmi-tatiller", "{\"tarih\":\"2026-06-15\",\"ad\":\"Deneme tatili\"}")
                .andExpect(status().is(beklenen));
    }

    @ParameterizedTest(name = "oturumsuz {0}")
    @ValueSource(strings = {
            "/api/evraklar", "/api/evraklar/1", "/api/evraklar/gelen-kutusu", "/api/evraklar/dosya-plani",
            "/api/bilgi-edinme", "/api/bilgi-edinme/1", "/api/ozet", "/api/ozet/grafik", "/api/denetim",
            "/api/kullanicilar", "/api/birimler", "/api/auth/ben", "/api/disa-aktar/evraklar",
            "/api/disa-aktar/bilgi-edinme", "/api/disa-aktar/denetim"})
    void oturumsuzIstekler401Alir(String yol) throws Exception {
        mvc.perform(MockMvcRequestBuilders.get(yol)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "oturumsuz POST {0}")
    @ValueSource(strings = {
            "/api/evraklar/gelen", "/api/evraklar/giden", "/api/evraklar/1/sevk", "/api/evraklar/1/tamamla",
            "/api/evraklar/1/imzala", "/api/evraklar/1/arsivle", "/api/bilgi-edinme", "/api/bilgi-edinme/1/cevapla",
            "/api/kullanicilar", "/api/resmi-tatiller"})
    void oturumsuzYazmaIstekleri401Alir(String yol) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post(yol).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gecersizAnahtarReddedilir() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/evraklar").header("Authorization", "Bearer gecersiz.anahtar.degeri"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void girisveSaglikUcNoktalariAcikKalir() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/actuator/health")).andExpect(status().isOk());
        girisYap("t_kayit", "yanlis-parola-1").andExpect(status().isUnauthorized());
        girisYap("t_kayit", SIFRE).andExpect(status().isOk());
    }
}
