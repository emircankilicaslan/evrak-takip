'use strict';

const EVRAK_DURUM = { TASLAK: 'Taslak', IMZA_BEKLIYOR: 'İmza bekliyor', KAYITLI: 'Kayıtlı', HAVALEDE: 'Havalede', TAMAMLANDI: 'Tamamlandı', ARSIVLENDI: 'Arşivlendi' };
const YON = { GELEN: 'Gelen', GIDEN: 'Giden' };
const GIZLILIK = { NORMAL: 'Normal', HIZMETE_OZEL: 'Hizmete özel', GIZLI: 'Gizli' };
const ACILIYET = { NORMAL: 'Normal', ACELE: 'Acele', GUNLUDUR: 'Günlüdür' };
const BASVURU_DURUM = { ALINDI: 'Alındı', INCELEMEDE: 'İncelemede', CEVAPLANDI: 'Cevaplandı', REDDEDILDI: 'Reddedildi', YONLENDIRILDI: 'Yönlendirildi' };
const KANAL = { DILEKCE: 'Dilekçe', E_DEVLET: 'e-Devlet', E_POSTA: 'E-posta' };
const RET = { ISTISNA_KAPSAMINDA: 'Kanundaki istisna kapsamında', BILGI_MEVCUT_DEGIL: 'Bilgi mevcut değil', BASVURU_KOSULLARI_SAGLANMIYOR: 'Başvuru koşulları sağlanmıyor', DIGER: 'Diğer' };
const ROL = { KAYIT_MEMURU: 'Kayıt memuru', BIRIM_PERSONELI: 'Birim personeli', BIRIM_AMIRI: 'Birim amiri', UST_YONETICI: 'Üst yönetici', BILGI_EDINME_SORUMLUSU: 'Bilgi edinme sorumlusu', ARSIV_SORUMLUSU: 'Arşiv sorumlusu', DENETCI: 'Denetçi', ADMIN: 'Yönetici' };
const EVRAK_OKUYAN = ['KAYIT_MEMURU', 'BIRIM_PERSONELI', 'BIRIM_AMIRI', 'UST_YONETICI', 'ARSIV_SORUMLUSU', 'DENETCI'];
const BE_OKUYAN = ['BILGI_EDINME_SORUMLUSU', 'UST_YONETICI', 'DENETCI', 'BIRIM_PERSONELI', 'BIRIM_AMIRI'];
const ARSIV_OKUYAN = ['ARSIV_SORUMLUSU', 'UST_YONETICI', 'DENETCI'];
const DENETIM_OKUYAN = ['DENETCI', 'ADMIN', 'UST_YONETICI'];
const BIRIM_ROLLERI = ['BIRIM_PERSONELI', 'BIRIM_AMIRI'];

let oturum = null;
try { oturum = JSON.parse(sessionStorage.getItem('oturum')); } catch (e) { oturum = null; }
let evrakSuzgec = { ara: '', yon: '', durum: '', gizlilik: '', geciken: false };
let beSuzgec = { ara: '', durum: '', geciken: false };

function oturumuYaz(d) {
  oturum = d;
  try { if (d) sessionStorage.setItem('oturum', JSON.stringify(d)); else sessionStorage.removeItem('oturum'); } catch (e) { /* bellekte kalır */ }
}
const rolde = (...roller) => oturum && roller.flat().includes(oturum.rol);

function el(etiket, oz, ...cocuklar) {
  const d = document.createElement(etiket);
  for (const [a, v] of Object.entries(oz || {})) {
    if (v === false || v == null) continue;
    if (a === 'sinif') d.className = v;
    else if (a.startsWith('on')) d.addEventListener(a.slice(2), v);
    else d.setAttribute(a, v === true ? '' : v);
  }
  for (const c of cocuklar.flat()) {
    if (c == null || c === false) continue;
    d.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return d;
}
const tarih = (v) => (v ? new Date(String(v).slice(0, 10) + 'T00:00:00').toLocaleDateString('tr-TR') : '-');
const zaman = (v) => (v ? new Date(v).toLocaleString('tr-TR') : '-');
const rozet = (m, s) => el('span', { sinif: 'rozet ' + (s || '') }, m);
const sorgu = (o) => { const p = new URLSearchParams(); for (const [k, v] of Object.entries(o)) if (v !== '' && v != null && v !== false) p.set(k, v); return p.toString(); };

let bildirimZ;
function bildir(m, hata) {
  const k = document.getElementById('bildirim');
  k.textContent = m; k.className = 'bildirim' + (hata ? ' hata' : ''); k.hidden = false;
  clearTimeout(bildirimZ); bildirimZ = setTimeout(() => { k.hidden = true; }, hata ? 7000 : 3500);
}

async function api(yol, s = {}) {
  const b = { Accept: 'application/json' };
  if (s.govde !== undefined) b['Content-Type'] = 'application/json';
  if (oturum) b.Authorization = 'Bearer ' + oturum.token;
  const y = await fetch(yol, { method: s.yontem || 'GET', headers: b, body: s.govde !== undefined ? JSON.stringify(s.govde) : undefined });
  if (y.status === 401 && oturum && yol !== '/api/auth/giris') {
    oturumuYaz(null); git('#/'); throw new Error('Oturum süresi doldu, yeniden giriş yapın');
  }
  const m = await y.text();
  const v = m ? JSON.parse(m) : null;
  if (!y.ok) throw new Error((v && v.detail) || 'İşlem başarısız (' + y.status + ')');
  return v;
}
const post = (yol, govde) => api(yol, { yontem: 'POST', govde: govde === undefined ? undefined : govde });

async function dene(islem, basari) {
  try { await islem(); if (basari) bildir(basari); return true; } catch (h) { bildir(h.message, true); return false; }
}
function git(h) { if (location.hash === h) yonlendir(); else location.hash = h; }

async function indir(yol) {
  const y = await fetch(yol, { headers: { Authorization: 'Bearer ' + oturum.token } });
  if (!y.ok) { const v = await y.json().catch(() => null); throw new Error((v && v.detail) || 'İndirilemedi'); }
  const blob = await y.blob();
  const ad = /filename="?([^";]+)"?/.exec(y.headers.get('Content-Disposition') || '');
  const u = URL.createObjectURL(blob);
  const a = el('a', { href: u, download: ad ? ad[1] : 'dosya' });
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(u), 1000);
}

