package dev.evraktakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Liste süzgeçleri, sıralama ve sayfalama. */
class FiltreSiralamaTest extends EntegrasyonTemeli {

    private long kaydet(String konu, String kurum, String disSayi, String gizlilik, String aciliyet, String gun) throws Exception {
        String istek = """
                {"konu":"%s","ilgiliKurum":"%s","disSayi":%s,"evrakTarihi":"2026-03-02","gizlilik":"%s","aciliyet":"%s","cevapGunu":%s}"""
                .formatted(konu, kurum, disSayi == null ? "null" : "\"" + disSayi + "\"", gizlilik, aciliyet, gun);
        String yanit = govde(gonder("t_kayit", "/api/evraklar/gelen", istek).andExpect(status().isCreated()));
        return ((Number) JsonPath.read(yanit, "$.id")).longValue();
    }

    private void veriHazirla() throws Exception {
        kaydet("Bütçe hazırlık genelgesi", "Maliye Bakanlığı", "M-100", "NORMAL", "NORMAL", "null");
        kaydet("Personel alım ilanı", "Devlet Personel Başkanlığı", "P-200", "HIZMETE_OZEL", "ACELE", "null");
        kaydet("Arşiv denetim raporu", "Devlet Arşivleri", "A-300", "NORMAL", "GUNLUDUR", "10");
        kaydet("Toplantı daveti", "Belediyeler Birliği", null, "NORMAL", "GUNLUDUR", "2");
    }

