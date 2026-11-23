package dev.evraktakip;

import dev.evraktakip.domain.Birim;
import dev.evraktakip.domain.Kullanici;
import dev.evraktakip.domain.Rol;
import dev.evraktakip.repo.BirimDeposu;
import dev.evraktakip.repo.KullaniciDeposu;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yalnızca "dev" profilinde çalışır: her rol için bir demo kullanıcı oluşturur.
 * Parola koda gömülü değildir; DEMO_SIFRE ortam değişkeninden okunur.
 */
@Component
@Profile("dev")
public class DemoVeriYukleyici implements CommandLineRunner {
    private final KullaniciDeposu kullanicilar;
    private final BirimDeposu birimler;
    private final PasswordEncoder kodlayici;
    private final String demoSifre;

    public DemoVeriYukleyici(KullaniciDeposu kullanicilar, BirimDeposu birimler, PasswordEncoder kodlayici,
                             @Value("${DEMO_SIFRE:}") String demoSifre) {
        this.kullanicilar = kullanicilar;
        this.birimler = birimler;
        this.kodlayici = kodlayici;
        this.demoSifre = demoSifre;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (demoSifre.isBlank()) {
            throw new IllegalStateException("dev profilinde DEMO_SIFRE ortam değişkeni tanımlanmalıdır");
        }
        if (kullanicilar.count() > 0) {
            return;
        }
        Birim bidb = birimler.findAll().stream().filter(b -> b.getKod().equals("BIDB")).findFirst().orElseThrow();
        Birim idari = birimler.findAll().stream().filter(b -> b.getKod().equals("IDARI")).findFirst().orElseThrow();
        String hash = kodlayici.encode(demoSifre);
        kullanicilar.save(new Kullanici("kayit", "Kemal Kayıt", hash, Rol.KAYIT_MEMURU, null));
        kullanicilar.save(new Kullanici("personel", "Ayşe Personel", hash, Rol.BIRIM_PERSONELI, bidb));
        kullanicilar.save(new Kullanici("amir", "Mehmet Amir", hash, Rol.BIRIM_AMIRI, bidb));
        kullanicilar.save(new Kullanici("idari.amir", "Zeynep İdari Amir", hash, Rol.BIRIM_AMIRI, idari));
        kullanicilar.save(new Kullanici("yonetici", "Ali Yönetici", hash, Rol.UST_YONETICI, null));
        kullanicilar.save(new Kullanici("bilgi", "Elif Bilgi Edinme", hash, Rol.BILGI_EDINME_SORUMLUSU, null));
        kullanicilar.save(new Kullanici("arsiv", "Can Arşiv", hash, Rol.ARSIV_SORUMLUSU, null));
        kullanicilar.save(new Kullanici("denetci", "Deniz Denetçi", hash, Rol.DENETCI, null));
        kullanicilar.save(new Kullanici("admin", "Yönetici Admin", hash, Rol.ADMIN, null));
    }
}
