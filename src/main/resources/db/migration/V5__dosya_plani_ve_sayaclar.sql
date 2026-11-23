-- ÖRNEK dosya planı. Kurum kendi standart dosya planını ve saklama sürelerini yükler;
-- saklama_yili boş bırakılan koda ait evrak süresiz saklanır.
INSERT INTO dosya_plani (kod, ad, saklama_yili) VALUES ('010', 'Mevzuat ve genel yazışmalar', 5);
INSERT INTO dosya_plani (kod, ad, saklama_yili) VALUES ('045', 'Basın ve halkla ilişkiler', 5);
INSERT INTO dosya_plani (kod, ad, saklama_yili) VALUES ('100', 'Personel işleri', 15);
INSERT INTO dosya_plani (kod, ad, saklama_yili) VALUES ('120', 'Mali işler ve bütçe', 10);
INSERT INTO dosya_plani (kod, ad, saklama_yili) VALUES ('160', 'Satın alma işleri', 10);
INSERT INTO dosya_plani (kod, ad, saklama_yili) VALUES ('300', 'Kurul ve komisyon kararları', NULL);

-- Sıra numarası sayaçları: yıl-tür. Yeni yıl için satır yoksa uygulama kendisi oluşturur.
INSERT INTO sayac (anahtar, deger) VALUES ('2026-GEL', 0);
INSERT INTO sayac (anahtar, deger) VALUES ('2026-GID', 0);
INSERT INTO sayac (anahtar, deger) VALUES ('2026-BE', 0);
INSERT INTO sayac (anahtar, deger) VALUES ('2027-GEL', 0);
INSERT INTO sayac (anahtar, deger) VALUES ('2027-GID', 0);
INSERT INTO sayac (anahtar, deger) VALUES ('2027-BE', 0);
