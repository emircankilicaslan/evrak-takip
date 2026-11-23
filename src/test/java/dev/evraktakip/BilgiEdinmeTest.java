package dev.evraktakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 4982 bilgi edinme başvuruları: süreler, uzatma, cevap/ret, yönlendirme, KVKK ve yetki. */
class BilgiEdinmeTest extends EntegrasyonTemeli {

    private static final String GECERLI_TC = "10000000146";

    // ------------------------------------------------------------------ süreler

    @Test
    void basvuruOnBesIsGunuSureyleKaydedilir() throws Exception {
        gonder("t_bilgi", "/api/bilgi-edinme", basvuruIstegi("Ayşe Yılmaz", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.basvuruNo").value("2026-BE-000001"))
                .andExpect(jsonPath("$.durum").value("ALINDI"))
                .andExpect(jsonPath("$.sureSonu").value("2026-03-23"))
                .andExpect(jsonPath("$.kalanIsGunu").value(15));
    }

    @Test
    void sureGerekceliVeBirKezUzatilirMevcutSureyeEklenir() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/uzat", "{\"gerekce\":\"Başka birimlerden bilgi toplanacak\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sureSonu").value("2026-04-13"))
                .andExpect(jsonPath("$.uzatmaSayisi").value(1));
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/uzat", "{\"gerekce\":\"Tekrar\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void uzatmaGerekcesiZorunludur() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/uzat", "{\"gerekce\":\" \"}").andExpect(status().isBadRequest());
    }

    @Test
    void suresiDolanBasvuruUzatilamazVeGecikmisIsaretlenir() throws Exception {
        long id = basvuruKaydet();
        saat.ayarla(LocalDate.of(2026, 3, 24));
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/uzat", "{\"gerekce\":\"Geç kalındı\"}")
                .andExpect(status().isUnprocessableEntity());
        al("t_bilgi", "/api/bilgi-edinme/" + id)
                .andExpect(jsonPath("$.gecikmis").value(true))
                .andExpect(jsonPath("$.kalanIsGunu").value(-1));
        al("t_bilgi", "/api/ozet").andExpect(jsonPath("$.bilgiEdinmeGeciken").value(1));
        alSorgu("t_bilgi", "/api/bilgi-edinme", "geciken", "true").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void yaklasanBasvuruOzettedeSayilir() throws Exception {
        basvuruKaydet();
        saat.ayarla(LocalDate.of(2026, 3, 19)); // 23 Mart'a 2 iş günü
        al("t_bilgi", "/api/ozet").andExpect(jsonPath("$.bilgiEdinmeYaklasan").value(1))
                .andExpect(jsonPath("$.bilgiEdinmeGeciken").value(0));
    }

    // ---------------------------------------------------------------- sonuçlandırma

    @Test
    void cevapIleItirazSuresiBaslarVeIkinciIslemYapilamaz() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/cevapla", "{\"metin\":\"Liste ektedir\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("CEVAPLANDI"))
                .andExpect(jsonPath("$.itirazSonTarihi").value("2026-03-17"))
                .andExpect(jsonPath("$.gecikmeliSonuc").value(false));
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/cevapla", "{\"metin\":\"Tekrar\"}")
                .andExpect(status().isUnprocessableEntity());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/uzat", "{\"gerekce\":\"x\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void gecVerilenCevapGecikmeliSayilir() throws Exception {
        long id = basvuruKaydet();
        saat.ayarla(LocalDate.of(2026, 3, 25));
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/cevapla", "{\"metin\":\"Geç cevap\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.gecikmeliSonuc").value(true));
    }

    @Test
    void retNedeniVeGerekceZorunludur() throws Exception {
        long id = basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/reddet", "{\"gerekce\":\"Gizli\"}").andExpect(status().isBadRequest());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/reddet", "{\"nedeni\":\"DIGER\",\"gerekce\":\" \"}")
                .andExpect(status().isBadRequest());
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/reddet",
                "{\"nedeni\":\"ISTISNA_KAPSAMINDA\",\"gerekce\":\"Kurum içi düzenleme kapsamında\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("REDDEDILDI"))
                .andExpect(jsonPath("$.retNedeni").value("ISTISNA_KAPSAMINDA"))
                .andExpect(jsonPath("$.itirazSonTarihi").value("2026-03-17"));
    }

    @Test
    void yetkisizKuruma_yapilanBasvuru_sureIcindeYonlendirilir() throws Exception {
        long id = basvuruKaydet();
        saat.ayarla(LocalDate.of(2026, 3, 9)); // 7. gün (Pazartesi)
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/yonlendir", "{\"kurum\":\"Maliye Bakanlığı\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durum").value("YONLENDIRILDI"))
                .andExpect(jsonPath("$.yonlendirilenKurum").value("Maliye Bakanlığı"));
    }

