package dev.evraktakip;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Gelen kutusu, özet sayıları ve grafik verileri. */
class EvrakPanoTest extends EntegrasyonTemeli {

    @Test
    void birimeHavaleEdilenEvrakAmirinGelenKutusundaCikar() throws Exception {
        long id = gelenKaydet();
        birimeHavaleEt(id, bidb);
        al("t_amir", "/api/evraklar/gelen-kutusu")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        al("t_amir", "/api/ozet").andExpect(jsonPath("$.gelenKutusu").value(1));
        // personel, birim düzeyindeki havaleyi kutusunda görmez; başka birim de görmez
        al("t_personel", "/api/evraklar/gelen-kutusu").andExpect(jsonPath("$.totalElements").value(0));
        al("t_baska_amir", "/api/evraklar/gelen-kutusu").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void kisiyeHavaleEdilenEvrakYalnizcaOKisininKutusundadir() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        gonder("t_amir", "/api/evraklar/" + id + "/sevk", "{\"hedefKullaniciId\":" + kimlik("t_personel") + "}")
                .andExpect(status().isOk());
        al("t_personel", "/api/evraklar/gelen-kutusu").andExpect(jsonPath("$.totalElements").value(1));
        al("t_personel2", "/api/evraklar/gelen-kutusu").andExpect(jsonPath("$.totalElements").value(0));
        // sevk kişiye devredildiği için amirin kutusu boşalır
        al("t_amir", "/api/evraklar/gelen-kutusu").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void tamamlananEvrakGelenKutusundanCikar() throws Exception {
        long id = gelenKaydet();
        amirTeslimAlmisHavale(id);
        al("t_amir", "/api/evraklar/gelen-kutusu").andExpect(jsonPath("$.totalElements").value(1));
        gonder("t_amir", "/api/evraklar/" + id + "/tamamla", "{}").andExpect(status().isOk());
        al("t_amir", "/api/evraklar/gelen-kutusu").andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"t_kayit", "t_yonetici", "t_bilgi", "t_arsiv", "t_denetci", "t_admin"})
    void gelenKutusuYalnizcaBirimRollerineAciktir(String kullanici) throws Exception {
        al(kullanici, "/api/evraklar/gelen-kutusu").andExpect(status().isForbidden());
    }

    @Test
    void grafikSonAltiAyiVeBirimYukunuVerir() throws Exception {
        long id = gelenKaydet();
        birimeHavaleEt(id, bidb);
        gelenKaydet();
        String yanit = govde(al("t_yonetici", "/api/ozet/grafik").andExpect(status().isOk())
                .andExpect(jsonPath("$.aylik.length()").value(6))
                .andExpect(jsonPath("$.aylik[5].ay").value("2026-03"))
                .andExpect(jsonPath("$.aylik[5].gelen").value(2))
                .andExpect(jsonPath("$.aylik[5].giden").value(0))
                .andExpect(jsonPath("$.aylik[0].ay").value("2025-10")));
        int acik = ((java.util.List<?>) JsonPath.read(yanit, "$.birimYuku[?(@.birim == 'Bilgi İşlem Daire Başkanlığı')].acik")).size();
        org.assertj.core.api.Assertions.assertThat(acik).isEqualTo(1);
        al("t_yonetici", "/api/ozet/grafik")
                .andExpect(jsonPath("$.birimYuku[?(@.birim == 'Bilgi İşlem Daire Başkanlığı')].acik").value(1));
    }

    @Test
    void grafikGizliEvrakiYetkisizeSaymaz() throws Exception {
        gelenKaydet("GIZLI", "NORMAL", "null");
        al("t_denetci", "/api/ozet/grafik").andExpect(jsonPath("$.aylik[5].gelen").value(0));
        al("t_yonetici", "/api/ozet/grafik").andExpect(jsonPath("$.aylik[5].gelen").value(1));
    }

    @Test
    void grafikYetkisizRolIcin403Doner() throws Exception {
        al("t_admin", "/api/ozet/grafik").andExpect(status().isForbidden());
        al("t_bilgi", "/api/ozet/grafik").andExpect(status().isForbidden());
    }

    @Test
    void ozetYetkisizAlanlariSifirGosterir() throws Exception {
        gelenKaydet();
        al("t_admin", "/api/ozet")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evrakDurumlari.KAYITLI").value(0))
                .andExpect(jsonPath("$.bilgiEdinmeAcik").value(0))
                .andExpect(jsonPath("$.gelenKutusu").value(0));
        al("t_yonetici", "/api/ozet").andExpect(jsonPath("$.evrakDurumlari.KAYITLI").value(1));
    }
}
