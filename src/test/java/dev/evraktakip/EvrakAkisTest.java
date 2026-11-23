package dev.evraktakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Evrak kayıt, havale zinciri, gizlilik, imza ve arşiv kuralları (gerçek Oracle, gerçek güvenlik zinciri). */
class EvrakAkisTest extends EntegrasyonTemeli {

    private long sevkVer(String kim, long evrakId, String hedef) throws Exception {
        String yanit = govde(gonder(kim, "/api/evraklar/" + evrakId + "/sevk", hedef).andExpect(status().isOk()));
        return ((Number) JsonPath.read(yanit, "$.id")).longValue();
    }

    // ------------------------------------------------------------------ kayıt

    @Test
    void gelenEvrakSiraliKayitNumarasiAlir() throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", """
                {"konu":"A","ilgiliKurum":"K","evrakTarihi":"2026-03-02","gizlilik":"NORMAL","aciliyet":"NORMAL"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kayitNo").value("2026-GEL-000001"))
                .andExpect(jsonPath("$.durum").value("KAYITLI"));
        gonder("t_kayit", "/api/evraklar/gelen", """
                {"konu":"B","ilgiliKurum":"K","evrakTarihi":"2026-03-02","gizlilik":"NORMAL","aciliyet":"NORMAL"}""")
                .andExpect(jsonPath("$.kayitNo").value("2026-GEL-000002"));
    }

    @Test
    void gunludurEvrakCevapSuresiIsGunuIleHesaplanir() throws Exception {
        // 2 Mart Pazartesi + 5 iş günü = 9 Mart Pazartesi
        long id = gelenKaydet("NORMAL", "GUNLUDUR", "5");
        al("t_kayit", "/api/evraklar/" + id)
                .andExpect(jsonPath("$.evrak.cevapSuresiSonu").value("2026-03-09"))
                .andExpect(jsonPath("$.evrak.kalanIsGunu").value(5))
                .andExpect(jsonPath("$.evrak.gecikmis").value(false));
    }

    @Test
    void aceleEvrakCevapSuresiYapilandirmadanGelir() throws Exception {
        long id = gelenKaydet("NORMAL", "ACELE", "null");
        al("t_kayit", "/api/evraklar/" + id).andExpect(jsonPath("$.evrak.cevapSuresiSonu").value("2026-03-05"));
    }

