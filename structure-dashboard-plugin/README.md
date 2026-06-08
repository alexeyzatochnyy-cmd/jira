# Structure Analytics Dashboard Plugin

**Jira Server 8.20.x + Structure 9.3.x (Tempo)**

## Сборка и установка

```bash
# 1. Установить Atlassian Plugin SDK
# https://developer.atlassian.com/server/framework/atlassian-sdk/install-the-atlassian-sdk-on-a-linux-or-mac-system/

# 2. Собрать плагин
cd structure-dashboard-plugin
atlas-package -DskipTests

# 3. Установить JAR через Jira UPM
# Jira → Manage Apps → Upload app → выбрать target/*.jar

# 4. Открыть дашборд
# В топ-навигации появится: "Structure Analytics"
# Или прямая ссылка: https://your-jira/plugins/servlet/structure-dashboard
```

## Локальная разработка

```bash
# Запустить Jira с плагином (порт 2990)
atlas-run

# Quick reload при изменении JS/CSS (без перезапуска)
atlas-run --product jira --jvmargs "-Dplugin.resource.directories=$(pwd)/src/main/resources"
```

## Как найти ключи колонок Structure

Ключи колонок (`columnKeys`) — это идентификаторы колонок в Structure.

**Способ 1 — через URL:**
1. Откройте структуру в Jira
2. Нажмите ✎ Edit Columns
3. В URL страницы настройки колонки есть параметр `key=...`

**Способ 2 — через REST API:**
```
GET /rest/structure/2.0/column
```
Возвращает все доступные колонки с их ключами.

**Способ 3 — стандартные колонки Structure:**

| Ключ | Описание |
|------|----------|
| `progress` | % выполнения (0-100) |
| `story_points` | Story Points |
| `story_points_sum` | Сумма SP по иерархии |
| `time_original_estimate` | Original Estimate (секунды) |
| `time_spent` | Time Spent (секунды) |
| `time_remaining` | Time Remaining |
| `issue_count` | Кол-во задач |
| `subtask_count` | Кол-во подзадач |
| `resolved_count` | Кол-во завершённых |

**Формульные колонки (кастомные):**
Если у тебя есть Formula columns в Structure, их ключ выглядит как `formula_XXXXX`
или как имя, которое ты дал колонке при создании.

## Типы виджетов

| Тип | Описание | Рекомендуемые columnKeys |
|-----|----------|--------------------------|
| `kpi` | KPI-карточка с одним числом | `progress`, `story_points_sum` |
| `progress-ring` | Кольцо прогресса | `progress` |
| `epic-progress` | Прогресс-бары по эпикам | `progress,story_points` |
| `aggregates-bar` | Bar chart по формулам | `story_points_sum,time_spent` |
| `donut` | Donut chart распределения | любые числовые |
| `table` | Таблица задач с колонками | любые |

## Архитектура

```
Browser
  ↓ GET /plugins/servlet/structure-dashboard
DashboardServlet (Velocity)
  → dashboard.vm (HTML shell)
    → dashboard.js (SPA)
      ↓ REST calls
DashboardResource (/rest/structuredash/1.0/)
  ├── /structures              → StructureApiService → Structure REST API
  ├── /structure/{id}/aggregates
  ├── /structure/{id}/epic-progress
  └── /dashboards/**           → Active Objects (layout persistence)
```

## Расширение: добавить новый тип виджета

1. В `dashboard.js` добавь case в `switch (widget.type)` → новая функция `renderXxx(body, widget, data)`
2. В модальном окне добавь `sd-type-card` с `data-type="xxx"`
3. При необходимости — новый REST endpoint в `DashboardResource`

## Troubleshooting

**Structure API возвращает 401:**
- Проверь, что пользователь авторизован в Jira
- Плагин использует сессионные куки — аутентификация прозрачная

**Пустые данные в виджете:**
- Проверь ключи колонок через `/rest/structure/2.0/column`
- Убедись, что структура содержит задачи с заполненными полями
- Для формульных колонок — нужна версия Structure 5.5+

**ECharts не отображается:**
- Убедись, что `echarts.min.js` доступен по URL из web-resource
- Скачай с: https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js
- Положи в: `src/main/resources/js/echarts.min.js`

**Sortable.js не работает:**
- Скачай: https://cdn.jsdelivr.net/npm/sortablejs@1.15.0/Sortable.min.js  
- Положи в: `src/main/resources/js/sortable.min.js`
