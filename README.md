# Evrak Takip

Kurumlar için **elektronik evrak yönetimi (EBYS) ve 4982 sayılı Bilgi Edinme Hakkı Kanunu başvuru takibi**: REST API ve web arayüzü.

**Java 21 · Spring Boot 3 · Oracle (Flyway) · JWT + Active Directory/LDAP · JUnit 5 · Docker · GitHub Actions**

![Genel bakış](docs/screenshots/genel-bakis.jpg)

<table>
  <tr>
    <td><img src="docs/screenshots/evrak-havale-zinciri.jpg" alt="Evrak ayrıntısı ve havale zinciri"><br><sub>Evrak ayrıntısı, süreç adımları ve havale zinciri</sub></td>
    <td><img src="docs/screenshots/gelen-kutusu.jpg" alt="Gelen kutusu"><br><sub>Birim amirinin gelen kutusu</sub></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/giden-imza.jpg" alt="İmza bekleyen giden evrak"><br><sub>İmza bekleyen giden evrak</sub></td>
    <td><img src="docs/screenshots/bilgi-edinme-detay.jpg" alt="Bilgi edinme başvurusu"><br><sub>Bilgi edinme başvurusu: süre, uzatma, birim görüşü</sub></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/evrak-gizli.jpg" alt="Gizli evrak"><br><sub>GİZLİ evrak (yalnızca işlem zincirine görünür)</sub></td>
    <td><img src="docs/screenshots/koyu-tema.jpg" alt="Koyu tema"><br><sub>Koyu tema</sub></td>
  </tr>
</table>

<details>
<summary>Diğer ekranlar</summary>

![Evrak panosu](docs/screenshots/evrak-pano.jpg)
![Evrak listesi](docs/screenshots/evrak-liste.jpg)
![Bilgi edinme listesi](docs/screenshots/bilgi-edinme-liste.jpg)
![Gelen evrak formu](docs/screenshots/gelen-evrak-formu.jpg)
![Arşivlenmiş evrak](docs/screenshots/arsiv-evrak.jpg)
![Denetim kaydı](docs/screenshots/denetim-kaydi.jpg)
![Kullanıcı yönetimi](docs/screenshots/kullanicilar.jpg)
![Mobil](docs/screenshots/mobil.jpg)
![Swagger](docs/screenshots/swagger.jpg)
![Giriş](docs/screenshots/giris.jpg)

</details>

Ekran görüntüleri elle yazılmış değildir: `Ekran görüntüleri` iş akışı uygulamayı Oracle ile ayağa kaldırır, örnek veriyi gerçek API ve iş kurallarından geçirerek yükler ve tarayıcıdan görüntü alır.

## Neler yapar?

### Evrak yönetimi
- **Gelen evrak**: kayıt memuru kaydeder, yıl bazlı kesintisiz kayıt numarası verilir (`2026-GEL-000001`), birime veya doğrudan kişiye **havale** edilir. Havale zinciri (kim, kime, talimat, teslim alma zamanı) saklanır.
- **Giden evrak**: taslak → imzaya gönder → birim amiri imzalar. Resmi sayı (`2026-GID-000001`) **imza anında** verilir. **Görevler ayrılığı**: evrakı hazırlayan kişi imzalayamaz; ret gerekçeli olur.
- **Cevap süresi**: `ACELE` ve `GÜNLÜDÜR` evrakta süre **iş günü ve resmi tatil** takvimine göre hesaplanır, geciken evrak özette ve listede işaretlenir. Bu evraklar cevap evrakı bağlanmadan ya da gerekçe yazılmadan tamamlanamaz.
- **Gizlilik**: `GİZLİ` evrak yalnızca işlem zincirindeki kişilere ve üst yöneticiye görünür; yetkisiz kullanıcıya evrak **404** döner (varlığı bile sızmaz). GİZLİ evrak birime değil kişiye havale edilir. Gizli/hizmete özel evrakın her görüntülenmesi denetim kaydına işlenir.
- **Arşiv**: dosya planı koduna göre saklama süresi ve **imha tarihi** hesaplanır; süresi dolanlar imha aday listesinde çıkar.

### Bilgi edinme (4982)
- Başvuru kaydı (dilekçe, e-Devlet, e-posta), ilgili birime havale, birim görüşü.
- **15 iş günü** cevap süresi, gerekçeli ve **tek seferlik** uzatma (ek süre mevcut süre sonuna eklenir), yetkisiz kuruma **yönlendirme** süresi, cevap/ret sonrası **itiraz son tarihi**.
- Ret için ret nedeni + gerekçe zorunlu.
- **KVKK**: T.C. kimlik numarası sağlama basamaklarıyla doğrulanır ve yalnızca **maskeli** saklanır; listelerde ve dışa aktarmada başvuran adı maskelidir; ayrıntı ekranının her açılışı denetim kaydına yazılır.

