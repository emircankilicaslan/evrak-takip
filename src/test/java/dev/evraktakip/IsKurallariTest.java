package dev.evraktakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Durum geçişi kuralları, olumsuz yollar, resmi tatil etkisi ve denetim kaydı. */
class IsKurallariTest extends EntegrasyonTemeli {

    private long tamamlanmisEvrak() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        gonder("t_amir", "/api/evraklar/" + id + "/tamamla", "{}").andExpect(status().isOk());
        return id;
    }

    // ------------------------------------------------------------ gelen evrak

    @Test
    void tamamlananEvrakTekrarTamamlanamazVeHavaleEdilemez() throws Exception {
        long id = tamamlanmisEvrak();
        gonder("t_amir", "/api/evraklar/" + id + "/tamamla", "{}").andExpect(status().isUnprocessableEntity());
        gonder("t_yonetici", "/api/evraklar/" + id + "/sevk", "{\"hedefBirimId\":" + idari.getId() + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void arsivlenenEvrakTekrarArsivlenemez() throws Exception {
        long id = tamamlanmisEvrak();
        gonder("t_arsiv", "/api/evraklar/" + id + "/arsivle", "{\"dosyaKodu\":\"010\"}").andExpect(status().isOk());
        gonder("t_arsiv", "/api/evraklar/" + id + "/arsivle", "{\"dosyaKodu\":\"010\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void olmayanDosyaKoduReddedilir() throws Exception {
        long id = tamamlanmisEvrak();
        gonder("t_arsiv", "/api/evraklar/" + id + "/arsivle", "{\"dosyaKodu\":\"999\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void kayitMemuruIkinciHavaleyiYapamaz() throws Exception {
        long id = gelenKaydet();
        birimeHavaleEt(id, bidb);
        gonder("t_kayit", "/api/evraklar/" + id + "/sevk", "{\"hedefBirimId\":" + idari.getId() + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ustYoneticiHavaleyiBaskaBirimeYonlendirebilir() throws Exception {
        long id = gelenKaydet();
        birimeHavaleEt(id, bidb);
        gonder("t_yonetici", "/api/evraklar/" + id + "/sevk", "{\"hedefBirimId\":" + idari.getId() + "}")
                .andExpect(status().isOk());
        al("t_baska_amir", "/api/evraklar/" + id).andExpect(status().isOk());
        al("t_personel", "/api/evraklar/" + id).andExpect(status().isNotFound());
    }

    @Test
    void amirEvrakiBaskaBirimeHavaleEdinceKendiGormezOlur() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        gonder("t_amir", "/api/evraklar/" + id + "/sevk", "{\"hedefBirimId\":" + idari.getId() + "}")
                .andExpect(status().isOk());
        al("t_amir", "/api/evraklar/" + id).andExpect(status().isOk()); // havale eden olarak zincirde
        gonder("t_amir", "/api/evraklar/" + id + "/sevk", "{\"hedefBirimId\":" + bidb.getId() + "}")
                .andExpect(status().isUnprocessableEntity()); // artık biriminde değil
    }

    @Test
    void kendineVeBirimsizKullaniciyaHavaleYapilamaz() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        gonder("t_amir", "/api/evraklar/" + id + "/sevk", "{\"hedefKullaniciId\":" + kimlik("t_amir") + "}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_amir", "/api/evraklar/" + id + "/sevk", "{\"hedefKullaniciId\":" + kimlik("t_kayit") + "}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_amir", "/api/evraklar/" + id + "/sevk", "{\"hedefKullaniciId\":" + kimlik("t_admin") + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pasifKullaniciyaHavaleYapilamaz() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        guncelle("t_admin", "/api/kullanicilar/" + kimlik("t_personel2"),
                "{\"rol\":\"BIRIM_PERSONELI\",\"birimId\":" + bidb.getId() + ",\"durum\":\"PASIF\"}").andExpect(status().isOk());
        gonder("t_amir", "/api/evraklar/" + id + "/sevk", "{\"hedefKullaniciId\":" + kimlik("t_personel2") + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void olmayanKayitlarIcin404Doner() throws Exception {
        al("t_kayit", "/api/evraklar/999999").andExpect(status().isNotFound());
        gonder("t_kayit", "/api/evraklar/999999/sevk", "{\"hedefBirimId\":" + bidb.getId() + "}").andExpect(status().isNotFound());
        gonder("t_amir", "/api/evraklar/sevkler/999999/teslim-al", null).andExpect(status().isNotFound());
        long id = gelenKaydet();
        gonder("t_kayit", "/api/evraklar/" + id + "/sevk", "{\"hedefBirimId\":999999}").andExpect(status().isNotFound());
        gonder("t_kayit", "/api/evraklar/" + id + "/sevk", "{\"hedefKullaniciId\":999999}").andExpect(status().isNotFound());
    }

    @Test
    void sevkIkinciKezTeslimAlinamaz() throws Exception {
        long id = gelenKaydet();
        long sevk = birimeHavaleEt(id, bidb);
        gonder("t_amir", "/api/evraklar/sevkler/" + sevk + "/teslim-al", null).andExpect(status().isOk());
        gonder("t_amir", "/api/evraklar/sevkler/" + sevk + "/teslim-al", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void teslimAlmaZamaniKaydedilir() throws Exception {
        long id = gelenKaydet();
        long sevk = birimeHavaleEt(id, bidb);
        gonder("t_amir", "/api/evraklar/sevkler/" + sevk + "/teslim-al", null)
                .andExpect(jsonPath("$.durum").value("ALINDI"))
                .andExpect(jsonPath("$.alinmaZamani").exists());
    }

    @Test
    void personelEvrakiAmirSevkEtmedenTamamlayamaz() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        // birim düzeyinde teslim alan amir; personel tamamlayamaz
        gonder("t_personel", "/api/evraklar/" + id + "/tamamla", "{}").andExpect(status().isForbidden());
    }

    @Test
    void giden_evrakSevkEdilemezVeTamamlanamaz() throws Exception {
        String yanit = govde(gonder("t_personel", "/api/evraklar/giden",
                "{\"konu\":\"G\",\"ilgiliKurum\":\"K\",\"gizlilik\":\"NORMAL\"}").andExpect(status().isCreated()));
        long id = ((Number) JsonPath.read(yanit, "$.id")).longValue();
        gonder("t_yonetici", "/api/evraklar/" + id + "/sevk", "{\"hedefBirimId\":" + bidb.getId() + "}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_personel", "/api/evraklar/" + id + "/tamamla", "{}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void imzaAdimlariSiraylaIslerVeBaskasiGonderemez() throws Exception {
        String yanit = govde(gonder("t_personel", "/api/evraklar/giden",
                "{\"konu\":\"G\",\"ilgiliKurum\":\"K\",\"gizlilik\":\"NORMAL\"}").andExpect(status().isCreated()));
        long id = ((Number) JsonPath.read(yanit, "$.id")).longValue();
        gonder("t_amir", "/api/evraklar/" + id + "/imzala", null).andExpect(status().isUnprocessableEntity()); // imzaya gönderilmedi
        gonder("t_amir", "/api/evraklar/" + id + "/imzaya-gonder", null).andExpect(status().isForbidden()); // hazırlayan değil
        gonder("t_personel", "/api/evraklar/" + id + "/imzaya-gonder", null).andExpect(status().isOk());
        gonder("t_personel", "/api/evraklar/" + id + "/imzaya-gonder", null).andExpect(status().isUnprocessableEntity());
        gonder("t_amir", "/api/evraklar/" + id + "/imzala", null).andExpect(status().isOk());
        gonder("t_amir", "/api/evraklar/" + id + "/imzala", null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cevapYazilacakEvrakBirimdeOlmalidir() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        gonder("t_baska_personel", "/api/evraklar/giden",
                "{\"konu\":\"G\",\"ilgiliKurum\":\"K\",\"gizlilik\":\"NORMAL\",\"ilgiliEvrakId\":" + id + "}")
                .andExpect(status().isNotFound());
        gonder("t_personel", "/api/evraklar/giden",
                "{\"konu\":\"G\",\"ilgiliKurum\":\"K\",\"gizlilik\":\"NORMAL\",\"ilgiliEvrakId\":999999}")
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ resmi tatil

    @Test
    void resmiTatilCevapSuresiniEtkiler() throws Exception {
        gonder("t_admin", "/api/resmi-tatiller", "{\"tarih\":\"2026-03-03\",\"ad\":\"Deneme tatili\"}")
                .andExpect(status().isCreated());
        long id = gelenKaydet("NORMAL", "GUNLUDUR", "1"); // 2 Mart + 1 iş günü: 3 Mart tatil → 4 Mart
        al("t_kayit", "/api/evraklar/" + id).andExpect(jsonPath("$.evrak.cevapSuresiSonu").value("2026-03-04"));
    }

    @Test
    void ayniTatilIkinciKezEklenemez() throws Exception {
        gonder("t_admin", "/api/resmi-tatiller", "{\"tarih\":\"2026-06-15\",\"ad\":\"Bir\"}").andExpect(status().isCreated());
        gonder("t_admin", "/api/resmi-tatiller", "{\"tarih\":\"2026-06-15\",\"ad\":\"Iki\"}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_admin", "/api/resmi-tatiller", "{\"tarih\":\"2026-01-01\",\"ad\":\"Tekrar\"}")
                .andExpect(status().isUnprocessableEntity()); // tohum veride var
    }

    @Test
    void cumaGunuKayitHaftaSonunuAtlar() throws Exception {
        saat.ayarla(LocalDate.of(2026, 3, 6)); // Cuma
        gonder("t_kayit", "/api/evraklar/gelen", """
                {"konu":"A","ilgiliKurum":"K","evrakTarihi":"2026-03-06","gizlilik":"NORMAL","aciliyet":"GUNLUDUR","cevapGunu":1}""")
                .andExpect(jsonPath("$.cevapSuresiSonu").value("2026-03-09"));
    }

    @Test
    void yilDegisincekayitNumarasiYenidenBaslar() throws Exception {
        gelenKaydet();
        saat.ayarla(LocalDate.of(2027, 1, 4));
        gonder("t_kayit", "/api/evraklar/gelen", """
                {"konu":"A","ilgiliKurum":"K","evrakTarihi":"2027-01-04","gizlilik":"NORMAL","aciliyet":"NORMAL"}""")
                .andExpect(jsonPath("$.kayitNo").value("2027-GEL-000001"));
    }

    @Test
    void sayacSatiriOlmayanYilIcinOlusturulur() throws Exception {
        saat.ayarla(LocalDate.of(2031, 1, 6));
        gonder("t_kayit", "/api/evraklar/gelen", """
                {"konu":"A","ilgiliKurum":"K","evrakTarihi":"2031-01-06","gizlilik":"NORMAL","aciliyet":"NORMAL"}""")
                .andExpect(jsonPath("$.kayitNo").value("2031-GEL-000001"));
    }

    // -------------------------------------------------------------- denetim

    @Test
    void islemlerDenetimKaydinaYazilirKonuMetniYazilmaz() throws Exception {
        long id = tamamlanmisEvrak();
        gonder("t_arsiv", "/api/evraklar/" + id + "/arsivle", "{\"dosyaKodu\":\"010\"}").andExpect(status().isOk());
        basvuruKaydet();
        String denetim = govde(al("t_denetci", "/api/denetim?size=200").andExpect(status().isOk()));
        assertThat(denetim).contains("GELEN_KAYIT", "SEVK", "TESLIM_AL", "TAMAMLA", "ARSIVLE", "BASVURU_OLUSTUR", "GIRIS");
        assertThat(denetim).doesNotContain("Bilgi talebi").doesNotContain("ihale listesi");
    }

    @Test
    void denetimKaydiVeritabaninda_degistirilemezVeSilinemez() throws Exception {
        gelenKaydet();
        assertThatThrownBy(() -> jdbc.update("update denetim_kaydi set islem = 'X'")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from denetim_kaydi")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void denetimKaydiVarlikaGoreSuzulur() throws Exception {
        long id = gelenKaydet();
        birimeHavaleEt(id, bidb);
        al("t_denetci", "/api/denetim?varlikTuru=EVRAK&varlikId=" + id)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].islem").value("SEVK"));
    }

    // ----------------------------------------------------------- bilgi edinme

    @Test
    void sonuclanmisBasvuruHavaleEdilemezVeGorusYazilamaz() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/reddet",
                "{\"nedeni\":\"BILGI_MEVCUT_DEGIL\",\"gerekce\":\"Kurumda bu bilgi bulunmamaktadır\"}").andExpect(status().isOk());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/havale", "{\"birimId\":" + bidb.getId() + "}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/yonlendir", "{\"kurum\":\"X\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void birimGorusuYalnizcaIncelemedekiBasvuruyaYazilir() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/birim-gorusu", "{\"metin\":\"x\"}").andExpect(status().isForbidden());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/havale", "{\"birimId\":" + bidb.getId() + "}").andExpect(status().isOk());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/yonlendir", "{\"kurum\":\"X\"}")
                .andExpect(status().isUnprocessableEntity()); // havaleden sonra yönlendirilemez
    }

    @Test
    void havaleOlmayanBirimeYapilamazVeBaskasiGoremez() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/havale", "{\"birimId\":999999}").andExpect(status().isNotFound());
        al("t_bilgi", "/api/bilgi-edinme/999999").andExpect(status().isNotFound());
    }

    @Test
    void havaleIkinciBirimeYapilabilirVeBirinciBirimGormezOlur() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/havale", "{\"birimId\":" + bidb.getId() + "}").andExpect(status().isOk());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/havale", "{\"birimId\":" + idari.getId() + "}").andExpect(status().isOk());
        al("t_personel", "/api/bilgi-edinme/" + id).andExpect(status().isNotFound());
        al("t_baska_personel", "/api/bilgi-edinme/" + id).andExpect(status().isOk());
    }

    @Test
    void uzatilanBasvuruSonrasindaYineCevaplanabilir() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/uzat", "{\"gerekce\":\"Ek süre gerekli\"}").andExpect(status().isOk());
        saat.ayarla(LocalDate.of(2026, 4, 1)); // ilk süre (23 Mart) geçti ama uzatılmış süre (13 Nisan) içinde
        al("t_bilgi", "/api/bilgi-edinme/" + id).andExpect(jsonPath("$.gecikmis").value(false));
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/cevapla", "{\"metin\":\"Cevap\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.gecikmeliSonuc").value(false));
    }

    @Test
    void itirazSonTarihiHaftaSonunaDenkGelirseIsGununeUzar() throws Exception {
        long id = basvuruKaydet();
        saat.ayarla(LocalDate.of(2026, 3, 6)); // Cuma + 15 gün = 21 Mart Cumartesi → 23 Mart
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/cevapla", "{\"metin\":\"Cevap\"}")
                .andExpect(jsonPath("$.itirazSonTarihi").value("2026-03-23"));
    }

    @Test
    void yonlendirilenBasvuruGecikmeliSayilmaz() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/yonlendir", "{\"kurum\":\"Maliye Bakanlığı\"}")
                .andExpect(jsonPath("$.gecikmeliSonuc").value(false))
                .andExpect(jsonPath("$.gecikmis").value(false));
        al("t_bilgi", "/api/ozet").andExpect(jsonPath("$.bilgiEdinmeAcik").value(0));
    }

    @Test
    void ayrintiEkraniBasvuranAdiniTamGosterir() throws Exception {
        long id = basvuruKaydet();
        al("t_denetci", "/api/bilgi-edinme/" + id).andExpect(jsonPath("$.basvuranAd").value(containsString("Ayşe Yılmaz")));
    }
}
