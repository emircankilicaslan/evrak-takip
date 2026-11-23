package dev.evraktakip;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** İstek doğrulama: alan sınırları, zorunlu alanlar, geçersiz sabit değerler. */
class DogrulamaTest extends EntegrasyonTemeli {

    private static String gelen(String konu, String kurum, String gizlilik, String aciliyet, String cevapGunu) {
        return """
                {"konu":"%s","ilgiliKurum":"%s","evrakTarihi":"2026-03-02","gizlilik":"%s","aciliyet":"%s","cevapGunu":%s}"""
                .formatted(konu, kurum, gizlilik, aciliyet, cevapGunu);
    }

    @Test
    void gecerliGelenEvrakKabulEdilir() throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", gelen("Konu", "Kurum", "NORMAL", "NORMAL", "null"))
                .andExpect(status().isCreated());
    }

    @Test
    void konuTamUcYuzKarakterKabulEdilirUcYuzBirReddedilir() throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", gelen("x".repeat(300), "Kurum", "NORMAL", "NORMAL", "null"))
                .andExpect(status().isCreated());
        gonder("t_kayit", "/api/evraklar/gelen", gelen("x".repeat(301), "Kurum", "NORMAL", "NORMAL", "null"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "bos konu [{0}]")
    @ValueSource(strings = {"", " ", "   "})
    void bosKonuReddedilir(String konu) throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", gelen(konu, "Kurum", "NORMAL", "NORMAL", "null"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "bos kurum [{0}]")
    @ValueSource(strings = {"", " "})
    void bosKurumReddedilir(String kurum) throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", gelen("Konu", kurum, "NORMAL", "NORMAL", "null"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "cevap gunu {0} -> {1}")
    @CsvSource({"0,400", "-1,400", "61,400", "1,201", "60,201"})
    void cevapGunuSiniriBirIleAltmisArasindadir(String gun, int beklenen) throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", gelen("Konu", "Kurum", "NORMAL", "GUNLUDUR", gun))
                .andExpect(status().is(beklenen));
    }

    @ParameterizedTest(name = "gecersiz gizlilik/aciliyet [{0}/{1}]")
    @CsvSource({"COK_GIZLI,NORMAL", "NORMAL,ACIL", "normal,NORMAL", "NORMAL,acele"})
    void gecersizSabitDegerlerReddedilir(String gizlilik, String aciliyet) throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", gelen("Konu", "Kurum", gizlilik, aciliyet, "null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zorunluAlanlarEksikseReddedilir() throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", "{}").andExpect(status().isBadRequest());
        gonder("t_kayit", "/api/evraklar/gelen", """
                {"konu":"K","ilgiliKurum":"Kurum","gizlilik":"NORMAL","aciliyet":"NORMAL"}""")
                .andExpect(status().isBadRequest()); // evrak tarihi yok
    }

    @Test
    void bozukJsonReddedilir() throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", "{bozuk").andExpect(status().isBadRequest());
    }

    @Test
    void disSayiUzunlugaSinirlidir() throws Exception {
        String istek = """
                {"konu":"K","ilgiliKurum":"Kurum","evrakTarihi":"2026-03-02","disSayi":"%s","gizlilik":"NORMAL","aciliyet":"NORMAL"}""";
        gonder("t_kayit", "/api/evraklar/gelen", istek.formatted("1".repeat(60))).andExpect(status().isCreated());
        gonder("t_kayit", "/api/evraklar/gelen", istek.formatted("1".repeat(61))).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "giden evrak bos alan [{0}]")
    @CsvSource({"'',Kurum", "Konu,''", "' ',Kurum"})
    void gidenEvrakZorunluAlanlari(String konu, String kurum) throws Exception {
        gonder("t_personel", "/api/evraklar/giden", """
                {"konu":"%s","ilgiliKurum":"%s","gizlilik":"NORMAL"}""".formatted(konu, kurum))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sevkTalimatiBeşYüzKarakterleSinirlidir() throws Exception {
        long id = gelenKaydet();
        gonder("t_kayit", "/api/evraklar/" + id + "/sevk",
                "{\"hedefBirimId\":" + bidb.getId() + ",\"talimat\":\"" + "t".repeat(501) + "\"}")
                .andExpect(status().isBadRequest());
        gonder("t_kayit", "/api/evraklar/" + id + "/sevk",
                "{\"hedefBirimId\":" + bidb.getId() + ",\"talimat\":\"" + "t".repeat(500) + "\"}")
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ bilgi edinme

    @ParameterizedTest(name = "basvuran adi [{0}]")
    @ValueSource(strings = {"", " "})
    void bosBasvuranAdiReddedilir(String ad) throws Exception {
        gonder("t_bilgi", "/api/bilgi-edinme", basvuruIstegi(ad, null)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "tc [{0}] -> {1}")
    @CsvSource({"1234567890,400", "123456789012,400", "abcdefghijk,400", "12345678901,422", "00000000000,422"})
    void kimlikNumarasiBicimiVeSaglamasi(String tc, int beklenen) throws Exception {
        gonder("t_bilgi", "/api/bilgi-edinme", basvuruIstegi("Ayşe Yılmaz", tc)).andExpect(status().is(beklenen));
    }

    @Test
    void talepMetniBinKarakterleSinirlidir() throws Exception {
        String istek = """
                {"basvuranAd":"Ayşe Yılmaz","kanal":"DILEKCE","konu":"%s"}""";
        gonder("t_bilgi", "/api/bilgi-edinme", istek.formatted("k".repeat(1000))).andExpect(status().isCreated());
        gonder("t_bilgi", "/api/bilgi-edinme", istek.formatted("k".repeat(1001))).andExpect(status().isBadRequest());
    }

    @Test
    void gecersizKanalReddedilir() throws Exception {
        gonder("t_bilgi", "/api/bilgi-edinme", """
                {"basvuranAd":"Ayşe Yılmaz","kanal":"FAKS","konu":"Talep"}""").andExpect(status().isBadRequest());
    }

    @Test
    void reddetmedeEksikAlanlarReddedilir() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/reddet", "{}").andExpect(status().isBadRequest());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/reddet", "{\"nedeni\":\"YOK_BOYLE\",\"gerekce\":\"x\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void cevapMetniBosOlamaz() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/cevapla", "{\"metin\":\"\"}").andExpect(status().isBadRequest());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/yonlendir", "{\"kurum\":\" \"}").andExpect(status().isBadRequest());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/havale", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void resmiTatilAlanlariDogrulanir() throws Exception {
        gonder("t_admin", "/api/resmi-tatiller", "{\"ad\":\"Tarihsiz\"}").andExpect(status().isBadRequest());
        gonder("t_admin", "/api/resmi-tatiller", "{\"tarih\":\"2026-06-15\",\"ad\":\"\"}").andExpect(status().isBadRequest());
    }
}
