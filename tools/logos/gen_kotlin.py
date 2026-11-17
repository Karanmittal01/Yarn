import re, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from brands import NEW
src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "core", "intelligence", "src", "main", "kotlin", "app", "yarn", "intelligence", "SenderNames.kt")).read()
existing = re.findall(r'Brand\(Regex\("\^([A-Z0-9]+)"\), "([^"]+)"', src)
new = []
for name, domain, prefixes, *_ in NEW:
    for p in dict.fromkeys(prefixes):
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
lines = [f'        Brand(Regex("^{p}"), "{n}"),' for p, n in new]
open("kotlin_new.txt", "w").write("\n".join(lines) + "\n")
print(len(lines), "new prefixes for", len({n for _, n in new}), "brands", file=sys.stderr)
