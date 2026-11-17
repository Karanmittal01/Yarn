import re, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from brands import NEW, ALIASES
src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "core", "intelligence", "src", "main", "kotlin", "app", "yarn", "intelligence", "SenderNames.kt")).read()
START = "        // Brands added for logos (longest prefixes first); they name the sender but add no category hint.\n"
END = "        // The original list, each with the category its messages usually are.\n"
head, rest = src.split(START)
block, tail = rest.split(END)
existing = re.findall(r'Brand\("([A-Z0-9]+)", "([^"]+)"', tail)
new = []
for name, domain, prefixes, *_ in NEW:
    for p in dict.fromkeys(prefixes):
        new.append((p, name))
for name, prefixes in ALIASES:
    for p in prefixes:
        new.append((p, name))
# Longer prefixes first so a specific one is never shadowed by a shorter one.
new.sort(key=lambda x: -len(x[0]))
order = new + existing
problems = []
for i, (p, n) in enumerate(order):
    for q, m in order[i + 1:]:
        if m != n and q.startswith(p):
            problems.append(f"{p}({n}) shadows {q}({m})")
dups = {}
for p, n in new + existing:
    dups.setdefault(p, set()).add(n)
problems += [f"duplicate {p}: {ns}" for p, ns in dups.items() if len(ns) > 1]
print("\n".join(problems) or "no conflicts", file=sys.stderr)
lines = [f'        Brand("{p}", "{n}"),' for p, n in new]
if problems:
    sys.exit(1)
path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "core", "intelligence", "src", "main", "kotlin", "app", "yarn", "intelligence", "SenderNames.kt")
open(path, "w").write(head + START + "\n".join(lines) + "\n" + END + tail)
print(len(lines), "new prefixes for", len({n for _, n in new}), "brands", file=sys.stderr)
