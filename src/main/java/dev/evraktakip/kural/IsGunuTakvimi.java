package dev.evraktakip.kural;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/** Hafta sonu ve resmi tatil bilgisine göre iş günü hesabı. Saf sınıf: veritabanı bilmez. */
public class IsGunuTakvimi {
    private final Set<LocalDate> tatiller;

    public IsGunuTakvimi(Collection<LocalDate> tatiller) {
        this.tatiller = Set.copyOf(tatiller);
    }

    public boolean isGunu(LocalDate gun) {
        DayOfWeek hafta = gun.getDayOfWeek();
        return hafta != DayOfWeek.SATURDAY && hafta != DayOfWeek.SUNDAY && !tatiller.contains(gun);
    }

    /** Verilen gün iş günüyse kendisi, değilse sonraki ilk iş günü. */
    public LocalDate ilkIsGunu(LocalDate gun) {
        LocalDate aday = gun;
        while (!isGunu(aday)) {
            aday = aday.plusDays(1);
        }
        return aday;
    }

    /** Takvim günü cinsinden süre eklenir; bitiş iş günü değilse ilk iş gününe uzar. */
    public LocalDate sureSonu(LocalDate baslangic, int takvimGunu) {
        return ilkIsGunu(baslangic.plusDays(takvimGunu));
    }

    /** Başlangıçtan sonraki n. iş günü (başlangıç günü sayılmaz). */
    public LocalDate isGunuEkle(LocalDate baslangic, int isGunu) {
        if (isGunu < 0) {
            throw new IllegalArgumentException("iş günü sayısı negatif olamaz");
        }
        LocalDate gun = baslangic;
        int kalan = isGunu;
        while (kalan > 0) {
            gun = gun.plusDays(1);
            if (isGunu(gun)) {
                kalan--;
            }
        }
        return gun;
    }

    /**
     * (baslangic, bitis] aralığındaki iş günü sayısı; bitiş başlangıçtan önceyse negatif.
     * Kalan süreyi göstermek için: pozitif = kalan iş günü, negatif = geçen iş günü.
     */
    public int isGunuFarki(LocalDate baslangic, LocalDate bitis) {
        if (bitis.isBefore(baslangic)) {
            return -isGunuFarki(bitis, baslangic);
        }
        int sayi = 0;
        for (LocalDate g = baslangic.plusDays(1); !g.isAfter(bitis); g = g.plusDays(1)) {
            if (isGunu(g)) {
                sayi++;
            }
        }
        return sayi;
    }
}
