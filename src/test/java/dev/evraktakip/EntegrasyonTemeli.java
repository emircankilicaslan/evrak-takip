package dev.evraktakip;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.evraktakip.config.ZamanConfig;
import dev.evraktakip.domain.Birim;
import dev.evraktakip.domain.Kullanici;
import dev.evraktakip.domain.Rol;
import dev.evraktakip.repo.BirimDeposu;
import dev.evraktakip.repo.KullaniciDeposu;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entegrasyon testlerinin ortak tabanı: gerçek Oracle veritabanı, gerçek güvenlik zinciri.
 * Her test geri alınan bir işlemde çalışır; zaman {@link AyarlanabilirClock} ile ilerletilir.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(EntegrasyonTemeli.SaatAyari.class)
abstract class EntegrasyonTemeli {
    protected static final String SIFRE = "Test-Sifre-1";
    protected static final LocalDate BASLANGIC = LocalDate.of(2026, 3, 2); // Pazartesi

    @TestConfiguration
    static class SaatAyari {
        @Bean
        @Primary
        AyarlanabilirClock testSaati() {
            return new AyarlanabilirClock(ZamanConfig.TR, BASLANGIC);
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected AyarlanabilirClock saat;
    @Autowired protected KullaniciDeposu kullanicilar;
    @Autowired protected BirimDeposu birimler;
    @Autowired protected PasswordEncoder kodlayici;
    @Autowired protected JdbcTemplate jdbc;

    protected Birim bidb;
    protected Birim idari;

    @BeforeEach
    void hazirla() {
        saat.ayarla(BASLANGIC);
        bidb = birimler.findAll().stream().filter(b -> b.getKod().equals("BIDB")).findFirst().orElseThrow();
        idari = birimler.findAll().stream().filter(b -> b.getKod().equals("IDARI")).findFirst().orElseThrow();
        kullan("t_kayit", Rol.KAYIT_MEMURU, null);
        kullan("t_personel", Rol.BIRIM_PERSONELI, bidb);
        kullan("t_personel2", Rol.BIRIM_PERSONELI, bidb);
        kullan("t_baska_personel", Rol.BIRIM_PERSONELI, idari);
        kullan("t_amir", Rol.BIRIM_AMIRI, bidb);
        kullan("t_baska_amir", Rol.BIRIM_AMIRI, idari);
        kullan("t_yonetici", Rol.UST_YONETICI, null);
        kullan("t_bilgi", Rol.BILGI_EDINME_SORUMLUSU, null);
        kullan("t_arsiv", Rol.ARSIV_SORUMLUSU, null);
        kullan("t_denetci", Rol.DENETCI, null);
        kullan("t_admin", Rol.ADMIN, null);
    }

    protected void kullan(String ad, Rol rol, Birim birim) {
        kullanicilar.save(new Kullanici(ad, ad, kodlayici.encode(SIFRE), rol, birim));
    }

    // ------------------------------------------------------------ istek yardımcıları

    protected ResultActions girisYap(String kullaniciAdi, String sifre) throws Exception {
        return mvc.perform(post("/api/auth/giris").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kullaniciAdi\":\"%s\",\"sifre\":\"%s\"}".formatted(kullaniciAdi, sifre)));
    }

    protected String giris(String kullaniciAdi) throws Exception {
        String yanit = girisYap(kullaniciAdi, SIFRE)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(yanit, "$.token");
    }

    /** Token her çağrıda yeniden alınır: saat ileri alındığında eski token süresi dolmuş sayılır. */
    protected ResultActions gonder(String kullanici, String yol, String govde) throws Exception {
        var istek = post(yol).header("Authorization", "Bearer " + giris(kullanici));
        if (govde != null) {
            istek.contentType(MediaType.APPLICATION_JSON).content(govde);
        }
        return mvc.perform(istek);
    }

    protected ResultActions guncelle(String kullanici, String yol, String govde) throws Exception {
        return mvc.perform(put(yol).header("Authorization", "Bearer " + giris(kullanici))
                .contentType(MediaType.APPLICATION_JSON).content(govde));
    }

    protected ResultActions al(String kullanici, String yol) throws Exception {
        return mvc.perform(get(yol).header("Authorization", "Bearer " + giris(kullanici)));
    }

    /**
     * Sorgu parametrelerini ham değerle gönderir (URL'e elle kodlanmış metin yazılmaz; MockMvc kodlamayı kendisi yapar).
     * Parametreler ad, değer, ad, değer biçiminde verilir.
     */
    protected ResultActions alSorgu(String kullanici, String yol, String... adDeger) throws Exception {
        var istek = get(yol).header("Authorization", "Bearer " + giris(kullanici));
        for (int i = 0; i + 1 < adDeger.length; i += 2) {
            istek.param(adDeger[i], adDeger[i + 1]);
        }
        return mvc.perform(istek);
    }

    protected String govde(ResultActions sonuc) throws Exception {
        return sonuc.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ iş akışı yardımcıları

    protected long kimlik(String kullaniciAdi) {
        return kullanicilar.findByKullaniciAdi(kullaniciAdi).orElseThrow().getId();
    }

    private long idOku(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(govde(r), "$.id")).longValue();
    }

    /** Kayıt memuru olarak gelen evrak kaydeder. */
    protected long gelenKaydet(String gizlilik, String aciliyet, String cevapGunu) throws Exception {
        String istek = """
                {"konu":"Bilgi talebi","ilgiliKurum":"Başka Kurum","evrakTarihi":"2026-03-02","gizlilik":"%s","aciliyet":"%s","cevapGunu":%s}"""
                .formatted(gizlilik, aciliyet, cevapGunu);
        return idOku(gonder("t_kayit", "/api/evraklar/gelen", istek).andExpect(status().isCreated()));
    }

    protected long gelenKaydet() throws Exception {
        return gelenKaydet("NORMAL", "NORMAL", "null");
    }

    protected long birimeHavaleEt(long evrakId, Birim birim) throws Exception {
        String yanit = govde(gonder("t_kayit", "/api/evraklar/" + evrakId + "/sevk",
                "{\"hedefBirimId\":" + birim.getId() + "}").andExpect(status().isOk()));
        return ((Number) JsonPath.read(yanit, "$.id")).longValue();
    }

    /** Evrakı bidb birimine havale eder ve birim amiri teslim alır. */
    protected void amirTeslimAlmisHavale(long evrakId) throws Exception {
        long sevk = birimeHavaleEt(evrakId, bidb);
        gonder("t_amir", "/api/evraklar/sevkler/" + sevk + "/teslim-al", null).andExpect(status().isOk());
    }

    protected String basvuruIstegi(String ad, String tc) {
        return """
                {"basvuranAd":"%s","tcKimlikNo":%s,"kanal":"DILEKCE","konu":"2025 yılı ihale listesi"}"""
                .formatted(ad, tc == null ? "null" : "\"" + tc + "\"");
    }

    protected long basvuruKaydet() throws Exception {
        return idOku(gonder("t_bilgi", "/api/bilgi-edinme", basvuruIstegi("Ayşe Yılmaz", null)).andExpect(status().isCreated()));
    }
}