/** Küçük form penceresi; alanlar: [{ad, etiket, tur, secenekler, zorunlu, deger}]. Vazgeçilirse null. */
function sor(baslik, alanlar) {
  const kutu = document.getElementById('iletisim');
  const form = document.getElementById('iletisim-form');
  const kap = document.getElementById('iletisim-alanlar');
  document.getElementById('iletisim-baslik').textContent = baslik;
  kap.replaceChildren();
  const girdiler = {};
  for (const a of alanlar) {
    const id = 'alan-' + a.ad;
    const zorunlu = a.zorunlu !== false;
    let g;
    if (a.tur === 'select') g = el('select', { id, required: zorunlu }, a.secenekler.map(([d, e]) => el('option', { value: d }, e)));
    else if (a.tur === 'textarea') g = el('textarea', { id, rows: 3, required: zorunlu, maxlength: 1000 });
    else g = el('input', { id, type: a.tur || 'text', required: zorunlu, min: a.min, max: a.max });
    if (a.deger != null) g.value = a.deger;
    girdiler[a.ad] = g;
    kap.append(el('div', { sinif: 'alan' }, el('label', { for: id }, a.etiket), g));
  }
  return new Promise((coz) => {
    let bitti = false;
    const iptal = document.getElementById('iletisim-iptal');
    const bitir = (d) => {
      if (bitti) return; bitti = true;
      form.removeEventListener('submit', gonder); kutu.removeEventListener('close', vazgec); iptal.removeEventListener('click', vazgec);
      if (kutu.open) kutu.close();
      coz(d);
    };
    const gonder = (o) => {
      o.preventDefault();
      if (!form.reportValidity()) return;
      const s = {};
      for (const [ad, g] of Object.entries(girdiler)) s[ad] = g.value.trim();
      bitir(s);
    };
    const vazgec = () => bitir(null);
    form.addEventListener('submit', gonder); kutu.addEventListener('close', vazgec); iptal.addEventListener('click', vazgec);
    kutu.showModal();
    const ilk = kap.querySelector('input, select, textarea'); if (ilk) ilk.focus();
  });
}
const sec = (obj) => Object.entries(obj);

function alanDl(satirlar) {
  return el('dl', { sinif: 'ayrinti' }, satirlar.filter(Boolean).flatMap(([e, d]) => [el('dt', null, e), el('dd', null, d == null || d === '' ? '-' : d)]));
}
function sayfalama(s, ac) {
  if (!s || s.totalPages <= 1) return null;
  return el('div', { sinif: 'sayfalama' },
    el('button', { type: 'button', sinif: 'ikincil kucuk', disabled: s.number === 0, onclick: () => ac(s.number - 1) }, 'Önceki'),
    el('span', null, 'Sayfa ' + (s.number + 1) + ' / ' + s.totalPages + ' (' + s.totalElements + ' kayıt)'),
    el('button', { type: 'button', sinif: 'ikincil kucuk', disabled: s.number + 1 >= s.totalPages, onclick: () => ac(s.number + 1) }, 'Sonraki'));
}
function tablo(basliklar, satirlar, bos) {
  if (!satirlar.length) return el('div', { sinif: 'bos' }, bos || 'Kayıt yok');
  return el('div', { sinif: 'tablo-kap' }, el('table', null,
    el('thead', null, el('tr', null, basliklar.map((b) => el('th', null, b)))), el('tbody', null, satirlar)));
}
const sure = (kalan, gecikmis) => (kalan == null ? null : gecikmis ? rozet(Math.abs(kalan) + ' iş günü gecikti', 'uyari') : rozet(kalan + ' iş günü kaldı', kalan <= 3 ? 'durum-DEGERLENDIRMEDE' : ''));

const SVGNS = 'http://www.w3.org/2000/svg';
function svg(etiket, oz, ...cocuklar) {
  const d = document.createElementNS(SVGNS, etiket);
  for (const [k, v] of Object.entries(oz || {})) d.setAttribute(k, v);
  for (const c of cocuklar.flat()) if (c != null) d.append(c instanceof Node ? c : document.createTextNode(String(c)));
  return d;
}
const ayAdi = (ay) => new Date(ay + '-01T00:00:00').toLocaleDateString('tr-TR', { month: 'short' });

function aylikGrafik(aylik) {
  const W = 560, H = 210, ust = 22, alt = 30;
  const en = Math.max(1, ...aylik.flatMap((a) => [a.gelen, a.giden]));
  const kolon = (W - 24) / aylik.length;
  const yuk = (n) => ((H - ust - alt) * n) / en;
  const parcalar = [svg('line', { x1: 12, y1: H - alt, x2: W - 12, y2: H - alt, class: 'eksen-cizgi' })];
  aylik.forEach((a, i) => {
    const x = 12 + i * kolon;
    const g = yuk(a.gelen), c = yuk(a.giden);
    parcalar.push(
      svg('rect', { x: x + kolon * 0.12, y: H - alt - g, width: kolon * 0.36, height: g, rx: 3, class: 'cubuk-gelen' }),
      svg('rect', { x: x + kolon * 0.52, y: H - alt - c, width: kolon * 0.36, height: c, rx: 3, class: 'cubuk-giden' }),
      a.gelen ? svg('text', { x: x + kolon * 0.3, y: H - alt - g - 5, 'text-anchor': 'middle', class: 'deger' }, a.gelen) : null,
      a.giden ? svg('text', { x: x + kolon * 0.7, y: H - alt - c - 5, 'text-anchor': 'middle', class: 'deger' }, a.giden) : null,
      svg('text', { x: x + kolon / 2, y: H - 10, 'text-anchor': 'middle', class: 'eksen' }, ayAdi(a.ay)));
  });
  return svg('svg', { viewBox: '0 0 ' + W + ' ' + H, class: 'grafik', role: 'img', 'aria-label': 'Son altı ayın gelen ve giden evrak sayısı' }, parcalar);
}

