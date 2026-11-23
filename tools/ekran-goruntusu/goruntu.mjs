// Çalışan uygulamadan README ekran görüntülerini alır (seed.mjs'ten sonra çalıştırılır).
import { chromium } from 'playwright';
import { mkdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const BASE = process.env.BASE || 'http://localhost:8080';
const SIFRE = process.env.DEMO_SIFRE;
if (!SIFRE) throw new Error('DEMO_SIFRE tanımlı olmalı');

const klasor = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../docs/screenshots');
mkdirSync(klasor, { recursive: true });
const kimlik = JSON.parse(readFileSync(new URL('./kimlikler.json', import.meta.url), 'utf8'));

const tarayici = await chromium.launch();

async function sayfaAc(secenek = {}) {
  const b = await tarayici.newContext({
    viewport: { width: 1280, height: 800 },
    locale: 'tr-TR',
    timezoneId: 'Europe/Istanbul',
    colorScheme: 'light',
    ...secenek,
  });
  return b.newPage();
}

async function girisYap(sayfa, kullanici) {
  await sayfa.goto(BASE + '/');
  await sayfa.fill('#ka', kullanici);
  await sayfa.fill('#ks', SIFRE);
  await sayfa.click('button[type=submit]');
  await sayfa.waitForSelector('.selam');
  await sayfa.waitForLoadState('networkidle');
}

async function git(sayfa, hash, bekle) {
  await sayfa.goto(BASE + '/' + hash);
  await sayfa.reload();
  await sayfa.waitForSelector(bekle);
  await sayfa.waitForLoadState('networkidle');
}

async function cek(sayfa, ad, secenek = {}) {
  await sayfa.screenshot({ path: path.join(klasor, ad + '.jpg'), type: 'jpeg', quality: 88, ...secenek });
  console.log('Kaydedildi:', ad);
}

// 1) Giriş
{
  const s = await sayfaAc({ viewport: { width: 1280, height: 620 } });
  await s.goto(BASE + '/');
  await s.waitForSelector('#ka');
  await cek(s, 'giris');
  await s.context().close();
}

// 2) Genel bakış (üst yönetici): özet kartları ve grafikler
{
  const s = await sayfaAc();
  await girisYap(s, 'yonetici');
  await cek(s, 'genel-bakis', { fullPage: true });

  // 3) Evrak listesi ve arama
  await git(s, '#/evraklar', 'table');
  await cek(s, 'evrak-liste', { fullPage: true });

  // 4) Havale zinciri olan evrak
  await git(s, '#/evrak/' + kimlik.genelge, '.adimlar');
  await cek(s, 'evrak-havale-zinciri', { fullPage: true });

  // 5) GİZLİ evrak (üst yönetici görür)
  await git(s, '#/evrak/' + kimlik.gizli, '.adimlar');
  await cek(s, 'evrak-gizli', { fullPage: true });
  await s.context().close();
}

// 6) Gelen kutusu (birim amiri) ve imza bekleyen giden evrak
{
  const s = await sayfaAc();
  await girisYap(s, 'amir');
  await git(s, '#/kutu', 'table');
  await cek(s, 'gelen-kutusu', { fullPage: true });
  await git(s, '#/evrak/' + kimlik.imzaBekleyen, '.adimlar');
  await cek(s, 'giden-imza', { fullPage: true });
  await s.context().close();
}

// 7) Gelen evrak kaydı formu (kayıt memuru)
{
  const s = await sayfaAc();
  await girisYap(s, 'kayit');
  await git(s, '#/evraklar', 'table');
  await s.click('text=Gelen evrak kaydet');
  await s.fill('#alan-konu', 'Yatırım Programı Hazırlık Yazısı');
  await s.fill('#alan-ilgiliKurum', 'Strateji ve Bütçe Başkanlığı');
  await cek(s, 'gelen-evrak-formu');
  await s.context().close();
}

// 8) Bilgi edinme: liste ve ayrıntı (bilgi edinme sorumlusu)
{
  const s = await sayfaAc();
  await girisYap(s, 'bilgi');
  await git(s, '#/bilgi-edinme', 'table');
  await cek(s, 'bilgi-edinme-liste', { fullPage: true });
  await git(s, '#/basvuru/' + kimlik.incelemede, '.kart');
  await cek(s, 'bilgi-edinme-detay', { fullPage: true });
  await s.context().close();
}

// 9) Arşiv (arşiv sorumlusu): arşivlenmiş evrak
{
  const s = await sayfaAc();
  await girisYap(s, 'arsiv');
  await git(s, '#/evrak/' + kimlik.arsivli, '.adimlar');
  await cek(s, 'arsiv-evrak', { fullPage: true });
  await s.context().close();
}

// 10) Denetim kaydı (denetçi)
{
  const s = await sayfaAc();
  await girisYap(s, 'denetci');
  await git(s, '#/denetim', 'table');
  await cek(s, 'denetim-kaydi', { fullPage: true });
  await s.context().close();
}

// 11) Mobil (birim personeli, gelen kutusu)
{
  const s = await sayfaAc({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  await girisYap(s, 'personel');
  await git(s, '#/evrak/' + kimlik.genelge, '.adimlar');
  await cek(s, 'mobil');
  await s.context().close();
}

// 12) Koyu tema (genel bakış)
{
  const s = await sayfaAc({ colorScheme: 'dark' });
  await girisYap(s, 'yonetici');
  await cek(s, 'koyu-tema', { fullPage: true });
  await s.context().close();
}

// 13) Kullanıcı yönetimi (yönetici)
{
  const s = await sayfaAc();
  await girisYap(s, 'admin');
  await git(s, '#/kullanicilar', 'text=Yeni kullanıcı');
  await cek(s, 'kullanicilar', { fullPage: true });
  await s.context().close();
}

// 14) Swagger arayüzü
{
  const s = await sayfaAc({ viewport: { width: 1280, height: 1100 } });
  await s.goto(BASE + '/swagger');
  await s.waitForSelector('.swagger-ui .opblock-tag');
  await s.waitForLoadState('networkidle');
  await cek(s, 'swagger');
  await s.context().close();
}

await tarayici.close();
