# Остаток работ по плагину (после v1.2.1 / 67c2434)

Список ведётся от 2026-11-03. Ветки создавать от `v1.2.1`.

## 0. Мелкие дефекты плагина (быстрые, не про инспекции)

### 0.1 `chore/inspection-descriptions` — нет HTML-описаний инспекций
`src/main/resources/inspectionDescriptions/` отсутствует целиком, ни одного `.html`.
`idea.log` при запуске пишет:
`PluginException: Inspection #CrystalEmptyCollection has no description [Plugin: io.github.unurgunite.crystal]`

Затронуты все 8 инспекций (файл `<shortName>.html` в `inspectionDescriptions/`):
`CrystalArgumentCount`, `CrystalColonSpacing`, `CrystalEmptyCollection`,
`CrystalInstanceVarType`, `CrystalLibFunParameterType`, `CrystalSingleQuoteString`,
`CrystalTypeMismatch`, `CrystalUnusedVariable`.

Последствие: пустая панель «Description» в Settings → Inspections, предупреждение в
логе, и `displayName`/`groupName` есть, а справки нет.
Проверка: `Plugin Verifier` + отсутствие `has no description` в `idea.log`.

### 0.2 Полнота регрессионного покрытия инспекций
Тестами покрыты не все 8 инспекций (`CrystalEmptyCollection`, `CrystalSingleQuoteString`
фактически без поведенческих тестов). Проверить при следующем заходе в эти файлы.

---

## 1. `fix/unused-var-liveness` — 580 отчётов `CrystalUnusedVariable`

Реальный false positive: **чтение в позиции ресивера**.
`header = AMIME::Header::Identification.new(...)`, дальше `header.ids = [...]` и
`header.ids.should eq [...]` — `header` читается, но `CrystalUnusedVarCollect` видит
только bare `CrystalVariableReference`.

Правка в `CrystalUnusedVarCollect` (сбор ссылок), не в `CrystalUnusedVarReads`
(окно чтения уже починено).
Концентрация: athena-console 92, athena-mime 78, athena-routing 74.
Имена: `header` 41, `metadata` 25, `request` 22, `h` 22, `question` 18.

Тест: `CrystalUnusedVariableInspectionTest.kt`.

## 2. `fix/macro-for-macros` — интерполированные имена макросов

`macro {{method.downcase.id}}(path, *args, **named_args, &)` в
`athena/src/controller.cr` — `CrystalMacroIndex` никогда не проиндексирует такое под
`get`, поэтому `CrystalMacroSetters.hasMacro("get")` = false, и DSL-сеттеры
(`return_type`, `status`) продолжают репортиться.

Нужен индекс по шаблону имени либо эвристика по enclosing-макро.

## 3. `fix/parser-tail` — 91 файл из 1660 (5.5%) с parse errors

Хвост one-off конструкций, каждая в 1–2 файлах:
- constant-assignment-with-hash-body (`FORMATS = {`, `AVAILABLE_FORMATTERS = {`) —
  необъяснён до сих пор, синтетика не воспроизводит;
- `@[TestWith({::HTTP::Headers{"x" => "y"}, true})]` — leading-`::` в hash-литерале;
- `def initialize(&@on_call : Crystal::Call, self ->)`;
- `property? {{name}}` в макросах;
- `private record Parameter(T) < Param, value : T do` (частично).

Инструмент: whole-file probe + PSI ancestor path. Синтетические редукции не работают.
Протокол: `stdlibCheckTree` (—no-configuration-cache, -Dgraph.root=…/autoreg/lib),
A/B только по номерам sweep'а, всегда с `generateParser --rerun-tasks`.

## 4. `chore/ci-detekt-main` — гигиена CI

`detektMain`/`detektTest` **не в CI** (только `spotlessCheck` + `detekt`), поэтому
~130 type-resolution findings накопятся снова. Добавить в workflow и почистить.

## 5. `fix/stdlib-index` — структурный долг (низкий приоритет)

`CrystalStdlibFileResolve` резолвит stdlib только по имени файла
(`ClassName -> class_name.cr`), без таблицы символов. Сейчас не проявляется
(кластер `initial_capacity` закрыт через `receiverTypeName`), но это долг.

---

## Вне скоупа (договорено)
- `CrystalEmptyCollection` — 2 отчёта, `{}` без `of K => V` это ошибка самого Crystal.
- `CrystalSingleQuoteString` — regex-литералы, лексика, не инспекция.

## Важно про приоритизацию
Все цифры выше — из снапшота `crystal-check/` от 2026-11-03 00:36, то есть **до**
PR #20/#21. Кластеры 1 и 3 реальны, но 2 и 5 надо перемерить по свежему экспорту,
иначе рискуем чинить уже исправленное (так вышло с макро-кластером).
