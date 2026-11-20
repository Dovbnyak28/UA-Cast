# Досьє історичних NextLib-релізів

26.10.2026. Підготовлено для консультації/погодження з правовласником.
Це не визнання судом порушення і не наданий дозвіл на relicensing.

## Підтверджені факти

Чотири публічні GitHub releases: v0.9.1, v0.9.2, v0.9.3, v0.9.7.
У `gradle/libs.versions.toml` відповідних git tags присутній
`io.github.anilbeesetti:nextlib-media3ext:1.10.1-0.13.0`.
Наявність dependency у tag не є побайтовою перевіркою кожного APK кожного релізу.

Для опублікованого arm64 APK v0.9.7 попередній аудит перевірив actual binary:
SHA256 `9129d05f3c2c80796482d28c99a6547402a12b270ea37ebb6a11c97207b84bb2`.
[Докази й межі перевірки](COPYRIGHT_AND_LEGAL_AUDIT_2026-10-11.md).

Upstream wrapper декларує GPL-3.0; included FFmpeg старого APK містить
LGPL-3-or-later configuration. Новий незалежний LGPL-2.1 decoder не є
corresponding source для старого APK.

## Пакет питань до фахівця/правовласника

1. Які саме раніше передані artifacts і умови розповсюдження потрібно виправити?
2. Чи можливий окремий дозвіл на минуле використання proprietary application
   без relicensing власного коду; які матеріали й повідомлення для цього потрібні?
3. Як належно виконати історичні FFmpeg source/notice/recombination обов'язки,
   враховуючи actual build старого постачальника та його patches/configurations?
4. Чи потрібно тимчасово припинити downloads або додати correction notice,
   і який точний текст/строк пропонується? Видалення файла саме по собі не
   виправляє вже переданих копій і не замінює source obligations.

Не надсилати приватні ключі, плейлисти, provider credentials чи user logs.
До звернення достатньо release URLs, version coordinates, hash, license metadata
та релевантних build sources. Надсилання звернень від імені власника в цьому
етапі не виконувалось. Власний код автоматично під GPL не перелiцензовується.

## Поточне стримування

- Старі releases/assets збережені без зміни; новий application release не створено.
- Поточний код замінив NextLib незалежною Apache/LGPL/BSD інтеграцією.
- Signed workflow блокується `historical_gpl_distribution=false`.
- Після погодженого рішення записати доказ, обсяг, версії й дату, виконати
  конкретні correction/distribution дії та лише тоді змінювати readiness.

[NextLib license](https://github.com/anilbeesetti/nextlib/blob/main/LICENSE),
[POM точної версії](https://repo.maven.apache.org/maven2/io/github/anilbeesetti/nextlib-media3ext/1.10.1-0.13.0/nextlib-media3ext-1.10.1-0.13.0.pom),
[FFmpeg distribution guidance](https://ffmpeg.org/legal.html).
