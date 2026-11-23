package dev.evraktakip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** Excel/CSV dışa aktarma: içerik, yetki kapsamı ve denetim izi. */
class DisaAktarimTest extends EntegrasyonTemeli {

    private byte[] indir(String kullanici, String yol) throws Exception {
        return al(kullanici, yol).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void evrakListesiCsvOlarakIndirilir() throws Exception {
        gelenKaydet();
        var yanit = al("t_kayit", "/api/disa-aktar/evraklar?bicim=csv")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("evraklar-2026-03-02.csv")))
                .andReturn().getResponse();
        String icerik = new String(yanit.getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(icerik).startsWith("﻿Kayıt no;Yön;Konu;İlgili kurum;Evrak tarihi;Gizlilik");
        assertThat(icerik).contains("2026-GEL-000001", "Gelen", "Bilgi talebi", "02.03.2026", "Kayıtlı");
    }

    @Test
    void evrakListesiExcelOlarakIndirilirVeAcilabilir() throws Exception {
        gelenKaydet();
        byte[] bayt = indir("t_kayit", "/api/disa-aktar/evraklar?bicim=xlsx");
        try (var defter = new XSSFWorkbook(new ByteArrayInputStream(bayt))) {
            var sayfa = defter.getSheet("Evraklar");
            assertThat(sayfa).isNotNull();
            assertThat(sayfa.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Kayıt no");
            assertThat(sayfa.getRow(1).getCell(0).getStringCellValue()).isEqualTo("2026-GEL-000001");
        }
    }

    @Test
    void bilinmeyenBicimReddedilir() throws Exception {
        al("t_kayit", "/api/disa-aktar/evraklar?bicim=pdf").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void gizliEvrakYetkisizinDisaAktarimindaGorunmez() throws Exception {
        gelenKaydet("GIZLI", "NORMAL", "null");
        String csv = new String(indir("t_denetci", "/api/disa-aktar/evraklar?bicim=csv"), StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("Bilgi talebi");
    }

    @Test
    void bilgiEdinmeDisaAktarimindaBasvuranAdiMaskelidir() throws Exception {
        basvuruKaydet();
        String csv = new String(indir("t_bilgi", "/api/disa-aktar/bilgi-edinme?bicim=csv"), StandardCharsets.UTF_8);
        assertThat(csv).contains("2026-BE-000001", "A*** Y***");
        assertThat(csv).doesNotContain("Ayşe", "Yılmaz");
    }

    @Test
    void denetimKaydiYalnizcaYetkiliRollerDisaAktarir() throws Exception {
        for (String yetkisiz : new String[] {"t_personel", "t_amir", "t_kayit", "t_arsiv", "t_bilgi"}) {
            al(yetkisiz, "/api/disa-aktar/denetim?bicim=csv").andExpect(status().isForbidden());
        }
        for (String yetkili : new String[] {"t_denetci", "t_admin", "t_yonetici"}) {
            al(yetkili, "/api/disa-aktar/denetim?bicim=csv").andExpect(status().isOk());
        }
    }

    @Test
    void denetimKaydiIcerigiDisaAktarilir() throws Exception {
        gelenKaydet();
        String csv = new String(indir("t_denetci", "/api/disa-aktar/denetim?bicim=csv"), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Zaman;Kullanıcı;İşlem;Kayıt türü;Kayıt no;Ayrıntı");
        assertThat(csv).contains("GELEN_KAYIT", "t_kayit", "EVRAK");
        assertThat(csv).doesNotContain("Bilgi talebi"); // konu metni denetim kaydına yazılmaz
    }

    @Test
    void disaAktarmaDenetimKaydinaYazilir() throws Exception {
        indir("t_kayit", "/api/disa-aktar/evraklar?bicim=csv");
        String denetim = govde(al("t_denetci", "/api/denetim?size=100").andExpect(status().isOk()));
        assertThat(denetim).contains("DISA_AKTAR", "csv, 0 kayıt");
    }

    @Test
    void oturumsuzDisaAktarmaYapilamaz() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/disa-aktar/evraklar"))
                .andExpect(status().isUnauthorized());
    }
}