### Panel
- Rol bazlı **genel bakış**: bekleyen/geciken sayıları, son altı ayın gelen-giden grafiği, birimlerdeki açık evrak yükü.
- Birim personeli ve amiri için **gelen kutusu** (bana ya da birimime havale edilmiş, süresi en yakın olan üstte).
- Durumuna göre evrakları gösteren **pano (kanban) görünümü**; acele/geciken ve GİZLİ kartlar renkle ayrılır.
- Evrak ayrıntısında **süreç adımları** (kayıt → havale → tamamlandı → arşiv) ve zaman çizelgesi biçiminde **olay akışı**.
- Koyu yan menülü yerleşim, açık/koyu tema, mobil uyumlu.

### Ortak
- 8 rol (kayıt memuru, birim personeli, birim amiri, üst yönetici, bilgi edinme sorumlusu, arşiv sorumlusu, denetçi, yönetici) ve birim bazlı yetki.
- **Değiştirilemez denetim kaydı**: Oracle tetikleyicisi güncelleme ve silmeyi engeller; konu metni gibi içerik denetim kaydına yazılmaz.
- Kullanıcı yönetimi, art arda hatalı girişte geçici hesap kilidi, parola politikası, Active Directory/LDAP kimlik doğrulama kipi.
- Excel/CSV dışa aktarma (yetki kapsamı listedekiyle aynı), Swagger arayüzü (`/swagger`), düz HTML/JS arayüz (sıkı CSP).

## Mimari

```
web (controller, DTO, hata eşleme)  →  servis (iş kuralları, görünürlük)  →  repo (Spring Data JPA)  →  Oracle
                                          ↳ kural (saf sınıflar: iş günü takvimi, TC kimlik, bilgi edinme süreleri)
guvenlik: JWT filtresi · AD/LDAP veya yerel kimlik · @PreAuthorize
```

- Süre ve iş günü hesapları veritabanı bilmeyen **saf sınıflardadır** ve birim testlidir.
- Görünürlük kuralı tek yerde (`EvrakServisi.gorunur`) JPA `Specification` olarak yazılır; liste, sayı, dışa aktarma ve ayrıntı hep aynı kuralı kullanır.
- Kayıt numaraları, satır kilidiyle (`PESSIMISTIC_WRITE`) artırılan sayaç tablosundan gelir; işlem geri alınırsa numara da geri döner.
- Şema değişiklikleri yalnızca Flyway göçleriyle yapılır; Hibernate `validate` modundadır.

## Çalıştırma

Gerekenler: Docker.

```powershell
$env:ORACLE_SYS_SIFRE = "<oracle-sys-parolasi>"
$env:DB_PASSWORD      = "<uygulama-db-parolasi>"
$env:JWT_SECRET       = "<en-az-32-karakterlik-rastgele-deger>"
$env:DEMO_SIFRE       = "<demo-kullanicilar-icin-parola>"
docker compose up --build
```

Arayüz: <http://localhost:8080> · API belgesi: <http://localhost:8080/swagger>

`dev` profili her rol için bir demo kullanıcı oluşturur (`kayit`, `personel`, `amir`, `idari.amir`, `yonetici`, `bilgi`, `arsiv`, `denetci`, `admin`); parola `DEMO_SIFRE` değeridir. Üretimde `dev` profili kullanılmaz.

Kurumsal kimlik için `KIMLIK_MODU=ldap`, `AD_DOMAIN`, `LDAP_URL` verilir; kimlik AD'de doğrulanır, rol ve birim uygulama tablosundan gelir.

## Test

```powershell
mvn verify
```

Entegrasyon testleri **gerçek Oracle** (Testcontainers yerine CI'daki `gvenzl/oracle-free` servisi veya yerel `DB_URL`) ve gerçek güvenlik zinciriyle çalışır; zaman, ayarlanabilir saatle ilerletilerek süre/gecikme senaryoları sınanır. CI her itmede derler, test eder, Docker imajını Oracle'a karşı açıp duman testi yapar.

## Yapılandırma ve mevzuat uyarısı

Cevap, uzatma, yönlendirme ve itiraz süreleri `application.yml` içindeki `evrak.kurallar` altında **örnek değerlerdir**; kurum kendi mevzuat yorumuna göre ayarlamalıdır (koda gömülü kural yoktur). Dosya planı kodları ve saklama süreleri de örnektir; kurum kendi standart dosya planını yükler. Bu proje bir portfolyo/örnek çalışmadır, resmî bir EBYS değildir ve e-imza/KEP entegrasyonu içermez.

## Lisans

MIT
