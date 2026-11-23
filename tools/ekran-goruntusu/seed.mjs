// Çalışan uygulamaya, API üzerinden, kurgusal ama gerçekçi evrak ve başvuru verisi yükler.
// Hiçbir kural atlanmaz: her adım, gerçek kullanıcıların yaptığı gibi sunucu kurallarından geçer.
import { writeFileSync } from 'node:fs';

const BASE = process.env.BASE || 'http://localhost:8080';
const SIFRE = process.env.DEMO_SIFRE;
if (!SIFRE) throw new Error('DEMO_SIFRE tanımlı olmalı');

const bugun = new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Istanbul' });

async function api(yontem, yol, token, govde) {
  const yanit = await fetch(BASE + yol, {
    method: yontem,
    headers: {
      Accept: 'application/json',
      ...(govde !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: govde !== undefined ? JSON.stringify(govde) : undefined,
  });
  const metin = await yanit.text();
  const veri = metin ? JSON.parse(metin) : null;
  if (!yanit.ok) throw new Error(`${yontem} ${yol} -> ${yanit.status}: ${veri && veri.detail}`);
  return veri;
}
const giris = async (k) => (await api('POST', '/api/auth/giris', null, { kullaniciAdi: k, sifre: SIFRE })).token;

const t = {};
for (const k of ['kayit', 'personel', 'amir', 'idari.amir', 'yonetici', 'bilgi', 'arsiv']) t[k] = await giris(k);

const birimler = await api('GET', '/api/birimler', t.kayit);
const birim = (kod) => birimler.find((b) => b.kod === kod).id;
const personelBidb = await api('GET', `/api/birimler/${birim('BIDB')}/personel`, t.kayit);
const kisi = (kullaniciAdi) => personelBidb.find((p) => p.kullaniciAdi === kullaniciAdi).id;

const kimlikler = {};

async function gelen(anahtar, konu, kurum, gizlilik, aciliyet, cevapGunu, disSayi) {
  const e = await api('POST', '/api/evraklar/gelen', t.kayit,
    { konu, ilgiliKurum: kurum, evrakTarihi: bugun, gizlilik, aciliyet, cevapGunu: cevapGunu ?? null, disSayi: disSayi ?? null });
  kimlikler[anahtar] = e.id;
  return e.id;
}
const birimeSevk = (id, kod, talimat) =>
  api('POST', `/api/evraklar/${id}/sevk`, t.kayit, { hedefBirimId: birim(kod), talimat });
const teslimAl = (sevk, kim) => api('POST', `/api/evraklar/sevkler/${sevk.id}/teslim-al`, t[kim]);

// 1) Günlüdür evrak: birime havale → amir teslim aldı → personele havale → personel teslim aldı
const genelge = await gelen('genelge', 'Kamu Hizmet Standartları Genelgesi Uygulama Raporu', 'Cumhurbaşkanlığı İdari İşler Başkanlığı',
  'NORMAL', 'GUNLUDUR', 5, '2026/184');
const s1 = await birimeSevk(genelge, 'BIDB', 'Birim görüşünü hazırlayınız.');
await teslimAl(s1, 'amir');
const s1b = await api('POST', `/api/evraklar/${genelge}/sevk`, t.amir,
  { hedefKullaniciId: kisi('personel'), talimat: 'Rapor taslağını hazırlayıp cevap yazısını oluşturunuz.' });
await teslimAl(s1b, 'personel');

// 2) Acele evrak: amirin gelen kutusunda bekliyor
const siber = await gelen('siber', 'Siber Güvenlik Tedbirleri Hakkında Acil Bildirim', 'Dijital Dönüşüm Ofisi', 'HIZMETE_OZEL', 'ACELE', null, '2026/57');
await birimeSevk(siber, 'BIDB', 'Acil: tedbir listesini bugün kontrol ediniz.');

// 3) GİZLİ evrak: yalnızca işlem zincirindeki kişi görür
const gizli = await gelen('gizli', 'Personel İnceleme Dosyası (Kişiye Özel)', 'İçişleri Bakanlığı Teftiş Kurulu', 'GIZLI', 'NORMAL', null, '2026/GZL-12');
await api('POST', `/api/evraklar/${gizli}/sevk`, t.kayit, { hedefKullaniciId: kisi('amir'), talimat: 'Yalnızca şahsınıza.' });

// 4) Henüz havale edilmemiş evrak
await gelen('kayitli', 'Belediyeler Birliği Dönem Toplantısı Daveti', 'Türkiye Belediyeler Birliği', 'NORMAL', 'NORMAL', null, '2026/911');

// 5) Tamamlanmış ve arşivlenmiş evrak (İdari İşler)
const rapor = await gelen('arsivli', 'Yıllık Faaliyet Raporu Talebi', 'Sayıştay Başkanlığı', 'NORMAL', 'NORMAL', null, '2026/31');
const s5 = await birimeSevk(rapor, 'IDARI', 'Rapor ekte gönderilmiştir.');
await teslimAl(s5, 'idari.amir');
await api('POST', `/api/evraklar/${rapor}/tamamla`, t['idari.amir'], { notu: 'Faaliyet raporu kurum internet sitesinde yayımlanmıştır; bilgi verilmiştir.' });
await api('POST', `/api/evraklar/${rapor}/arsivle`, t.arsiv, { dosyaKodu: '120' });

// 6) Giden evrak: genelgeye cevap (imza bekliyor) ve imzalanmış giden evrak
const cevap = await api('POST', '/api/evraklar/giden', t.personel,
  { konu: 'Kamu Hizmet Standartları Genelgesi Uygulama Raporu Hk.', ilgiliKurum: 'Cumhurbaşkanlığı İdari İşler Başkanlığı', gizlilik: 'NORMAL', ilgiliEvrakId: genelge });
await api('POST', `/api/evraklar/${cevap.id}/imzaya-gonder`, t.personel);
kimlikler.imzaBekleyen = cevap.id;

const bilgi = await api('POST', '/api/evraklar/giden', t.personel,
  { konu: 'Bilgi Sistemleri Envanter Çalışması Hk.', ilgiliKurum: 'Dijital Dönüşüm Ofisi', gizlilik: 'NORMAL' });
await api('POST', `/api/evraklar/${bilgi.id}/imzaya-gonder`, t.personel);
await api('POST', `/api/evraklar/${bilgi.id}/imzala`, t.amir);
kimlikler.imzali = bilgi.id;

// 7) Bilgi edinme başvuruları
const basvuru = (ad, tc, kanal, konu) =>
  api('POST', '/api/bilgi-edinme', t.bilgi, { basvuranAd: ad, tcKimlikNo: tc, iletisim: null, kanal, konu });

const b1 = await basvuru('Selin Arslan', '10000000146', 'E_DEVLET', '2025 yılında yapılan doğrudan temin alımlarının listesi ve tutarları');
await api('POST', `/api/bilgi-edinme/${b1.id}/cevapla`, t.bilgi, { metin: 'İstenen liste kurum internet sitesinde yayımlanmış olup bağlantısı başvurana bildirilmiştir.' });
kimlikler.cevaplanan = b1.id;

const b2 = await basvuru('Mert Yıldırım', null, 'DILEKCE', 'Kurumun 2024-2026 yıllarına ait bilgi güvenliği denetim raporları');
await api('POST', `/api/bilgi-edinme/${b2.id}/havale`, t.bilgi, { birimId: birim('BIDB') });
await api('POST', `/api/bilgi-edinme/${b2.id}/birim-gorusu`, t.personel, { metin: 'Raporların bir bölümü ayrı bir inceleme gerektirmektedir; birim arşivinden derlenmektedir.' });
await api('POST', `/api/bilgi-edinme/${b2.id}/uzat`, t.bilgi, { gerekce: 'Talep edilen bilgilere birden fazla birimden ulaşılması gerektiğinden ek süre gerekmektedir.' });
kimlikler.incelemede = b2.id;

const b3 = await basvuru('Zehra Kaya', null, 'E_POSTA', 'Kurumun ihale ilanlarında kullandığı şartname şablonları');
kimlikler.yeniBasvuru = b3.id;

writeFileSync(new URL('./kimlikler.json', import.meta.url), JSON.stringify(kimlikler, null, 2));
console.log('Örnek veri yüklendi:', kimlikler);
