# Structure Analytics Dashboard Plugin — контекст проекта

## Статус
✅ **JAR собран успешно** — [target/structure-dashboard-plugin-1.0.0.jar](https://github.com/alexeyzatochnyy-cmd/jira/blob/claude/quirky-turing-SxrDM/target/structure-dashboard-plugin-1.0.0.jar) (384 KB)

## Стек
- **Jira Server 8.20.30** (не Cloud, не DC)
- **Structure 9.3.2** by Tempo Software
- **Java 11**, Maven (Central, не Atlassian repo)
- **Apache HttpClient 4.5** (provided Jira)
- **Jackson 2.13**, Active Objects 3.0.6
- **ECharts 5.4.3**, **Sortable.js 1.15.0** (bundled в resources/js/, скачаны через npm)

## Критичные адаптации сборки (обязательно сохранить!)

> `packages.atlassian.com` заблокирован в среде Claude Code — сборка адаптирована под Maven Central.

| Что | Как в оригинале | Как адаптировано |
|-----|----------------|-----------------|
| Packaging | `atlassian-plugin` (AMPS) | `bundle` (Apache Felix maven-bundle-plugin) |
| Atlassian API | из `packages.atlassian.com` | Stub JAR — compile-time only, в финальный JAR не попадает |
| Spring Scanner | `atlassian-spring-scanner-*` | Удалён из pom.xml, подключается at runtime внутри Jira |
| JS-библиотеки | cdn.jsdelivr.net | Скачаны через npm registry |
| `@ComponentImport` | из Spring Scanner | В stub JAR для compile-time |
| `net.java.ao.*` | из AO plugin | В stub JAR для compile-time |

**Важно:** при любом изменении `pom.xml` — сохранять `bundle` packaging и stub JAR, не возвращаться к `atlassian-plugin`.

## Структура файлов

```
structure-dashboard-plugin/
```

`├── pom.xml                          ← bundle packaging, Felix plugin, Maven Central`
`├── build.sh                         ← сборка одной командой (./build.sh)`
`├── src/main/`
`│   ├── java/com/company/jira/structuredash/`
`│   │   ├── ao/`
`│   │   │   ├── DashboardLayout.java     ← AO entity: SD_DASHBOARD (dashboardId, name, ownerKey, sharedWith)`
`│   │   │   └── WidgetConfig.java        ← AO entity: SD_WIDGET (widgetId, dashboardId, type, cols, widgetOrder, structureId, columnKeys JSON, options JSON)`
`│   │   ├── model/`
`│   │   │   ├── StructureInfo.java       ← {id, name, description}`
`│   │   │   ├── ForestRow.java           ← {issueId, depth, childCount}`
`│   │   │   ├── ForestResponse.java      ← список issueIds + rows; getTopLevelIds(), getChildrenOf(id)`
`│   │   │   ├── StructureAggregates.java ← {totalIssues, overallProgress, sums{col→double}, counts{col→int}}`
`│   │   │   ├── EpicProgressItem.java    ← {issueId, progress, childCount, columnValues{key→Object}}`
`│   │   │   ├── WidgetConfigDto.java     ← {id, type, title, cols, order, structureId, columnKeys[], options{}}`
`│   │   │   ├── DashboardLayoutDto.java  ← {id, name, ownerKey, widgets[]}`
`│   │   │   └── ApiError.java            ← {status, message}`
`│   │   ├── service/`
`│   │   │   └── StructureApiService.java ← вся работа с Structure REST API (Apache HttpClient)`
`│   │   ├── rest/`
`│   │   │   └── DashboardResource.java   ← JAX-RS /rest/structuredash/1.0/...`
`│   │   └── servlet/`
`│   │       └── DashboardServlet.java    ← рендерит dashboard.vm, проверяет auth`
`│   └── resources/`
`│       ├── atlassian-plugin.xml         ← дескриптор: servlet, rest, web-resource, web-item, ao, i18n`
`│       ├── templates/dashboard.vm       ← HTML-оболочка SPA`
`│       ├── js/`
`│       │   ├── echarts.min.js           ← ECharts 5.4.3 (из npm)`
`│       │   ├── sortable.min.js          ← Sortable.js 1.15.0 (из npm)`
`│       │   └── dashboard.js             ← SPA: рендер виджетов, API, drag&drop, layout`
`│       ├── css/dashboard.css            ← стили (ADS цвета, 12-col grid, виджеты, модалка)`
`│       └── i18n/structure-dashboard.properties`

## Structure REST API — как используется

```
GET  /rest/structure/2.0/structure/
```

`     → список структур пользователя`
`GET  /rest/structure/2.0/forest/latest?structureId={id}&expand=items`
`     → [{item:"issue:123", depth:0, childCount:3}, ...]`
`POST /rest/structure/2.0/forest/value`
`     body: { structureId, rows:[{item:"issue:123"},...], columns:[{key:"progress"},...] }`
`     → { values:[{ row:"issue:123", columns:{ progress:{v:63}, story_points:{v:8} } },...] }`

Аутентификация: **cookie-based** — сессионные куки Jira передаются из HttpServletRequest.

## REST API плагина

```
GET  /rest/structuredash/1.0/structures
```

`GET  /rest/structuredash/1.0/structure/{id}/aggregates?columns=progress,story_points_sum`
`GET  /rest/structuredash/1.0/structure/{id}/epic-progress?columns=progress,story_points`
`POST /rest/structuredash/1.0/structure/{id}/column-values   body:{issueIds,columnKeys}`
`GET    /rest/structuredash/1.0/dashboards`
`GET    /rest/structuredash/1.0/dashboards/{dashboardId}`
`POST   /rest/structuredash/1.0/dashboards`
`PUT    /rest/structuredash/1.0/dashboards/{dashboardId}`
`DELETE /rest/structuredash/1.0/dashboards/{dashboardId}`

## Типы виджетов

| type | источник данных | описание |
|------|----------------|----------|
| `kpi` | aggregates | одно число + sparkline + delta |
| `progress-ring` | aggregates.overallProgress | SVG-кольцо % выполнения |
| `epic-progress` | epic-progress | прогресс-бары по верхнему уровню иерархии |
| `aggregates-bar` | aggregates.sums | bar chart по колонкам Structure |
| `donut` | aggregates.sums | donut chart распределения |
| `table` | epic-progress | таблица задач с формульными колонками |

## Стандартные ключи колонок Structure 9.x

| ключ | тип | описание |
|------|-----|----------|
| `progress` | number 0-100 | % выполнения |
| `story_points` | number | Story Points задачи |
| `story_points_sum` | number | сумма SP по иерархии |
| `time_original_estimate` | number (сек) | оригинальная оценка |
| `time_spent` | number (сек) | затраченное время |
| `time_remaining` | number (сек) | оставшееся |
| `issue_count` | number | кол-во задач |
| `resolved_count` | number | завершённых |

Кастомные формульные колонки — смотреть ключи через `GET /rest/structure/2.0/column`.

## URL после установки в Jira

```
https://your-jira/plugins/servlet/structure-dashboard
```

`https://your-jira/plugins/servlet/structure-dashboard?dashboard={uuid}`

## Установка JAR

```
Jira → Manage Apps → Upload app → target/structure-dashboard-plugin-1.0.0.jar
```

## TODO / известные ограничения

- **Кэширование** не реализовано → добавить Atlassian Cache API с TTL ~5 мин
- **Пагинация** — `getUnlimitedFilter()` не подходит для структур >2000 задач
- **Экспорт Excel** — не реализован (добавить Apache POI)
- **Drag & drop** — сохранение порядка только через кнопку "Save layout"
- **Аутентификация Structure** — cookie forwarding; если Structure на другом домене — нужен токен
