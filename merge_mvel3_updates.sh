#!/usr/bin/env bash
set -euo pipefail

# Merge the latest lexer/parser grammars and visitor methods from the mvel3 project.
# Default upstream repo location can be overridden with MVEL3_ROOT=/path/to/mvel3.

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
MVEL3_ROOT="${MVEL3_ROOT:-${REPO_ROOT}/../mvel3}"

LEX_SRC="${MVEL3_ROOT}/src/main/antlr4/org/mvel3/parser/antlr4/Mvel3Lexer.g4"
PARSER_SRC="${MVEL3_ROOT}/src/main/antlr4/org/mvel3/parser/antlr4/Mvel3Parser.g4"
VISITOR_SRC="${MVEL3_ROOT}/src/main/java/org/mvel3/parser/antlr4/Mvel3ToJavaParserVisitor.java"

LEX_DST="${REPO_ROOT}/src/main/antlr4/org/drools/drlx/parser/Mvel3Lexer.g4"
PARSER_DST="${REPO_ROOT}/src/main/antlr4/org/drools/drlx/parser/Mvel3Parser.g4"
VISITOR_DST="${REPO_ROOT}/src/main/java/org/drools/drlx/parser/DRLXToJavaParserVisitor.java"

copy_grammars() {
  cp "${LEX_SRC}" "${LEX_DST}"
  cp "${PARSER_SRC}" "${PARSER_DST}"
}

update_visitor_methods() {
  REPO_ROOT="${REPO_ROOT}" VISITOR_SRC="${VISITOR_SRC}" VISITOR_DST="${VISITOR_DST}" python - <<'PY'
from pathlib import Path
import os
import sys

repo_root = Path(os.environ["REPO_ROOT"])
visitor_src = Path(os.environ["VISITOR_SRC"])
visitor_dst = Path(os.environ["VISITOR_DST"])
marker = "//--- Below copied from Mvel3ToJavaParserVisitor in mvel3 -----"

if not visitor_src.is_file():
    sys.exit(f"Missing visitor source: {visitor_src}")
if not visitor_dst.is_file():
    sys.exit(f"Missing visitor destination: {visitor_dst}")

# Read destination and source
dst_lines = visitor_dst.read_text().splitlines()
src_lines = visitor_src.read_text().splitlines()

# Sync imports: add any imports from src not present in dst
dst_imports = [ln for ln in dst_lines if ln.lstrip().startswith("import ")]
src_imports = [ln for ln in src_lines if ln.lstrip().startswith("import ")]
dst_import_set = set(dst_imports)
missing_imports = [imp for imp in src_imports if imp not in dst_import_set]

if missing_imports:
    # Find insertion point: after last existing import, otherwise after package line
    insert_idx = None
    for i in range(len(dst_lines) - 1, -1, -1):
        if dst_lines[i].lstrip().startswith("import "):
            insert_idx = i + 1
            break
    if insert_idx is None:
        for i, line in enumerate(dst_lines):
            if line.startswith("package "):
                insert_idx = i + 1
                break
    if insert_idx is None:
        insert_idx = 0
    dst_lines[insert_idx:insert_idx] = missing_imports

# Find marker (ignore leading/trailing whitespace)
marker_idx = None
for i, line in enumerate(dst_lines):
    if marker in line:
        marker_idx = i
        break
if marker_idx is None:
    sys.exit(f"Marker not found in {visitor_dst}")

# Extract body of the mvel3 visitor (inside class, without the outer braces)
try:
    class_idx = next(i for i, line in enumerate(src_lines) if "class Mvel3ToJavaParserVisitor" in line)
except StopIteration:
    sys.exit("Cannot find class declaration in visitor source")

body_lines = src_lines[class_idx + 1 :]

# Drop trailing blanks
while body_lines and not body_lines[-1].strip():
    body_lines.pop()
# Drop final closing brace
if body_lines and body_lines[-1].strip() == "}":
    body_lines.pop()

body_text = "\n".join(body_lines)
body_text = body_text.replace("Mvel3Parser.", "DRLXParser.")

prefix = dst_lines[: marker_idx + 1]
new_content = "\n".join(prefix) + "\n" + body_text + "\n}"

visitor_dst.write_text(new_content)
PY
}

main() {
  copy_grammars
  update_visitor_methods
  echo "Merged mvel3 updates into DRLX project."
}

main "$@"