    private void adetBeklenen(String parametre, String deger, int adet) throws Exception {
        alSorgu("t_denetci", "/api/evraklar", parametre, deger).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(adet));
    }

    @ParameterizedTest(name = "ara [{0}] -> {1} kayıt")
    @CsvSource({"bütçe,1", "BÜTÇE,1", "genelge,1", "maliye,1", "devlet,2", "p-200,1", "2026-GEL-000003,1",
            "bulunmayan metin,0", "toplantı,1", "denetim,1"})
    void aramaKonuKurumSayiVeKayitNoyaBakar(String ara, int adet) throws Exception {
        veriHazirla();
        adetBeklenen("ara", ara, adet);
    }

    @ParameterizedTest(name = "gizlilik {0} -> {1}")
    @CsvSource({"NORMAL,3", "HIZMETE_OZEL,1", "GIZLI,0"})
    void gizlilikSuzgeci(String gizlilik, int adet) throws Exception {
        veriHazirla();
        adetBeklenen("gizlilik", gizlilik, adet);
    }

    @ParameterizedTest(name = "aciliyet {0} -> {1}")
    @CsvSource({"NORMAL,1", "ACELE,1", "GUNLUDUR,2"})
    void aciliyetSuzgeci(String aciliyet, int adet) throws Exception {
        veriHazirla();
        adetBeklenen("aciliyet", aciliyet, adet);
    }

    @ParameterizedTest(name = "yön {0} -> {1}")
    @CsvSource({"GELEN,4", "GIDEN,0"})
    void yonSuzgeci(String yon, int adet) throws Exception {
        veriHazirla();
        adetBeklenen("yon", yon, adet);
    }

    @Test
    void durumSuzgeciVeBirimSuzgeci() throws Exception {
        veriHazirla();
        adetBeklenen("durum", "KAYITLI", 4);
        adetBeklenen("durum", "HAVALEDE", 0);
        long id = kaydet("Havale edilecek", "Kurum", null, "NORMAL", "NORMAL", "null");
        birimeHavaleEt(id, bidb);
        adetBeklenen("durum", "HAVALEDE", 1);
        adetBeklenen("birimId", String.valueOf(bidb.getId()), 1);
        adetBeklenen("birimId", String.valueOf(idari.getId()), 0);
    }

    @Test
    void suzgecler_birlestirilebilir() throws Exception {
        veriHazirla();
        alSorgu("t_denetci", "/api/evraklar", "ara", "devlet", "aciliyet", "ACELE")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].konu").value("Personel alım ilanı"));
    }

    @Test
    void sureYakinSiralamasiEnYakinSureyiUsteAlirSuresizleriSonaAtar() throws Exception {
        veriHazirla();
        String yanit = govde(alSorgu("t_denetci", "/api/evraklar", "sirala", "SURE_YAKIN").andExpect(status().isOk()));
        List<String> konular = JsonPath.read(yanit, "$.content[*].konu");
        // Toplantı daveti 2 iş günü, Arşiv denetim 10 iş günü, Personel ACELE 3 iş günü; süresizler sonda
        assertThat(konular).startsWith("Toplantı daveti", "Personel alım ilanı", "Arşiv denetim raporu");
        assertThat(konular.get(3)).isEqualTo("Bütçe hazırlık genelgesi");
    }

    @Test
    void yeniVeEskiSiralama() throws Exception {
        veriHazirla();
        alSorgu("t_denetci", "/api/evraklar", "sirala", "YENI").andExpect(jsonPath("$.content[0].konu").value("Toplantı daveti"));
        alSorgu("t_denetci", "/api/evraklar", "sirala", "ESKI")
                .andExpect(jsonPath("$.content[0].konu").value("Bütçe hazırlık genelgesi"));
    }

    @Test
    void gecersizSiralamaDegeriReddedilir() throws Exception {
        alSorgu("t_denetci", "/api/evraklar", "sirala", "id; drop table evrak").andExpect(status().isBadRequest());
    }

    @Test
    void sayfalamaSayfaBoyutunaGoreBolur() throws Exception {
        veriHazirla();
        alSorgu("t_denetci", "/api/evraklar", "boyut", "3", "sayfa", "0")
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(4));
        alSorgu("t_denetci", "/api/evraklar", "boyut", "3", "sayfa", "1")
                .andExpect(jsonPath("$.content.length()").value(1));
        alSorgu("t_denetci", "/api/evraklar", "boyut", "3", "sayfa", "5")
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void negatifSayfaVeBoyutDuzeltilir() throws Exception {
        veriHazirla();
        alSorgu("t_denetci", "/api/evraklar", "boyut", "-5", "sayfa", "-1")
                .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void gecikenSuzgeciYalnizcaSuresiGecenAcikEvraklariGetirir() throws Exception {
        veriHazirla();
        saat.ayarla(java.time.LocalDate.of(2026, 3, 5)); // Toplantı daveti (2 iş günü) 4 Mart'ta doldu
        alSorgu("t_denetci", "/api/evraklar", "geciken", "true")
                // acele evrakın süresi 5 Mart'ta dolar (bugün, henüz geçmedi); günlüdür 10 iş günlük olan da geçmedi
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].konu").value("Toplantı daveti"))
                .andExpect(jsonPath("$.content[0].gecikmis").value(true));
    }

    // ----------------------------------------------------------- bilgi edinme

    @Test
    void basvuruAramasiNoAdVeKonuyaBakar() throws Exception {
        gonder("t_bilgi", "/api/bilgi-edinme", basvuruIstegi("Ayşe Yılmaz", null)).andExpect(status().isCreated());
        gonder("t_bilgi", "/api/bilgi-edinme", """
                {"basvuranAd":"Veli Demir","kanal":"E_DEVLET","konu":"Araç kiralama sözleşmeleri"}""").andExpect(status().isCreated());
        for (String[] durum : new String[][] {{"2026-BE-000002", "1"}, {"ayşe", "1"}, {"araç", "1"}, {"ihale", "1"}, {"yok", "0"}}) {
            alSorgu("t_bilgi", "/api/bilgi-edinme", "ara", durum[0])
                    .andExpect(jsonPath("$.totalElements").value(Integer.parseInt(durum[1])));
        }
    }

    @Test
    void basvuruDurumVeBirimSuzgeci() throws Exception {
        long a = basvuruKaydet();
        basvuruKaydet();
        gonder("t_bilgi", "/api/bilgi-edinme/" + a + "/havale", "{\"birimId\":" + bidb.getId() + "}").andExpect(status().isOk());
        alSorgu("t_bilgi", "/api/bilgi-edinme", "durum", "INCELEMEDE").andExpect(jsonPath("$.totalElements").value(1));
        alSorgu("t_bilgi", "/api/bilgi-edinme", "durum", "ALINDI").andExpect(jsonPath("$.totalElements").value(1));
        alSorgu("t_bilgi", "/api/bilgi-edinme", "birimId", String.valueOf(bidb.getId()))
                .andExpect(jsonPath("$.totalElements").value(1));
        alSorgu("t_bilgi", "/api/bilgi-edinme", "durum", "CEVAPLANDI").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void basvuruSureYakinSiralamasiEnAcilOlaniUsteAlir() throws Exception {
        long eski = basvuruKaydet();
        saat.ayarla(java.time.LocalDate.of(2026, 3, 9));
        long yeni = basvuruKaydet();
        String yanit = govde(alSorgu("t_bilgi", "/api/bilgi-edinme", "sirala", "SURE_YAKIN").andExpect(status().isOk()));
        List<Integer> idler = JsonPath.read(yanit, "$.content[*].id");
        assertThat(idler).containsExactly((int) eski, (int) yeni);
        yanit = govde(alSorgu("t_bilgi", "/api/bilgi-edinme", "sirala", "YENI").andExpect(status().isOk()));
        idler = JsonPath.read(yanit, "$.content[*].id");
        assertThat(idler).containsExactly((int) yeni, (int) eski);
    }

    @Test
    void basvuruSayfalama() throws Exception {
        for (int i = 0; i < 3; i++) {
            basvuruKaydet();
        }
        alSorgu("t_bilgi", "/api/bilgi-edinme", "boyut", "2")
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}
