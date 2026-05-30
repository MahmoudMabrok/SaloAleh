#!/usr/bin/env python3
"""Render an informative dashboard from the last 7 daily stats snapshots.

Reads stats/YYYY-MM-DD.json files and produces stats/last-week.png with:
  - Daily salawat (bars) + cumulative round total (line)
  - Active players & countries reached (lines)
  - Top score growth (line)
Round resets are detected via roundKey changes and annotated.
"""
import json
import glob
import os
from datetime import datetime

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

HERE = os.path.dirname(os.path.abspath(__file__))

# Load the last 7 daily snapshots (by date in filename).
files = sorted(glob.glob(os.path.join(HERE, "20??-??-??.json")))[-7:]
days = []
for f in files:
    with open(f) as fh:
        d = json.load(fh)
    d["date"] = os.path.basename(f).replace(".json", "")
    days.append(d)

labels   = [datetime.strptime(d["date"], "%Y-%m-%d").strftime("%a\n%m-%d") for d in days]
daily    = [d.get("todaySalawat", 0) for d in days]
week     = [d.get("weekSalawat", 0) for d in days]
players  = [d.get("activePlayers", 0) for d in days]
ctry     = [d.get("countriesCount", 0) for d in days]
top      = [d.get("topScore", 0) for d in days]
rounds   = [d.get("roundKey", "") for d in days]

# Index where a new round started (roundKey changed vs previous day).
reset_idx = [i for i in range(1, len(rounds)) if rounds[i] != rounds[i - 1]]

thousands = FuncFormatter(lambda x, _: f"{int(x):,}")

plt.rcParams.update({"font.size": 10, "axes.grid": True, "grid.alpha": 0.3})
fig, axes = plt.subplots(3, 1, figsize=(11, 12), sharex=True)
fig.suptitle("SaloAleh — Last 7 Days", fontsize=16, fontweight="bold")

# Panel 1: daily salawat bars + cumulative round total line
ax = axes[0]
bars = ax.bar(labels, daily, color="#2e7d32", alpha=0.85, label="Salawat that day")
ax.set_ylabel("Daily salawat")
ax.yaxis.set_major_formatter(thousands)
for b, v in zip(bars, daily):
    ax.text(b.get_x() + b.get_width() / 2, v, f"{v:,}", ha="center", va="bottom", fontsize=8)
ax2 = ax.twinx()
ax2.plot(labels, week, color="#1565c0", marker="o", lw=2, label="Round total (cumulative)")
ax2.set_ylabel("Round cumulative", color="#1565c0")
ax2.yaxis.set_major_formatter(thousands)
ax2.grid(False)
l1, lb1 = ax.get_legend_handles_labels()
l2, lb2 = ax2.get_legend_handles_labels()
ax.legend(l1 + l2, lb1 + lb2, loc="upper left", fontsize=9)
ax.set_title("Salawat — daily and cumulative", fontsize=11)

# Panel 2: active players + countries
ax = axes[1]
ax.plot(labels, players, color="#6a1b9a", marker="o", lw=2, label="Active players")
ax.set_ylabel("Active players", color="#6a1b9a")
for x, v in zip(labels, players):
    ax.text(x, v, f" {v}", va="bottom", ha="center", fontsize=8, color="#6a1b9a")
ax3 = ax.twinx()
ax3.plot(labels, ctry, color="#ef6c00", marker="s", lw=2, label="Countries reached")
ax3.set_ylabel("Countries", color="#ef6c00")
ax3.grid(False)
l1, lb1 = ax.get_legend_handles_labels()
l3, lb3 = ax3.get_legend_handles_labels()
ax.legend(l1 + l3, lb1 + lb3, loc="upper left", fontsize=9)
ax.set_title("Participation — players and reach", fontsize=11)

# Panel 3: top score
ax = axes[2]
ax.plot(labels, top, color="#c62828", marker="D", lw=2, label="Top player score")
ax.fill_between(range(len(labels)), top, color="#c62828", alpha=0.12)
ax.set_ylabel("Top score")
ax.yaxis.set_major_formatter(thousands)
for x, v in zip(labels, top):
    ax.text(x, v, f" {v:,}", va="bottom", ha="center", fontsize=8)
ax.legend(loc="upper left", fontsize=9)
ax.set_title("Leader's score", fontsize=11)

# Annotate round resets across all panels.
for i in reset_idx:
    for a in axes:
        a.axvline(i - 0.5, color="grey", ls="--", lw=1.2, alpha=0.7)
    axes[0].text(i - 0.5, max(daily) * 0.95, "  new round", color="grey",
                 fontsize=8, rotation=90, va="top")

fig.tight_layout(rect=[0, 0, 1, 0.97])
out = os.path.join(HERE, "last-week.png")
fig.savefig(out, dpi=130)
print("wrote", out)

# Print a quick text summary for the terminal.
print("\nLast 7 days summary:")
for d in days:
    print(f"  {d['date']}  daily={d.get('todaySalawat',0):>6,}  "
          f"players={d.get('activePlayers',0):>3}  "
          f"countries={d.get('countriesCount',0):>2}  "
          f"top={d.get('topScore',0):>6,}  round={d.get('roundKey','')}")
