"""PreToolUse hook: block java.*/android.* imports being written into shared/src/commonMain/.

Reads the tool-call JSON from stdin. Exit 2 blocks the edit (stderr shown to the
agent); any other outcome exits 0 so a hook failure can never block normal work.
"""
import json
import re
import sys

FORBIDDEN = re.compile(r"^\s*import\s+(java\.|javax\.|android\.|androidx\.)", re.MULTILINE)

try:
    payload = json.load(sys.stdin)
    tool_input = payload.get("tool_input", {})
    file_path = (tool_input.get("file_path") or "").replace("\\", "/")
    if "shared/src/commonMain/" not in file_path:
        sys.exit(0)
    content = tool_input.get("new_string") or tool_input.get("content") or ""
    match = FORBIDDEN.search(content)
    if match:
        sys.stderr.write(
            "KMP boundary violation: '%s...' must not be imported in shared/src/commonMain/ "
            "(platform-independent code only — use kotlinx.* equivalents, e.g. kotlinx.datetime). "
            "Put platform code in androidMain/ or desktopMain/ instead."
            % match.group(0).strip()[:60]
        )
        sys.exit(2)
    sys.exit(0)
except SystemExit:
    raise
except Exception:
    sys.exit(0)
