#!/usr/bin/env bash
# ============================================================
# build.sh — сборка Structure Analytics Dashboard Plugin
# Запускать из корня проекта: ./build.sh
# ============================================================

set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[BUILD]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1"; }

# ── 1. Проверка Java ──────────────────────────────────────────
log "Checking Java..."
if ! command -v java &>/dev/null; then
  err "Java не найден. Установи JDK 11+"; exit 1
fi
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ "$JAVA_VER" -lt 11 ] 2>/dev/null; then
  err "Требуется Java 11+, найдена $JAVA_VER"; exit 1
fi
log "Java OK: $(java -version 2>&1 | head -1)"

# ── 2. Установка Atlassian Plugin SDK (если не установлен) ────
if ! command -v atlas-package &>/dev/null; then
  warn "Atlassian Plugin SDK не найден. Устанавливаем..."

  SDK_URL="https://marketplace.atlassian.com/download/plugins/atlassian-plugin-sdk-tgz"
  SDK_DIR="$HOME/atlassian-plugin-sdk"

  if [ ! -d "$SDK_DIR" ]; then
    log "Скачиваем SDK..."
    curl -L "$SDK_URL" -o /tmp/atlassian-sdk.tar.gz
    mkdir -p "$SDK_DIR"
    tar -xzf /tmp/atlassian-sdk.tar.gz -C "$SDK_DIR" --strip-components=1
    rm /tmp/atlassian-sdk.tar.gz
  fi

  export PATH="$SDK_DIR/bin:$PATH"
  log "SDK установлен: $SDK_DIR"
fi

log "atlas-package: $(which atlas-package)"

# ── 3. Скачивание JS-библиотек ────────────────────────────────
JS_DIR="src/main/resources/js"

if [ ! -f "$JS_DIR/echarts.min.js" ]; then
  log "Скачиваем ECharts 5.4.3..."
  curl -L "https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js" \
       -o "$JS_DIR/echarts.min.js"
  log "ECharts OK ($(du -sh $JS_DIR/echarts.min.js | cut -f1))"
else
  log "ECharts уже есть: $JS_DIR/echarts.min.js"
fi

if [ ! -f "$JS_DIR/sortable.min.js" ]; then
  log "Скачиваем Sortable.js 1.15.0..."
  curl -L "https://cdn.jsdelivr.net/npm/sortablejs@1.15.0/Sortable.min.js" \
       -o "$JS_DIR/sortable.min.js"
  log "Sortable.js OK ($(du -sh $JS_DIR/sortable.min.js | cut -f1))"
else
  log "Sortable.js уже есть: $JS_DIR/sortable.min.js"
fi

# ── 4. Сборка ─────────────────────────────────────────────────
log "Запускаем atlas-package..."
atlas-package -DskipTests 2>&1

# ── 5. Результат ──────────────────────────────────────────────
JAR=$(find target -name "*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1)

if [ -z "$JAR" ]; then
  err "JAR не найден в target/. Проверь вывод выше."; exit 1
fi

SIZE=$(du -sh "$JAR" | cut -f1)
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  ✅  BUILD SUCCESS                                ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  JAR:  ${GREEN}$(pwd)/$JAR${NC}"
echo -e "  Size: $SIZE"
echo ""
echo -e "Установка в Jira:"
echo -e "  1. Jira → Manage Apps → Upload app"
echo -e "  2. Выбрать: ${GREEN}$JAR${NC}"
echo -e "  3. Открыть: ${GREEN}https://your-jira/plugins/servlet/structure-dashboard${NC}"
echo ""