function birimGrafigi(yuk) {
  const satir = 30, W = 560, H = Math.max(60, yuk.length * satir + 8);
  const en = Math.max(1, ...yuk.map((b) => b.acik));
  const parcalar = [];
  yuk.forEach((b, i) => {
    const y = 4 + i * satir;
    const genislik = ((W - 300) * b.acik) / en;
    parcalar.push(
      svg('text', { x: 0, y: y + 17, class: 'eksen-yatay' }, b.birim.length > 34 ? b.birim.slice(0, 33) + '…' : b.birim),
      svg('rect', { x: 240, y, width: Math.max(genislik, b.acik ? 4 : 0), height: 20, rx: 4, class: 'cubuk-gelen' }),
      svg('text', { x: 246 + Math.max(genislik, 4), y: y + 15, class: 'deger-yatay' }, b.acik));
  });
  return svg('svg', { viewBox: '0 0 ' + W + ' ' + H, class: 'grafik', role: 'img', 'aria-label': 'Birimlerdeki açık evrak sayısı' }, parcalar);
}

function adimlar(e) {
  const adlar = e.yon === 'GELEN' ? ['Kayıt', 'Havale', 'Tamamlandı', 'Arşiv'] : ['Taslak', 'İmzada', 'İmzalandı', 'Arşiv'];
  const sira = { TASLAK: 0, KAYITLI: 0, IMZA_BEKLIYOR: 1, HAVALEDE: 1, TAMAMLANDI: 2, ARSIVLENDI: 3 }[e.durum];
  return el('ol', { sinif: 'adimlar', 'aria-label': 'Süreç adımları' }, adlar.map((a, i) => {
    const gecti = i < sira || (i === sira && sira === 3);
    return el('li', { sinif: gecti ? 'gecti' : i === sira ? 'simdi' : '', 'aria-current': i === sira ? 'step' : false },
      el('span', { sinif: 'adim-no' }, gecti ? '✓' : i + 1), a);
  }));
}

async function gelenKutusu(sayfa = 0) {
  const s = await api('/api/evraklar/gelen-kutusu?' + sorgu({ sayfa, boyut: 15 }));
  const satirlar = s.content.map((e) => el('tr', { sinif: 'tikla', onclick: () => git('#/evrak/' + e.id) },
    el('td', null, e.kayitNo), el('td', null, e.konu), el('td', null, e.ilgiliKurum),
    el('td', null, e.aciliyet !== 'NORMAL' ? rozet(ACILIYET[e.aciliyet], 'uyari') : '-'),
    el('td', null, e.cevapSuresiSonu ? [tarih(e.cevapSuresiSonu), ' ', sure(e.kalanIsGunu, e.gecikmis)] : '-')));
  return el('div', null, el('h2', null, 'Gelen kutum'),
    el('p', { sinif: 'soluk' }, 'Bana (amirsem birimime) havale edilen ve henüz tamamlanmamış evraklar; cevap süresi en yakın olan üstte.'),
    tablo(['Kayıt no', 'Konu', 'Gönderen', 'Aciliyet', 'Cevap süresi'], satirlar, 'Bekleyen işiniz yok'),
    sayfalama(s, (n) => dene(async () => { document.getElementById('icerik').replaceChildren(await gelenKutusu(n)); })));
}

// ------------------------------------------------------------------ sayfalar

function giris() {
  const hata = el('div', { sinif: 'hata-metni', role: 'alert' });
  const ad = el('input', { id: 'ka', autocomplete: 'username', required: true });
  const sifre = el('input', { id: 'ks', type: 'password', autocomplete: 'current-password', required: true });
  const form = el('form', { sinif: 'kart giris', onsubmit: async (o) => {
    o.preventDefault(); hata.textContent = '';
    try { oturumuYaz(await post('/api/auth/giris', { kullaniciAdi: ad.value.trim(), sifre: sifre.value })); git('#/'); } catch (h) { hata.textContent = h.message; }
  } }, el('h2', null, 'Giriş'),
  el('div', { sinif: 'alan' }, el('label', { for: 'ka' }, 'Kullanıcı adı'), ad),
  el('div', { sinif: 'alan' }, el('label', { for: 'ks' }, 'Parola'), sifre), hata, el('button', { type: 'submit' }, 'Giriş yap'));
  return form;
}

