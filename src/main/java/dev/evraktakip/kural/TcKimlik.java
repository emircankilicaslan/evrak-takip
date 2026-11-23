package dev.evraktakip.kural;

/** T.C. kimlik numarası doğrulama (sağlama basamakları) ve maskeleme. */
public final class TcKimlik {
    private TcKimlik() {}

    /**
     * 11 hane, ilk hane 0 değil; 10. hane = ((tekler toplamı x 7) - çiftler toplamı) mod 10,
     * 11. hane = ilk 10 hanenin toplamı mod 10.
     */
    public static boolean gecerliMi(String no) {
        if (no == null || !no.matches("[1-9][0-9]{10}")) {
            return false;
        }
        int[] h = new int[11];
        for (int i = 0; i < 11; i++) {
            h[i] = no.charAt(i) - '0';
        }
        int tekler = h[0] + h[2] + h[4] + h[6] + h[8];
        int ciftler = h[1] + h[3] + h[5] + h[7];
        int onuncu = ((tekler * 7) - ciftler) % 10;
        if (onuncu < 0) {
            onuncu += 10;
        }
        int toplam = 0;
        for (int i = 0; i < 10; i++) {
            toplam += h[i];
        }
        return h[9] == onuncu && h[10] == toplam % 10;
    }

    /** İlk üç ve son iki hane görünür: 123******45. */
    public static String maskele(String no) {
        return no.substring(0, 3) + "******" + no.substring(9);
    }
}