    @Test
    void gunludurEvrakCevapGunuOlmadanKaydedilemez() throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", """
                {"konu":"A","ilgiliKurum":"K","evrakTarihi":"2026-03-02","gizlilik":"NORMAL","aciliyet":"GUNLUDUR"}""")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void gelecekTarihliEvrakKaydedilemez() throws Exception {
        gonder("t_kayit", "/api/evraklar/gelen", """
                {"konu":"A","ilgiliKurum":"K","evrakTarihi":"2026-03-03","gizlilik":"NORMAL","aciliyet":"NORMAL"}""")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void gecikenEvrakOzettedeVeSuzgecteGorunur() throws Exception {
        long id = gelenKaydet("NORMAL", "GUNLUDUR", "2"); // son gün 4 Mart (tatil değil)
        saat.ayarla(LocalDate.of(2026, 3, 6));
        al("t_yonetici", "/api/ozet").andExpect(jsonPath("$.gecikenEvrak").value(1));
        alSorgu("t_yonetici", "/api/evraklar", "geciken", "true")
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].gecikmis").value(true));
    }

    // ------------------------------------------------------------ havale zinciri

    @Test
    void gelenEvrakHavaleTeslimTamamlamaVeArsivAkisi() throws Exception {
        long id = gelenKaydet("NORMAL", "GUNLUDUR", "5");
        long birimSevki = birimeHavaleEt(id, bidb);
        gonder("t_amir", "/api/evraklar/sevkler/" + birimSevki + "/teslim-al", null).andExpect(status().isOk());

        long kisiSevki = sevkVer("t_amir", id, "{\"hedefKullaniciId\":" + kimlik("t_personel") + ",\"talimat\":\"İnceleyin\"}");
        // teslim almadan tamamlanamaz
        gonder("t_personel", "/api/evraklar/" + id + "/tamamla", "{\"notu\":\"x\"}").andExpect(status().isUnprocessableEntity());
        gonder("t_personel", "/api/evraklar/sevkler/" + kisiSevki + "/teslim-al", null).andExpect(status().isOk());

        // günlüdür evrakta cevap evrakı veya not zorunlu
        gonder("t_personel", "/api/evraklar/" + id + "/tamamla", "{}").andExpect(status().isUnprocessableEntity());
        gonder("t_personel", "/api/evraklar/" + id + "/tamamla", "{\"notu\":\"Bilgi için tarafımıza ulaştı\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("TAMAMLANDI"));

        // arşiv: dosya kodu 010 → 5 yıl saklama
        gonder("t_arsiv", "/api/evraklar/" + id + "/arsivle", "{\"dosyaKodu\":\"010\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("ARSIVLENDI"))
                .andExpect(jsonPath("$.imhaTarihi").value("2031-03-02"));
        al("t_denetci", "/api/evraklar/" + id)
                .andExpect(jsonPath("$.sevkler.length()").value(2))
                .andExpect(jsonPath("$.sevkler[0].durum").value("DEVREDILDI"))
                .andExpect(jsonPath("$.sevkler[1].durum").value("TAMAMLANDI"));
    }

    @Test
    void suresizSaklananDosyaKoduIcinImhaTarihiYoktur() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        gonder("t_amir", "/api/evraklar/" + id + "/tamamla", "{}").andExpect(status().isOk());
        gonder("t_arsiv", "/api/evraklar/" + id + "/arsivle", "{\"dosyaKodu\":\"300\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.imhaTarihi").doesNotExist());
    }

    @Test
    void imhaSuresiDolanEvrakAdayListesindeCikar() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        gonder("t_amir", "/api/evraklar/" + id + "/tamamla", "{}").andExpect(status().isOk());
        gonder("t_arsiv", "/api/evraklar/" + id + "/arsivle", "{\"dosyaKodu\":\"010\"}").andExpect(status().isOk());
        al("t_arsiv", "/api/evraklar/imha-adaylari").andExpect(jsonPath("$.totalElements").value(0));
        saat.ayarla(LocalDate.of(2031, 3, 3));
        al("t_arsiv", "/api/evraklar/imha-adaylari").andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    void tamamlanmamisEvrakArsivlenemez() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        gonder("t_arsiv", "/api/evraklar/" + id + "/arsivle", "{\"dosyaKodu\":\"010\"}").andExpect(status().isNotFound());
        gonder("t_yonetici", "/api/evraklar/" + id + "/arsivle", "{\"dosyaKodu\":\"010\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sevkteHedefBirimVeKullaniciyiBirlikteVermekYasaktir() throws Exception {
        long id = gelenKaydet();
        gonder("t_kayit", "/api/evraklar/" + id + "/sevk",
                "{\"hedefBirimId\":" + bidb.getId() + ",\"hedefKullaniciId\":" + kimlik("t_personel") + "}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_kayit", "/api/evraklar/" + id + "/sevk", "{}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void baskaBirimPersoneliSevkiTeslimAlamaz() throws Exception {
        long id = gelenKaydet();
        long sevk = birimeHavaleEt(id, bidb);
        gonder("t_baska_amir", "/api/evraklar/sevkler/" + sevk + "/teslim-al", null).andExpect(status().isNotFound());
        gonder("t_personel", "/api/evraklar/sevkler/" + sevk + "/teslim-al", null).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ gizlilik

    @Test
    void gizliEvrakYalnizcaBirimeHavaleEdilemez() throws Exception {
        long id = gelenKaydet("GIZLI", "NORMAL", "null");
        gonder("t_kayit", "/api/evraklar/" + id + "/sevk", "{\"hedefBirimId\":" + bidb.getId() + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void gizliEvrakYalnizcaIslemZincirindekilereGorunur() throws Exception {
        long id = gelenKaydet("GIZLI", "NORMAL", "null");
        sevkVer("t_kayit", id, "{\"hedefKullaniciId\":" + kimlik("t_personel") + "}");

        al("t_personel", "/api/evraklar/" + id).andExpect(status().isOk());
        al("t_yonetici", "/api/evraklar/" + id).andExpect(status().isOk());
        al("t_kayit", "/api/evraklar/" + id).andExpect(status().isOk()); // kaydeden ve havale eden
        // aynı birimdeki diğer personel, denetçi ve amir: evrakın varlığını bile göremez
        al("t_personel2", "/api/evraklar/" + id).andExpect(status().isNotFound());
        al("t_amir", "/api/evraklar/" + id).andExpect(status().isNotFound());
        al("t_denetci", "/api/evraklar/" + id).andExpect(status().isNotFound());
        al("t_arsiv", "/api/evraklar/" + id).andExpect(status().isNotFound());
        alSorgu("t_personel2", "/api/evraklar").andExpect(jsonPath("$.totalElements").value(0));
        alSorgu("t_denetci", "/api/evraklar").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void gizliEvrakGoruntulemeDenetimKaydinaYazilir() throws Exception {
        long id = gelenKaydet("GIZLI", "NORMAL", "null");
        al("t_kayit", "/api/evraklar/" + id).andExpect(status().isOk());
        al("t_denetci", "/api/denetim?size=100")
                .andExpect(jsonPath("$.content[*].islem").value(hasItem("GORUNTULE")));
    }

    @Test
    void sevkEdilmemisEvrakBirimPersoneliniGormez() throws Exception {
        gelenKaydet();
        alSorgu("t_personel", "/api/evraklar").andExpect(jsonPath("$.totalElements").value(0));
        alSorgu("t_denetci", "/api/evraklar").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void birimPersoneliYalnizcaKendiBiriminiGorur() throws Exception {
        long id = gelenKaydet();
        birimeHavaleEt(id, bidb);
        al("t_personel", "/api/evraklar/" + id).andExpect(status().isOk());
        al("t_baska_personel", "/api/evraklar/" + id).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ giden evrak ve imza

    private long gidenTaslak(String kim) throws Exception {
        String yanit = govde(gonder(kim, "/api/evraklar/giden",
                "{\"konu\":\"Cevap yazısı\",\"ilgiliKurum\":\"Başka Kurum\",\"gizlilik\":\"NORMAL\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.durum").value("TASLAK"))
                .andExpect(jsonPath("$.kayitNo").doesNotExist()));
        return ((Number) JsonPath.read(yanit, "$.id")).longValue();
    }

    @Test
    void gidenEvrakImzadaSayiAlir() throws Exception {
        long id = gidenTaslak("t_personel");
        gonder("t_personel", "/api/evraklar/" + id + "/imzaya-gonder", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("IMZA_BEKLIYOR"));
        gonder("t_amir", "/api/evraklar/" + id + "/imzala", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("TAMAMLANDI"))
                .andExpect(jsonPath("$.kayitNo").value("2026-GID-000001"))
                .andExpect(jsonPath("$.imzalayan").value("t_amir"));
    }

    @Test
    void evrakiHazirlayanKisiImzalayamaz() throws Exception {
        long id = gidenTaslak("t_amir");
        gonder("t_amir", "/api/evraklar/" + id + "/imzaya-gonder", null).andExpect(status().isOk());
        gonder("t_amir", "/api/evraklar/" + id + "/imzala", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void personelImzalayamazVeBaskaBirimAmiriGoremez() throws Exception {
        long id = gidenTaslak("t_personel");
        gonder("t_personel", "/api/evraklar/" + id + "/imzaya-gonder", null).andExpect(status().isOk());
        gonder("t_personel2", "/api/evraklar/" + id + "/imzala", null).andExpect(status().isForbidden());
        gonder("t_baska_amir", "/api/evraklar/" + id + "/imzala", null).andExpect(status().isNotFound());
    }

    @Test
    void reddedilenImzaTaslagaDonerVeGerekceTasir() throws Exception {
        long id = gidenTaslak("t_personel");
        gonder("t_personel", "/api/evraklar/" + id + "/imzaya-gonder", null).andExpect(status().isOk());
        gonder("t_amir", "/api/evraklar/" + id + "/imza-reddet", "{\"gerekce\":\"Hitap hatalı\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("TASLAK"))
                .andExpect(jsonPath("$.redGerekcesi").value("Hitap hatalı"));
        gonder("t_amir", "/api/evraklar/" + id + "/imza-reddet", "{\"gerekce\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void taslagiBaskaPersonelGormez() throws Exception {
        long id = gidenTaslak("t_personel");
        al("t_personel2", "/api/evraklar/" + id).andExpect(status().isNotFound());
        al("t_amir", "/api/evraklar/" + id).andExpect(status().isOk());
        al("t_denetci", "/api/evraklar/" + id).andExpect(status().isNotFound());
    }

    @Test
    void cevapEvrakiBaglanincaGelenEvrakTamamlanir() throws Exception {
        long gelen = gelenKaydet("NORMAL", "ACELE", "null");
        amirTeslimAlmisHavale(gelen);

        String yanit = govde(gonder("t_amir", "/api/evraklar/giden",
                "{\"konu\":\"Cevap\",\"ilgiliKurum\":\"Başka Kurum\",\"gizlilik\":\"NORMAL\",\"ilgiliEvrakId\":" + gelen + "}")
                .andExpect(status().isCreated()));
        long giden = ((Number) JsonPath.read(yanit, "$.id")).longValue();

        // cevap imzalanmadan bağlanamaz
        gonder("t_amir", "/api/evraklar/" + gelen + "/tamamla", "{\"cevapEvrakId\":" + giden + "}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_amir", "/api/evraklar/" + giden + "/imzaya-gonder", null).andExpect(status().isOk());
        // hazırlayan amir imzalayamaz; başka bir amir bu birimde yok → personelin taslağıyla denemek için yeni taslak
        long digeri = gidenTaslak("t_personel");
        gonder("t_personel", "/api/evraklar/" + digeri + "/imzaya-gonder", null).andExpect(status().isOk());
        gonder("t_amir", "/api/evraklar/" + digeri + "/imzala", null).andExpect(status().isOk());
        // imzalı ama bu evraka cevap vermeyen giden evrak bağlanamaz
        gonder("t_amir", "/api/evraklar/" + gelen + "/tamamla", "{\"cevapEvrakId\":" + digeri + "}")
                .andExpect(status().isUnprocessableEntity());
        // acele evrak cevap veya not olmadan tamamlanamaz
        gonder("t_amir", "/api/evraklar/" + gelen + "/tamamla", "{}").andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------ yetki

    @Test
    void roller_yetkisiz_islemlerde_403_alir() throws Exception {
        gonder("t_kayit", "/api/evraklar/giden",
                "{\"konu\":\"A\",\"ilgiliKurum\":\"K\",\"gizlilik\":\"NORMAL\"}").andExpect(status().isForbidden());
        gonder("t_personel", "/api/evraklar/gelen", """
                {"konu":"A","ilgiliKurum":"K","evrakTarihi":"2026-03-02","gizlilik":"NORMAL","aciliyet":"NORMAL"}""")
                .andExpect(status().isForbidden());
        al("t_admin", "/api/evraklar").andExpect(status().isForbidden());
        al("t_bilgi", "/api/evraklar").andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/evraklar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aramaJokerKarakterleriniDuzMetinSayar() throws Exception {
        gelenKaydet();
        alSorgu("t_kayit", "/api/evraklar", "ara", "%").andExpect(jsonPath("$.totalElements").value(0));
        alSorgu("t_kayit", "/api/evraklar", "ara", "bilgi tal").andExpect(jsonPath("$.totalElements").value(1));
        alSorgu("t_kayit", "/api/evraklar", "ara", "2026-GEL-000001").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sonSayfaDisindaBoyutSinirlanir() throws Exception {
        gelenKaydet();
        var yanit = govde(alSorgu("t_kayit", "/api/evraklar", "boyut", "100000").andExpect(status().isOk()));
        assertThat((Integer) JsonPath.read(yanit, "$.size")).isLessThanOrEqualTo(100);
    }
}