async function ozetSayfasi() {
  const o = await api('/api/ozet');
  const kart = (e, d, hedef, uyari) => el('button', { type: 'button', sinif: 'ozet-kart' + (uyari && d > 0 ? ' secili' : ''), onclick: hedef }, el('span', { sinif: 'ozet-etiket' }, e), el('span', { sinif: 'ozet-deger' }, d));
  const kartlar = [];
  if (rolde(EVRAK_OKUYAN)) {
    kartlar.push(kart('Kayıtlı (havale bekliyor)', o.evrakDurumlari.KAYITLI, () => { evrakSuzgec = { ...evrakSuzgec, durum: 'KAYITLI', geciken: false }; git('#/evraklar'); }));
    kartlar.push(kart('Havalede', o.evrakDurumlari.HAVALEDE, () => { evrakSuzgec = { ...evrakSuzgec, durum: 'HAVALEDE', geciken: false }; git('#/evraklar'); }));
    kartlar.push(kart('İmza bekleyen', o.imzaBekleyen, () => { evrakSuzgec = { ...evrakSuzgec, durum: 'IMZA_BEKLIYOR', geciken: false }; git('#/evraklar'); }));
    kartlar.push(kart('Cevap süresi geçen evrak', o.gecikenEvrak, () => { evrakSuzgec = { ...evrakSuzgec, durum: '', geciken: true }; git('#/evraklar'); }, true));
  }
  if (rolde(BE_OKUYAN)) {
    kartlar.push(kart('Açık bilgi edinme başvurusu', o.bilgiEdinmeAcik, () => { beSuzgec = { ...beSuzgec, geciken: false }; git('#/bilgi-edinme'); }));
    kartlar.push(kart('Süresi yaklaşan (3 iş günü)', o.bilgiEdinmeYaklasan, () => git('#/bilgi-edinme')));
    kartlar.push(kart('Süresi geçen başvuru', o.bilgiEdinmeGeciken, () => { beSuzgec = { ...beSuzgec, geciken: true }; git('#/bilgi-edinme'); }, true));
  }
  if (rolde('ADMIN')) {
    kartlar.push(kart('Kullanıcı yönetimi', '→', () => git('#/kullanicilar')));
    kartlar.push(kart('Denetim kaydı', '→', () => git('#/denetim')));
  }
  if (rolde(BIRIM_ROLLERI)) kartlar.unshift(kart('Gelen kutum', o.gelenKutusu, () => git('#/kutu'), true));
  let grafikler = null;
  if (rolde(EVRAK_OKUYAN)) {
    const g = await api('/api/ozet/grafik');
    grafikler = el('div', { sinif: 'grafik-izgara' },
      el('section', { sinif: 'kart' }, el('h3', { sinif: 'kart-baslik' }, 'Aylık evrak sayısı'),
        el('div', { sinif: 'lejant' }, el('span', { sinif: 'lejant-gelen' }, 'Gelen'), el('span', { sinif: 'lejant-giden' }, 'Giden')), aylikGrafik(g.aylik)),
      el('section', { sinif: 'kart' }, el('h3', { sinif: 'kart-baslik' }, 'Birimlerde açık evrak'), birimGrafigi(g.birimYuku)));
  }
  const selam = el('div', { sinif: 'selam' }, el('h2', null, 'Merhaba, ' + oturum.adSoyad.split(' ')[0]),
    el('p', { sinif: 'soluk' }, ROL[oturum.rol] + ' · ' + new Date().toLocaleDateString('tr-TR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })));
  return el('div', null, selam, el('div', { sinif: 'ozet' }, kartlar), grafikler);
}

async function evrakListe(sayfa = 0) {
  const f = evrakSuzgec;
  const s = await api('/api/evraklar?' + sorgu({ ...f, sayfa, boyut: 15, sirala: f.geciken ? 'SURE_YAKIN' : 'YENI' }));
  const secenek = (obj, bos) => [el('option', { value: '' }, bos), ...sec(obj).map(([d, e]) => el('option', { value: d, selected: f[bos === 'Tüm yönler' ? 'yon' : bos === 'Tüm durumlar' ? 'durum' : 'gizlilik'] === d }, e))];
  const ara = el('input', { sinif: 'ara-girdi', type: 'search', placeholder: 'Konu, kurum veya kayıt no', value: f.ara });
  const yenile = () => { evrakSuzgec = { ...f, ara: ara.value, yon: yon.value, durum: durum.value, gizlilik: giz.value }; yonlendir(); };
  const yon = el('select', { onchange: yenile }, secenek(YON, 'Tüm yönler'));
  const durum = el('select', { onchange: yenile }, secenek(EVRAK_DURUM, 'Tüm durumlar'));
  const giz = el('select', { onchange: yenile }, secenek(GIZLILIK, 'Tüm gizlilikler'));
  const satirlar = s.content.map((e) => el('tr', { sinif: 'tikla', onclick: () => git('#/evrak/' + e.id) },
    el('td', null, e.kayitNo || '(sayı verilmedi)'), el('td', null, YON[e.yon]), el('td', null, e.konu),
    el('td', null, e.ilgiliKurum), el('td', null, rozet(EVRAK_DURUM[e.durum], 'durum-' + e.durum)),
    el('td', null, e.gizlilik !== 'NORMAL' && rozet(GIZLILIK[e.gizlilik], 'uyari'), ' ', e.aciliyet !== 'NORMAL' && rozet(ACILIYET[e.aciliyet])),
    el('td', null, e.cevapSuresiSonu ? [tarih(e.cevapSuresiSonu), ' ', sure(e.kalanIsGunu, e.gecikmis)] : '-')));
  return el('div', null, el('h2', null, 'Evraklar'),
    el('div', { sinif: 'arac' },
      el('form', { sinif: 'ara-formu', onsubmit: (o) => { o.preventDefault(); yenile(); } }, ara, el('button', { type: 'submit', sinif: 'ikincil' }, 'Ara')),
      yon, durum, giz,
      el('label', { sinif: 'onay' }, el('input', { type: 'checkbox', checked: f.geciken, onchange: (o) => { evrakSuzgec = { ...f, geciken: o.target.checked }; yonlendir(); } }), ' Yalnız geciken'),
      el('span', { sinif: 'bosluk' }),
      rolde('KAYIT_MEMURU') && el('button', { type: 'button', onclick: gelenForm }, 'Gelen evrak kaydet'),
      rolde(BIRIM_ROLLERI) && el('button', { type: 'button', onclick: () => gidenForm(null) }, 'Giden evrak taslağı'),
      el('button', { type: 'button', sinif: 'ikincil', onclick: () => dene(() => indir('/api/disa-aktar/evraklar?' + sorgu({ bicim: 'xlsx', ...f })), 'Dosya indirildi') }, 'Excel')),
    tablo(['Kayıt no', 'Yön', 'Konu', 'Kurum', 'Durum', 'Nitelik', 'Cevap süresi'], satirlar, 'Görebildiğiniz evrak yok'),
    sayfalama(s, (n) => dene(async () => { document.getElementById('icerik').replaceChildren(await evrakListe(n)); })));
}

async function gelenForm() {
  const v = await sor('Gelen evrak kaydı', [
    { ad: 'konu', etiket: 'Konu' }, { ad: 'ilgiliKurum', etiket: 'Gönderen kurum' },
    { ad: 'evrakTarihi', etiket: 'Evrak tarihi', tur: 'date' }, { ad: 'disSayi', etiket: 'Evrakın sayısı', zorunlu: false },
    { ad: 'gizlilik', etiket: 'Gizlilik', tur: 'select', secenekler: sec(GIZLILIK) },
    { ad: 'aciliyet', etiket: 'Aciliyet', tur: 'select', secenekler: sec(ACILIYET) },
    { ad: 'cevapGunu', etiket: 'Cevap süresi (iş günü; yalnız günlüdür)', tur: 'number', min: 1, max: 60, zorunlu: false }]);
  if (!v) return;
  await dene(async () => {
    const e = await post('/api/evraklar/gelen', { ...v, cevapGunu: v.cevapGunu ? Number(v.cevapGunu) : null, disSayi: v.disSayi || null });
    git('#/evrak/' + e.id);
  }, 'Evrak kaydedildi');
}

async function gidenForm(ilgili) {
  const v = await sor(ilgili ? 'Cevap taslağı' : 'Giden evrak taslağı', [
    { ad: 'konu', etiket: 'Konu', deger: ilgili ? 'Cevap: ' + ilgili.konu : '' },
    { ad: 'ilgiliKurum', etiket: 'Alıcı kurum', deger: ilgili ? ilgili.ilgiliKurum : '' },
    { ad: 'gizlilik', etiket: 'Gizlilik', tur: 'select', secenekler: sec(GIZLILIK) }]);
  if (!v) return;
  await dene(async () => { const e = await post('/api/evraklar/giden', { ...v, ilgiliEvrakId: ilgili ? ilgili.id : null }); git('#/evrak/' + e.id); }, 'Taslak oluşturuldu');
}

async function evrakDetay(id) {
  const { evrak: e, sevkler } = await api('/api/evraklar/' + id);
  const benimBirimde = oturum.birimId != null && oturum.birimId === e.birimId;
  const yenile = () => yonlendir();
  const eylem = (ad, f, sinif) => el('button', { type: 'button', sinif, onclick: f }, ad);
  const eylemler = [];
  const islemde = e.yon === 'GELEN' && (e.durum === 'KAYITLI' || e.durum === 'HAVALEDE');
  if (islemde && (rolde('UST_YONETICI') || (rolde('KAYIT_MEMURU') && e.durum === 'KAYITLI') || (rolde('BIRIM_AMIRI') && benimBirimde))) eylemler.push(eylem('Havale et', () => havaleEt(e, yenile)));
  if (e.yon === 'GELEN' && e.durum === 'HAVALEDE' && rolde(BIRIM_ROLLERI) && benimBirimde) {
    eylemler.push(eylem('Cevap taslağı yaz', () => gidenForm(e)));
    eylemler.push(eylem('Tamamla', async () => {
      const v = await sor('Evrakı tamamla', [{ ad: 'cevapEvrakId', etiket: 'Cevap evrakı no (varsa)', tur: 'number', min: 1, zorunlu: false }, { ad: 'notu', etiket: 'Tamamlama notu', tur: 'textarea', zorunlu: false }]);
      if (v && await dene(() => post('/api/evraklar/' + e.id + '/tamamla', { cevapEvrakId: v.cevapEvrakId ? Number(v.cevapEvrakId) : null, notu: v.notu || null }), 'Evrak tamamlandı')) yenile();
    }));
  }
  if (e.yon === 'GIDEN' && e.durum === 'TASLAK' && e.olusturan === oturum.kullaniciAdi) eylemler.push(eylem('İmzaya gönder', async () => { if (await dene(() => post('/api/evraklar/' + e.id + '/imzaya-gonder'), 'İmzaya gönderildi')) yenile(); }));
  if (e.yon === 'GIDEN' && e.durum === 'IMZA_BEKLIYOR' && rolde('BIRIM_AMIRI') && benimBirimde) {
    eylemler.push(eylem('İmzala', async () => { if (await dene(() => post('/api/evraklar/' + e.id + '/imzala'), 'İmzalandı')) yenile(); }));
    eylemler.push(eylem('İmzayı reddet', async () => {
      const v = await sor('İmzayı reddet', [{ ad: 'gerekce', etiket: 'Gerekçe', tur: 'textarea' }]);
      if (v && await dene(() => post('/api/evraklar/' + e.id + '/imza-reddet', v), 'Taslağa döndü')) yenile();
    }, 'tehlike'));
  }
  if (e.durum === 'TAMAMLANDI' && rolde('ARSIV_SORUMLUSU', 'UST_YONETICI')) {
    eylemler.push(eylem('Arşivle', async () => {
      const plan = await api('/api/evraklar/dosya-plani');
      const v = await sor('Arşivle', [{ ad: 'dosyaKodu', etiket: 'Dosya planı kodu', tur: 'select', secenekler: plan.map((p) => [p.kod, p.kod + ' – ' + p.ad + (p.saklamaYili ? ' (' + p.saklamaYili + ' yıl)' : ' (süresiz)')]) }]);
      if (v && await dene(() => post('/api/evraklar/' + e.id + '/arsivle', v), 'Arşivlendi')) yenile();
    }));
  }
  const sevkSatir = sevkler.map((s) => el('tr', null, el('td', null, zaman(s.sevkZamani)), el('td', null, s.sevkEden), el('td', null, s.hedefBirim, s.hedefKullanici ? ' / ' + s.hedefKullanici : ''),
    el('td', null, s.talimat || '-'), el('td', null, rozet(s.durum)), el('td', null,
      s.durum === 'BEKLIYOR' && rolde(BIRIM_ROLLERI) && (s.hedefKullanici === oturum.kullaniciAdi || (!s.hedefKullanici && rolde('BIRIM_AMIRI') && benimBirimde)) &&
      eylem('Teslim al', async () => { if (await dene(() => post('/api/evraklar/sevkler/' + s.id + '/teslim-al'), 'Teslim alındı')) yenile(); }, 'kucuk'))));
  return el('div', null,
    el('div', { sinif: 'arac' }, el('button', { type: 'button', sinif: 'ikincil', onclick: () => git('#/evraklar') }, '← Evraklar')),
    el('h2', null, e.konu), adimlar(e),
    el('div', { sinif: 'kart' }, alanDl([
      ['Kayıt no', e.kayitNo || '(imzada verilir)'], ['Yön', YON[e.yon]], ['Durum', rozet(EVRAK_DURUM[e.durum], 'durum-' + e.durum)],
      ['İlgili kurum', e.ilgiliKurum], ['Evrak tarihi', tarih(e.evrakTarihi)], ['Evrakın sayısı', e.disSayi],
      ['Gizlilik', GIZLILIK[e.gizlilik]], ['Aciliyet', ACILIYET[e.aciliyet]], ['Bulunduğu birim', e.birim],
      e.cevapSuresiSonu && ['Cevap süresi sonu', [tarih(e.cevapSuresiSonu), ' ', sure(e.kalanIsGunu, e.gecikmis)]],
      ['Kaydeden', e.olusturan], e.imzalayan && ['İmzalayan', e.imzalayan + ' (' + zaman(e.imzaZamani) + ')'],
      e.redGerekcesi && ['İmza ret gerekçesi', e.redGerekcesi], e.ilgiliEvrakId && ['Cevap verilen evrak no', e.ilgiliEvrakId],
      e.cevapEvrakId && ['Cevap evrakı no', e.cevapEvrakId], e.tamamlamaNotu && ['Tamamlama notu', e.tamamlamaNotu],
      e.dosyaKodu && ['Dosya kodu', e.dosyaKodu], e.arsivTarihi && ['Arşiv tarihi', tarih(e.arsivTarihi)],
      e.durum === 'ARSIVLENDI' && ['İmha tarihi', e.imhaTarihi ? tarih(e.imhaTarihi) : 'Süresiz saklanır']]),
    eylemler.length ? el('div', { sinif: 'eylemler' }, eylemler) : null),
    e.yon === 'GELEN' ? [el('h3', null, 'Havale zinciri'), tablo(['Zaman', 'Havale eden', 'Hedef', 'Talimat', 'Durum', ''], sevkSatir, 'Henüz havale edilmedi')] : null);
}

async function havaleEt(e, yenile) {
  const birimler = await api('/api/birimler');
  const v = await sor('Havale et', [
    { ad: 'birimId', etiket: 'Birim', tur: 'select', secenekler: birimler.map((b) => [b.id, b.ad]) },
    { ad: 'talimat', etiket: 'Talimat', zorunlu: false }]);
  if (!v) return;
  const personel = await api('/api/birimler/' + v.birimId + '/personel');
  const k = await sor('Kime? (GİZLİ evrakta kişi seçimi zorunludur)', [
    { ad: 'kisi', etiket: 'Hedef', tur: 'select', zorunlu: false, secenekler: [['', '— birimin kendisi —'], ...personel.map((p) => [p.id, p.adSoyad + ' (' + ROL[p.rol] + ')'])] }]);
  if (!k) return;
  const govde = { talimat: v.talimat || null };
  if (k.kisi) govde.hedefKullaniciId = Number(k.kisi); else govde.hedefBirimId = Number(v.birimId);
  if (await dene(() => post('/api/evraklar/' + e.id + '/sevk', govde), 'Havale edildi')) yenile();
}

async function bilgiListe(sayfa = 0) {
  const f = beSuzgec;
  const s = await api('/api/bilgi-edinme?' + sorgu({ ...f, sayfa, boyut: 15 }));
  const yenile = () => { beSuzgec = { ...f, ara: ara.value, durum: durum.value }; yonlendir(); };
  const ara = el('input', { sinif: 'ara-girdi', type: 'search', placeholder: 'Başvuru no, ad veya konu', value: f.ara });
  const durum = el('select', { onchange: yenile }, [el('option', { value: '' }, 'Tüm durumlar'), ...sec(BASVURU_DURUM).map(([d, e]) => el('option', { value: d, selected: f.durum === d }, e))]);
  const satirlar = s.content.map((b) => el('tr', { sinif: 'tikla', onclick: () => git('#/basvuru/' + b.id) },
    el('td', null, b.basvuruNo), el('td', null, b.basvuranAd), el('td', null, b.konu), el('td', null, rozet(BASVURU_DURUM[b.durum], 'durum-' + b.durum)),
    el('td', null, b.birim || '-'), el('td', null, [tarih(b.sureSonu), ' ', b.uzatmaSayisi ? rozet('Uzatıldı') : null, ' ', sure(b.kalanIsGunu, b.gecikmis)])));
  return el('div', null, el('h2', null, 'Bilgi edinme başvuruları (4982)'),
    el('div', { sinif: 'arac' },
      el('form', { sinif: 'ara-formu', onsubmit: (o) => { o.preventDefault(); yenile(); } }, ara, el('button', { type: 'submit', sinif: 'ikincil' }, 'Ara')), durum,
      el('label', { sinif: 'onay' }, el('input', { type: 'checkbox', checked: f.geciken, onchange: (o) => { beSuzgec = { ...f, geciken: o.target.checked }; yonlendir(); } }), ' Yalnız geciken'),
      el('span', { sinif: 'bosluk' }),
      rolde('BILGI_EDINME_SORUMLUSU', 'KAYIT_MEMURU') && el('button', { type: 'button', onclick: basvuruForm }, 'Başvuru kaydet'),
      el('button', { type: 'button', sinif: 'ikincil', onclick: () => dene(() => indir('/api/disa-aktar/bilgi-edinme?' + sorgu({ bicim: 'xlsx', ...f })), 'Dosya indirildi') }, 'Excel')),
    tablo(['No', 'Başvuran', 'Konu', 'Durum', 'Birim', 'Süre sonu'], satirlar, 'Başvuru yok'),
    sayfalama(s, (n) => dene(async () => { document.getElementById('icerik').replaceChildren(await bilgiListe(n)); })));
}

async function basvuruForm() {
  const v = await sor('Bilgi edinme başvurusu', [
    { ad: 'basvuranAd', etiket: 'Başvuran adı soyadı' },
    { ad: 'tcKimlikNo', etiket: 'T.C. kimlik no (isteğe bağlı; yalnız maskeli saklanır)', zorunlu: false },
    { ad: 'iletisim', etiket: 'İletişim (e-posta/adres)', zorunlu: false },
    { ad: 'kanal', etiket: 'Başvuru kanalı', tur: 'select', secenekler: sec(KANAL) },
    { ad: 'konu', etiket: 'Talep edilen bilgi', tur: 'textarea' },
    { ad: 'basvuruTarihi', etiket: 'Başvuru tarihi (boşsa bugün)', tur: 'date', zorunlu: false }]);
  if (!v) return;
  await dene(async () => {
    const b = await post('/api/bilgi-edinme', { ...v, tcKimlikNo: v.tcKimlikNo || null, iletisim: v.iletisim || null, basvuruTarihi: v.basvuruTarihi || null });
    if (rolde(BE_OKUYAN)) git('#/basvuru/' + b.id); else bildir('Kaydedildi: ' + b.basvuruNo);
  }, 'Başvuru kaydedildi');
}

async function basvuruDetay(id) {
  const b = await api('/api/bilgi-edinme/' + id);
  const yenile = () => yonlendir();
  const eylem = (ad, f, sinif) => el('button', { type: 'button', sinif, onclick: f }, ad);
  const calistir = async (yol, govde, mesaj) => { if (await dene(() => post('/api/bilgi-edinme/' + id + '/' + yol, govde), mesaj)) yenile(); };
  const e = [];
  if (rolde('BILGI_EDINME_SORUMLUSU') && b.durum !== 'CEVAPLANDI' && b.durum !== 'REDDEDILDI' && b.durum !== 'YONLENDIRILDI') {
    e.push(eylem('Birime havale et', async () => {
      const birimler = await api('/api/birimler');
      const v = await sor('Birime havale', [{ ad: 'birimId', etiket: 'Birim', tur: 'select', secenekler: birimler.map((x) => [x.id, x.ad]) }]);
      if (v) calistir('havale', { birimId: Number(v.birimId) }, 'Havale edildi');
    }));
    if (!b.uzatmaSayisi) e.push(eylem('Süreyi uzat', async () => { const v = await sor('Süre uzatma', [{ ad: 'gerekce', etiket: 'Gerekçe', tur: 'textarea' }]); if (v) calistir('uzat', v, 'Süre uzatıldı'); }));
    e.push(eylem('Cevapla', async () => { const v = await sor('Cevap', [{ ad: 'metin', etiket: 'Cevap özeti', tur: 'textarea' }]); if (v) calistir('cevapla', v, 'Cevaplandı'); }));
    e.push(eylem('Reddet', async () => {
      const v = await sor('Ret', [{ ad: 'nedeni', etiket: 'Ret nedeni', tur: 'select', secenekler: sec(RET) }, { ad: 'gerekce', etiket: 'Gerekçe', tur: 'textarea' }]);
      if (v) calistir('reddet', v, 'Reddedildi');
    }, 'tehlike'));
    if (b.durum === 'ALINDI') e.push(eylem('Başka kuruma yönlendir', async () => { const v = await sor('Yönlendirme', [{ ad: 'kurum', etiket: 'Yetkili kurum' }]); if (v) calistir('yonlendir', v, 'Yönlendirildi'); }));
  }
  if (rolde(BIRIM_ROLLERI) && b.durum === 'INCELEMEDE') e.push(eylem('Birim görüşü yaz', async () => { const v = await sor('Birim görüşü', [{ ad: 'metin', etiket: 'Görüş', tur: 'textarea', deger: b.birimGorusu || '' }]); if (v) calistir('birim-gorusu', v, 'Görüş kaydedildi'); }));
  return el('div', null,
    el('div', { sinif: 'arac' }, el('button', { type: 'button', sinif: 'ikincil', onclick: () => git('#/bilgi-edinme') }, '← Başvurular')),
    el('h2', null, b.basvuruNo),
    el('div', { sinif: 'kart' }, alanDl([
      ['Durum', rozet(BASVURU_DURUM[b.durum], 'durum-' + b.durum)], ['Başvuran', b.basvuranAd], ['T.C. kimlik (maskeli)', b.tcKimlikMaskeli], ['İletişim', b.iletisim],
      ['Kanal', KANAL[b.kanal]], ['Başvuru tarihi', tarih(b.basvuruTarihi)], ['Talep', b.konu],
      ['Süre sonu', [tarih(b.sureSonu), ' ', sure(b.kalanIsGunu, b.gecikmis)]], b.uzatmaSayisi && ['Uzatma gerekçesi', b.uzatmaGerekcesi],
      ['İlgili birim', b.birim], b.birimGorusu && ['Birim görüşü', b.birimGorusu],
      b.sonucTarihi && ['Sonuç tarihi', tarih(b.sonucTarihi) + (b.gecikmeliSonuc ? ' (süre aşıldı)' : '')],
      b.retNedeni && ['Ret nedeni', RET[b.retNedeni]], b.sonucMetni && ['Cevap / gerekçe', b.sonucMetni],
      b.itirazSonTarihi && ['İtiraz son tarihi', tarih(b.itirazSonTarihi)], b.yonlendirilenKurum && ['Yönlendirilen kurum', b.yonlendirilenKurum]]),
    e.length ? el('div', { sinif: 'eylemler' }, e) : null),
    el('p', { sinif: 'soluk' }, 'Kişisel veri içeren bu ekranın görüntülenmesi denetim kaydına işlenmiştir.'));
}

async function arsivSayfasi(sayfa = 0) {
  const s = await api('/api/evraklar/imha-adaylari?' + sorgu({ sayfa, boyut: 15 }));
  const satirlar = s.content.map((e) => el('tr', { sinif: 'tikla', onclick: () => git('#/evrak/' + e.id) }, el('td', null, e.kayitNo), el('td', null, e.konu), el('td', null, e.dosyaKodu), el('td', null, tarih(e.arsivTarihi)), el('td', null, tarih(e.imhaTarihi))));
  return el('div', null, el('h2', null, 'İmha süresi dolan arşiv evrakı'), tablo(['Kayıt no', 'Konu', 'Dosya kodu', 'Arşiv tarihi', 'İmha tarihi'], satirlar, 'İmha süresi dolan evrak yok'),
    sayfalama(s, (n) => dene(async () => { document.getElementById('icerik').replaceChildren(await arsivSayfasi(n)); })));
}

async function denetimSayfasi(sayfa = 0) {
  const s = await api('/api/denetim?' + sorgu({ page: sayfa, size: 30 }));
  const satirlar = s.content.map((k) => el('tr', null, el('td', null, zaman(k.zaman)), el('td', null, k.kullaniciAdi), el('td', null, k.islem), el('td', null, k.varlikTuru + (k.varlikId ? ' #' + k.varlikId : '')), el('td', null, k.ayrinti || '-')));
  return el('div', null, el('h2', null, 'Denetim kaydı'),
    el('div', { sinif: 'arac' }, el('span', { sinif: 'bosluk' }), el('button', { type: 'button', sinif: 'ikincil', onclick: () => dene(() => indir('/api/disa-aktar/denetim?bicim=xlsx'), 'Dosya indirildi') }, 'Excel')),
    tablo(['Zaman', 'Kullanıcı', 'İşlem', 'Kayıt', 'Ayrıntı'], satirlar),
    sayfalama(s, (n) => dene(async () => { document.getElementById('icerik').replaceChildren(await denetimSayfasi(n)); })));
}

async function kullaniciSayfasi() {
  const [liste, birimler] = await Promise.all([api('/api/kullanicilar'), api('/api/birimler')]);
  const alanlar = (k) => [
    { ad: 'rol', etiket: 'Rol', tur: 'select', secenekler: sec(ROL), deger: k && k.rol },
    { ad: 'birimId', etiket: 'Birim (birim rolleri için)', tur: 'select', zorunlu: false, secenekler: [['', '—'], ...birimler.map((b) => [b.id, b.ad])] }];
  const satirlar = liste.map((k) => el('tr', null, el('td', null, k.kullaniciAdi), el('td', null, k.adSoyad), el('td', null, ROL[k.rol]), el('td', null, k.birim || '-'),
    el('td', null, rozet(k.durum === 'AKTIF' ? 'Aktif' : 'Pasif', 'durum-' + k.durum), ' ', k.kilitli && rozet('Kilitli', 'uyari')),
    el('td', null, el('button', { type: 'button', sinif: 'ikincil kucuk', onclick: async () => {
      const v = await sor('Kullanıcıyı düzenle', [...alanlar(k), { ad: 'durum', etiket: 'Durum', tur: 'select', secenekler: [['AKTIF', 'Aktif'], ['PASIF', 'Pasif']], deger: k.durum }]);
      if (v && await dene(() => api('/api/kullanicilar/' + k.id, { yontem: 'PUT', govde: { rol: v.rol, birimId: v.birimId ? Number(v.birimId) : null, durum: v.durum } }), 'Güncellendi')) yonlendir();
    } }, 'Düzenle'), ' ', k.kilitli && el('button', { type: 'button', sinif: 'ikincil kucuk', onclick: async () => { if (await dene(() => post('/api/kullanicilar/' + k.id + '/kilit-ac'), 'Kilit açıldı')) yonlendir(); } }, 'Kilidi aç'))));
  return el('div', null, el('h2', null, 'Kullanıcılar'),
    el('div', { sinif: 'arac' }, el('span', { sinif: 'bosluk' }), el('button', { type: 'button', onclick: async () => {
      const v = await sor('Yeni kullanıcı', [{ ad: 'kullaniciAdi', etiket: 'Kullanıcı adı' }, { ad: 'adSoyad', etiket: 'Ad soyad' }, { ad: 'sifre', etiket: 'Parola (en az 10 karakter)', tur: 'password' }, ...alanlar(null)]);
      if (v && await dene(() => post('/api/kullanicilar', { ...v, birimId: v.birimId ? Number(v.birimId) : null }), 'Kullanıcı oluşturuldu')) yonlendir();
    } }, 'Yeni kullanıcı')),
    tablo(['Kullanıcı adı', 'Ad soyad', 'Rol', 'Birim', 'Durum', ''], satirlar));
}

// ------------------------------------------------------------------ yönlendirme

const MENU = [
  ['#/', 'Genel bakış', () => true],
  ['#/kutu', 'Gelen kutum', () => rolde(BIRIM_ROLLERI)],
  ['#/evraklar', 'Evraklar', () => rolde(EVRAK_OKUYAN)],
  ['#/bilgi-edinme', 'Bilgi edinme', () => rolde(BE_OKUYAN, 'KAYIT_MEMURU')],
  ['#/arsiv', 'Arşiv', () => rolde(ARSIV_OKUYAN)],
  ['#/denetim', 'Denetim kaydı', () => rolde(DENETIM_OKUYAN)],
  ['#/kullanicilar', 'Kullanıcılar', () => rolde('ADMIN')],
];

function menuyuCiz() {
  const menu = document.getElementById('menu');
  const kutu = document.getElementById('kullanici');
  menu.hidden = kutu.hidden = !oturum;
  menu.replaceChildren();
  if (!oturum) return;
  const hash = location.hash || '#/';
  for (const [h, ad, gorunur] of MENU) {
    if (!gorunur()) continue;
    const aktif = h === '#/' ? hash === '#/' : hash.startsWith(h) || (h === '#/evraklar' && hash.startsWith('#/evrak/')) || (h === '#/bilgi-edinme' && hash.startsWith('#/basvuru/'));
    menu.append(el('button', { type: 'button', sinif: 'menu-dugme' + (aktif ? ' aktif' : ''), onclick: () => git(h) }, ad));
  }
  document.getElementById('kullanici-bilgi').textContent = oturum.adSoyad + ' · ' + ROL[oturum.rol];
}

async function yonlendir() {
  const icerik = document.getElementById('icerik');
  menuyuCiz();
  if (!oturum) { icerik.replaceChildren(giris()); return; }
  const h = location.hash || '#/';
  try {
    let sayfa;
    if (h === '#/') sayfa = await ozetSayfasi();
    else if (h === '#/kutu') sayfa = await gelenKutusu();
    else if (h === '#/evraklar') sayfa = await evrakListe();
    else if (h.startsWith('#/evrak/')) sayfa = await evrakDetay(h.slice(8));
    else if (h === '#/bilgi-edinme') sayfa = rolde(BE_OKUYAN) ? await bilgiListe() : el('div', null, el('h2', null, 'Bilgi edinme'), el('button', { type: 'button', onclick: basvuruForm }, 'Başvuru kaydet'));
    else if (h.startsWith('#/basvuru/')) sayfa = await basvuruDetay(h.slice(10));
    else if (h === '#/arsiv') sayfa = await arsivSayfasi();
    else if (h === '#/denetim') sayfa = await denetimSayfasi();
    else if (h === '#/kullanicilar') sayfa = await kullaniciSayfasi();
    else sayfa = el('div', { sinif: 'bos' }, 'Sayfa bulunamadı');
    icerik.replaceChildren(sayfa);
  } catch (hata) {
    if (oturum) icerik.replaceChildren(el('div', { sinif: 'bos' }, hata.message));
  }
}

document.getElementById('cikis').addEventListener('click', () => { oturumuYaz(null); evrakSuzgec = { ara: '', yon: '', durum: '', gizlilik: '', geciken: false }; git('#/'); });
document.getElementById('sifre-degistir').addEventListener('click', async () => {
  const v = await sor('Parola değiştir', [{ ad: 'mevcutSifre', etiket: 'Mevcut parola', tur: 'password' }, { ad: 'yeniSifre', etiket: 'Yeni parola (en az 10 karakter)', tur: 'password' }]);
  if (v) dene(() => post('/api/auth/sifre-degistir', v), 'Parola değiştirildi');
});
window.addEventListener('hashchange', yonlendir);
yonlendir();
