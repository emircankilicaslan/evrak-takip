package dev.evraktakip.web;

import dev.evraktakip.domain.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** İstek ve yanıt modelleri. */
public final class Dto {
    private Dto() {}

    public record GirisIstegi(@NotBlank String kullaniciAdi, @NotBlank String sifre) {}

    public record GirisYaniti(String token, String kullaniciAdi, String adSoyad, Rol rol, Long birimId) {}

    public record SifreDegistirIstegi(@NotBlank String mevcutSifre, @NotBlank @Size(max = 200) String yeniSifre) {}

    public record KullaniciOlusturIstegi(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,100}",
                    message = "3-100 karakter; harf, rakam, nokta, alt çizgi ve tire kullanılabilir") String kullaniciAdi,
            @NotBlank @Size(max = 200) String adSoyad,
            @Size(max = 200) String sifre,
            @NotNull Rol rol,
            Long birimId) {}

    public record KullaniciGuncelleIstegi(@NotNull Rol rol, Long birimId, @NotNull KullaniciDurumu durum) {}

    public record KullaniciYaniti(
            Long id, String kullaniciAdi, String adSoyad, Rol rol, String birim, KullaniciDurumu durum, boolean kilitli) {}

    // ------------------------------------------------------------------ evrak

    public record GelenEvrakIstegi(
            @NotBlank @Size(max = 300) String konu,
            @NotBlank @Size(max = 200) String ilgiliKurum,
            @NotNull LocalDate evrakTarihi,
            @Size(max = 60) String disSayi,
            @NotNull Gizlilik gizlilik,
            @NotNull Aciliyet aciliyet,
            @Min(1) @Max(60) Integer cevapGunu) {}

    public record GidenEvrakIstegi(
            @NotBlank @Size(max = 300) String konu,
            @NotBlank @Size(max = 200) String ilgiliKurum,
            @NotNull Gizlilik gizlilik,
            Long ilgiliEvrakId) {}

    public record GerekceIstegi(@NotBlank @Size(max = 1000) String gerekce) {}

    /** hedefBirimId ile hedefKullaniciId'den tam biri verilir. */
    public record SevkIstegi(Long hedefBirimId, Long hedefKullaniciId, @Size(max = 500) String talimat) {}

    public record TamamlaIstegi(Long cevapEvrakId, @Size(max = 1000) String notu) {}

    public record ArsivIstegi(@NotBlank @Size(max = 20) String dosyaKodu) {}

    public record TatilIstegi(@NotNull LocalDate tarih, @NotBlank @Size(max = 100) String ad) {}

    public record EvrakYaniti(
            Long id,
            String kayitNo,
            EvrakYonu yon,
            String konu,
            String ilgiliKurum,
            LocalDate evrakTarihi,
            String disSayi,
            Gizlilik gizlilik,
            Aciliyet aciliyet,
            EvrakDurumu durum,
            Long birimId,
            String birim,
            String olusturan,
            String imzalayan,
            LocalDateTime imzaZamani,
            String redGerekcesi,
            LocalDate cevapSuresiSonu,
            Integer kalanIsGunu,
            boolean gecikmis,
            Long ilgiliEvrakId,
            Long cevapEvrakId,
            String tamamlamaNotu,
            String dosyaKodu,
            LocalDate arsivTarihi,
            LocalDate imhaTarihi,
            LocalDateTime kayitZamani) {}

    public record SevkYaniti(
            Long id,
            String sevkEden,
            String hedefBirim,
            String hedefKullanici,
            String talimat,
            SevkDurumu durum,
            LocalDateTime sevkZamani,
            LocalDateTime alinmaZamani) {}

    public record EvrakDetayYaniti(EvrakYaniti evrak, java.util.List<SevkYaniti> sevkler) {}

    public record DosyaPlaniYaniti(String kod, String ad, Integer saklamaYili) {}

    // ----------------------------------------------------------- bilgi edinme

    public record BasvuruIstegi(
            @NotBlank @Size(max = 200) String basvuranAd,
            @Pattern(regexp = "\d{11}", message = "T.C. kimlik numarası 11 rakam olmalıdır") String tcKimlikNo,
            @Size(max = 300) String iletisim,
            @NotNull BasvuruKanali kanal,
            @NotBlank @Size(max = 1000) String konu,
            LocalDate basvuruTarihi) {}

    public record HavaleIstegi(@NotNull Long birimId) {}

    public record MetinIstegi(@NotBlank @Size(max = 1000) String metin) {}

    public record CevapIstegi(@NotBlank @Size(max = 2000) String metin) {}

    public record RetIstegi(@NotNull RetNedeni nedeni, @NotBlank @Size(max = 2000) String gerekce) {}

    public record YonlendirIstegi(@NotBlank @Size(max = 200) String kurum) {}

    public record BasvuruYaniti(
            Long id,
            String basvuruNo,
            String basvuranAd,
            String tcKimlikMaskeli,
            String iletisim,
            BasvuruKanali kanal,
            String konu,
            LocalDate basvuruTarihi,
            BasvuruDurumu durum,
            Long birimId,
            String birim,
            String birimGorusu,
            LocalDate sureSonu,
            Integer kalanIsGunu,
            boolean gecikmis,
            int uzatmaSayisi,
            String uzatmaGerekcesi,
            String sonucMetni,
            RetNedeni retNedeni,
            LocalDate sonucTarihi,
            boolean gecikmeliSonuc,
            LocalDate itirazSonTarihi,
            String yonlendirilenKurum,
            LocalDateTime kayitZamani) {}

    // ------------------------------------------------------------------- ortak

    public record OzetYaniti(
            java.util.Map<EvrakDurumu, Long> evrakDurumlari,
            long gecikenEvrak,
            long imzaBekleyen,
            long bilgiEdinmeAcik,
            long bilgiEdinmeGeciken,
            long bilgiEdinmeYaklasan) {}

    public record DenetimYaniti(
            Long id, LocalDateTime zaman, String kullaniciAdi, String islem,
            String varlikTuru, Long varlikId, String ayrinti) {

        public static DenetimYaniti of(DenetimKaydi k) {
            return new DenetimYaniti(k.getId(), k.getZaman(), k.getKullaniciAdi(), k.getIslem(),
                    k.getVarlikTuru(), k.getVarlikId(), k.getAyrinti());
        }
    }
}
