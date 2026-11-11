#!/usr/bin/env bash
# verify-stage.sh — Cổng kiểm tra tự động giữa các stage.
#
# Dùng:
#   scripts/verify-stage.sh <stage> [--e2e] [--update-baseline] [--skip-review-check]
#
# Mã thoát 0 = qua cổng. Khác 0 = chưa qua.
# Giả định cấu trúc repo: backend/ (Gradle), frontend/ (npm), e2e/ (Playwright, tuỳ chọn),
# docs/test-cases/stage-N.md, docs/reviews/stage-N.md.
# Frontend cần các script npm: lint, typecheck, test (chạy Vitest một lần, không watch).

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

STAGE="${1:-}"
if [[ -z "$STAGE" || ! "$STAGE" =~ ^[0-9]+$ ]]; then
  echo "Usage: $0 <stage-number> [--e2e] [--update-baseline] [--skip-review-check]" >&2
  exit 2
fi
shift

RUN_E2E=false
UPDATE_BASELINE=false
CHECK_REVIEW=true
for arg in "$@"; do
  case "$arg" in
    --e2e) RUN_E2E=true ;;
    --update-baseline) UPDATE_BASELINE=true ;;
    --skip-review-check) CHECK_REVIEW=false ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

FAILED=0
step() { echo; echo "==> $*"; }
fail() { echo "FAIL: $*" >&2; FAILED=1; }

BASELINE_FILE=".test-baseline"

# ---------------------------------------------------------------------------
# 1. Tài liệu của stage
# ---------------------------------------------------------------------------
step "Kiểm tra tài liệu của stage $STAGE"

TESTCASE_FILE="docs/test-cases/stage-$STAGE.md"
REVIEW_FILE="docs/reviews/stage-$STAGE.md"

[[ -f "$TESTCASE_FILE" ]] || fail "Thiếu $TESTCASE_FILE"

if $CHECK_REVIEW; then
  if [[ -f "$REVIEW_FILE" ]]; then
    OPEN_ISSUES="$(grep -nE '^\s*-\s*\[(BLOCKER|MAJOR)\]\[OPEN\]' "$REVIEW_FILE" || true)"
    if [[ -n "$OPEN_ISSUES" ]]; then
      echo "$OPEN_ISSUES" >&2
      fail "Còn lỗi BLOCKER/MAJOR ở trạng thái OPEN trong $REVIEW_FILE"
    fi
  else
    fail "Thiếu $REVIEW_FILE (bỏ qua bằng --skip-review-check khi đang làm dở)"
  fi
fi

# ---------------------------------------------------------------------------
# 2. Test bị tắt hoặc bị khoá
# ---------------------------------------------------------------------------
step "Tìm test bị tắt hoặc bị khoá"

DISABLED_PATTERN='@Disabled|@Ignore|\b(it|test|describe)\.(skip|only)\b|\bxit\(|\bxdescribe\(|\bxtest\('
FOUND_DISABLED=""

if [[ -d backend/src/test ]]; then
  FOUND_DISABLED+="$(grep -rnE "$DISABLED_PATTERN" backend/src/test --include='*.java' || true)"
fi
if [[ -d frontend/src ]]; then
  FOUND_DISABLED+="$(grep -rnE "$DISABLED_PATTERN" frontend/src --include='*.test.ts' --include='*.spec.ts' || true)"
fi
if [[ -d e2e ]]; then
  FOUND_DISABLED+="$(grep -rnE "$DISABLED_PATTERN" e2e --include='*.ts' --exclude-dir=node_modules || true)"
fi

if [[ -n "${FOUND_DISABLED//[[:space:]]/}" ]]; then
  echo "$FOUND_DISABLED" >&2
  fail "Có test bị tắt/khoá. Không được tắt test để cho pass."
fi

# ---------------------------------------------------------------------------
# 3. Backend: unit + integration + phân quyền + cấu trúc module
# ---------------------------------------------------------------------------
step "Backend: gradlew check"

