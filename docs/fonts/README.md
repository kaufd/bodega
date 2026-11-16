# Шрифты

Лежат в `app/src/main/res/font` и попадают в сборку, а не качаются провайдером
Google Play Services: без сервисов гарнитура не приезжала вовсе, и приложение
рисовалось системным гротеском. Настраиваются в `ui/theme/Type.kt`.

| Файл | Начертания | Размер |
|---|---|---|
| `rubik_variable.ttf` | вариативный, ось `wght` 300–900 | 351 КБ |
| `ibm_plex_mono_regular.ttf` | Regular | 132 КБ |
| `ibm_plex_mono_medium.ttf` | Medium | 134 КБ |

Rubik взят вариативным: один файл заменяет четыре статических начертания и
весит меньше их суммы. Ось `wght` поддерживается с API 26 — это и есть `minSdk`.

Оба семейства распространяются под SIL Open Font License 1.1. Она разрешает
встраивание в приложение и требует, чтобы копия лицензии шла вместе со
шрифтами: `Rubik-OFL.txt` и `IBMPlexMono-OFL.txt` рядом.

Источник — репозиторий [google/fonts](https://github.com/google/fonts):
`ofl/rubik` и `ofl/ibmplexmono`.
