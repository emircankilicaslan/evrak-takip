package dev.evraktakip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uygulama ayarları. Mevzuata bağlı süre değerleri koda gömülü değildir; kurum kendi mevzuat
 * yorumuna göre application.yml üzerinden değiştirir. Varsayılanlar ÖRNEKTİR.
 */
@ConfigurationProperties("evrak")
public record EvrakProperties(Kimlik kimlik, Jwt jwt, Kurallar kurallar, Guvenlik guvenlik) {

    /** Art arda hatalı girişte kilit ve parola politikası. */
    public record Guvenlik(int kilitEsigi, int kilitDakika, int sifreMinUzunluk) {}

    /** mod: "yerel" (kullanıcı adı + parola) veya "ldap" (Active Directory ile kimlik doğrulama). */
    public record Kimlik(String mod, String adDomain, String ldapUrl) {}

    public record Jwt(String gizliAnahtar, long gecerlilikDakika) {}

    public record Kurallar(
            int aceleCevapIsGunu,
            int bilgiEdinmeCevapIsGunu,
            int bilgiEdinmeUzatmaIsGunu,
            int bilgiEdinmeYonlendirmeGun,
            int bilgiEdinmeItirazGun) {}
}