    @Test
    void yonlendirmeSuresiGecinceYapilamaz() throws Exception {
        long id = basvuruKaydet();
        saat.ayarla(LocalDate.of(2026, 3, 10));
        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/yonlendir", "{\"kurum\":\"Maliye Bakanlığı\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------- KVKK

    @Test
    void gecersizKimlikNumarasiReddedilirGecerliOlanYalnizcaMaskeliSaklanir() throws Exception {
        gonder("t_bilgi", "/api/bilgi-edinme", basvuruIstegi("Ayşe Yılmaz", "12345678901"))
                .andExpect(status().isUnprocessableEntity());
        gonder("t_bilgi", "/api/bilgi-edinme", basvuruIstegi("Ayşe Yılmaz", "123"))
                .andExpect(status().isBadRequest());
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(
                govde(gonder("t_bilgi", "/api/bilgi-edinme", basvuruIstegi("Ayşe Yılmaz", GECERLI_TC))
                        .andExpect(status().isCreated())), "$.id")).longValue();

        String kolon = jdbc.queryForObject("select tc_maskeli from bilgi_edinme where id = ?", String.class, id);
        assertThat(kolon).isEqualTo("100******46");
        String detay = govde(al("t_bilgi", "/api/bilgi-edinme/" + id).andExpect(status().isOk()));
        assertThat(detay).contains("100******46").doesNotContain(GECERLI_TC);
    }

    @Test
    void listedeBasvuranAdiMaskelidirAyrintidaTamdir() throws Exception {
        long id = basvuruKaydet();
        alSorgu("t_bilgi", "/api/bilgi-edinme")
                .andExpect(jsonPath("$.content[0].basvuranAd").value("A*** Y***"))
                .andExpect(jsonPath("$.content[0].iletisim").doesNotExist());
        al("t_bilgi", "/api/bilgi-edinme/" + id).andExpect(jsonPath("$.basvuranAd").value("Ayşe Yılmaz"));
    }

    @Test
    void ayrintiGoruntulemeDenetimKaydinaYazilir() throws Exception {
        long id = basvuruKaydet();
        al("t_yonetici", "/api/bilgi-edinme/" + id).andExpect(status().isOk());
        Integer adet = jdbc.queryForObject(
                "select count(*) from denetim_kaydi where islem = 'GORUNTULE' and varlik_turu = 'BILGI_EDINME' and varlik_id = ?",
                Integer.class, id);
        assertThat(adet).isEqualTo(1);
    }

    // ------------------------------------------------------------ birim ve yetki

    @Test
    void birimYalnizcaKendisineHavaleEdilenBasvuruyuGorurVeGorusYazar() throws Exception {
        long id = basvuruKaydet();
        al("t_personel", "/api/bilgi-edinme/" + id).andExpect(status().isNotFound());

        gonder("t_bilgi", "/api/bilgi-edinme/" + id + "/havale", "{\"birimId\":" + bidb.getId() + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.durum").value("INCELEMEDE"));

        al("t_personel", "/api/bilgi-edinme/" + id).andExpect(status().isOk());
        al("t_baska_personel", "/api/bilgi-edinme/" + id).andExpect(status().isNotFound());
        alSorgu("t_baska_personel", "/api/bilgi-edinme").andExpect(jsonPath("$.totalElements").value(0));

        gonder("t_personel", "/api/bilgi-edinme/" + id + "/birim-gorusu", "{\"metin\":\"Bilgi arşivde mevcut\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.birimGorusu").value("Bilgi arşivde mevcut"));
        gonder("t_baska_personel", "/api/bilgi-edinme/" + id + "/birim-gorusu", "{\"metin\":\"x\"}")
                .andExpect(status().isNotFound());
        // cevap yalnızca bilgi edinme sorumlusundadır
        gonder("t_personel", "/api/bilgi-edinme/" + id + "/cevapla", "{\"metin\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void yetkisizRollerBasvurulariGoremez() throws Exception {
        for (String kim : new String[] {"t_kayit", "t_arsiv", "t_admin"}) {
            al(kim, "/api/bilgi-edinme").andExpect(status().isForbidden());
        }
        for (String kim : new String[] {"t_personel", "t_amir", "t_denetci", "t_arsiv", "t_admin"}) {
            gonder(kim, "/api/bilgi-edinme", basvuruIstegi("Ayşe Yılmaz", null)).andExpect(status().isForbidden());
        }
        gonder("t_kayit", "/api/bilgi-edinme", basvuruIstegi("Ayşe Yılmaz", null)).andExpect(status().isCreated());
    }

    @Test
    void gelecekTarihliBasvuruReddedilir() throws Exception {
        gonder("t_bilgi", "/api/bilgi-edinme", """
                {"basvuranAd":"Ayşe Yılmaz","kanal":"E_DEVLET","konu":"Talep","basvuruTarihi":"2026-03-03"}""")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void gecmisTarihliDilekceIcinSureBasvuruTarihindenBaslar() throws Exception {
        // 23 Şubat Pazartesi + 15 iş günü = 16 Mart
        gonder("t_bilgi", "/api/bilgi-edinme", """
                {"basvuranAd":"Ayşe Yılmaz","kanal":"DILEKCE","konu":"Talep","basvuruTarihi":"2026-02-23"}""")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sureSonu").value("2026-03-16"));
    }

    @Test
    void ayniGunKayitlarArdisikNumaraAlir() throws Exception {
        basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme", basvuruIstegi("Veli Demir", null))
                .andExpect(jsonPath("$.basvuruNo").value("2026-BE-000002"));
    }
}