if [[ -d backend ]]; then
  if ! (cd backend && ./gradlew --console=plain check); then
    fail "Backend: gradlew check không qua"
  fi
else
  fail "Không thấy thư mục backend/"
fi

# ---------------------------------------------------------------------------
# 4. Frontend: lint + typecheck + unit
# ---------------------------------------------------------------------------
step "Frontend: lint, typecheck, test"

if [[ -d frontend ]]; then
  if ! (cd frontend && npm ci && npm run lint && npm run typecheck && npm run test); then
    fail "Frontend: lint/typecheck/test không qua"
  fi
else
  fail "Không thấy thư mục frontend/"
fi

# ---------------------------------------------------------------------------
# 5. E2E (tuỳ chọn)
# ---------------------------------------------------------------------------
if $RUN_E2E; then
  step "E2E: Playwright"
  if [[ -d e2e ]]; then
    if ! (cd e2e && npm ci && npx playwright test); then
      fail "E2E không qua"
    fi
  else
    fail "Không thấy thư mục e2e/"
  fi
fi

# ---------------------------------------------------------------------------
# 6. Số test không được giảm so với baseline
# ---------------------------------------------------------------------------
step "Kiểm tra số test không giảm"

count_backend() {
  [[ -d backend/src/test ]] || { echo 0; return; }
  grep -rhE '@(Test|ParameterizedTest|RepeatedTest)\b' backend/src/test --include='*.java' | wc -l | tr -d ' '
}
count_frontend() {
  [[ -d frontend/src ]] || { echo 0; return; }
  grep -rhE '\b(it|test)(\.each\([^)]*\))?\(' frontend/src --include='*.test.ts' --include='*.spec.ts' | wc -l | tr -d ' '
}
count_e2e() {
  [[ -d e2e ]] || { echo 0; return; }
  grep -rhE '\btest(\.each\([^)]*\))?\(' e2e --include='*.ts' --exclude-dir=node_modules | wc -l | tr -d ' '
}

CUR_BACKEND="$(count_backend)"
CUR_FRONTEND="$(count_frontend)"
CUR_E2E="$(count_e2e)"
echo "Hiện tại: backend=$CUR_BACKEND frontend=$CUR_FRONTEND e2e=$CUR_E2E"

if [[ -f "$BASELINE_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$BASELINE_FILE"
  BASE_BACKEND="${backend:-0}"
  BASE_FRONTEND="${frontend:-0}"
  BASE_E2E="${e2e:-0}"
  echo "Baseline: backend=$BASE_BACKEND frontend=$BASE_FRONTEND e2e=$BASE_E2E"
  (( CUR_BACKEND  < BASE_BACKEND  )) && fail "Số test backend giảm ($CUR_BACKEND < $BASE_BACKEND)"
  (( CUR_FRONTEND < BASE_FRONTEND )) && fail "Số test frontend giảm ($CUR_FRONTEND < $BASE_FRONTEND)"
  (( CUR_E2E      < BASE_E2E      )) && fail "Số test e2e giảm ($CUR_E2E < $BASE_E2E)"
else
  echo "Chưa có $BASELINE_FILE (lần đầu). Dùng --update-baseline khi qua cổng để tạo."
fi

# ---------------------------------------------------------------------------
# Kết quả
# ---------------------------------------------------------------------------
echo
if (( FAILED != 0 )); then
  echo "=== STAGE $STAGE: CHƯA QUA CỔNG ===" >&2
  exit 1
fi

echo "=== STAGE $STAGE: QUA CỔNG TỰ ĐỘNG ==="

if $UPDATE_BASELINE; then
  {
    echo "backend=$CUR_BACKEND"
    echo "frontend=$CUR_FRONTEND"
    echo "e2e=$CUR_E2E"
  } > "$BASELINE_FILE"
  echo "Đã cập nhật $BASELINE_FILE"
fi

echo "Nhắc: cổng tự động không thay thế test thủ công và review của chủ dự án."
exit 0
