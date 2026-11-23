package dev.evraktakip.servis;

import java.util.Locale;

/** Kullanıcı girdisinden güvenli LIKE kalıbı üretir. */
final class AramaYardimcisi {
    static final char KACIS = '\\';

    private AramaYardimcisi() {}

    /**
     * Boş girdi için null döner. LIKE joker karakterleri (% ve _) ile kaçış karakteri düz metin sayılır;
     * uzunluk sınırlandırılır.
     */
    static String kalip(String ara, int enCok) {
        if (ara == null || ara.isBlank()) {
            return null;
        }
        String metin = ara.strip();
        if (metin.length() > enCok) {
            metin = metin.substring(0, enCok);
        }
        return "%" + metin.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
