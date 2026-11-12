"""Turns target/reports/run-summary.json into a GitHub job summary and one warning annotation per healed locator.

Usage: python3 job_summary.py <run-summary.json> <suite name>
Writes Markdown to $GITHUB_STEP_SUMMARY (or stdout when run locally). Never fails the job: a missing report
just produces a short note, because the test step's own exit code already decides pass or fail.
"""
import json
import os
import sys

# Scenarios with this tag rename elements on purpose, so their heals are not warnings.
EXPECTED_TAG = "@healing"


def main() -> None:
    path, suite = sys.argv[1], sys.argv[2]
    out = []
    if not os.path.exists(path):
        out.append(f"### {suite}: no run summary\n\nThe tests did not produce `{path}`; see the step log.\n")
        write(out)
        return

    with open(path, encoding="utf-8") as f:
        run = json.load(f)

    icon = "❌" if run["failed"] else "✅"
    seconds = run["durationMs"] / 1000
    out.append(f"### {icon} {suite} tests on `{run['environment']}`\n")
    out.append("| Scenarios | Passed | Passed after retry | Failed | Skipped | Healed locators | Time |")
    out.append("|---:|---:|---:|---:|---:|---:|---:|")
    out.append(f"| {run['total']} | {run['passed']} | {run.get('flaky', 0)} | {run['failed']} | {run['skipped']} "
               f"| {run['heals']} | {seconds:.1f} s |\n")

    # Passed in the end, but only on a retry: worth a look before they turn into real failures.
    flaky = [s for s in run["scenarios"] if s["status"] == "passed" and s.get("attempts", 1) > 1]
    if flaky:
        out.append("#### Passed after retry (flaky)\n")
        for s in flaky:
            out.append(f"- {cell(s['scenario'])} (`{s['location']}`, {s['attempts']} attempts)")
            print(f"::warning title=Flaky scenario::{s['scenario']} passed only after {s['attempts']} attempts")
        out.append("")

    failed = [s for s in run["scenarios"] if s["status"] not in ("passed", "skipped", "pending", "unused")]
    if failed:
        out.append("#### Failures\n")
        out.append("| Scenario | Location | Error |")
        out.append("|---|---|---|")
        for s in failed:
            out.append(f"| {cell(s['scenario'])} | `{s['location']}` | {cell(s.get('error', s['status']))} |")
        out.append(f"\nScreenshots are in **run-summary.html** (artifact `reports-{suite}`); "
                   f"Playwright traces are in artifact `traces-{suite}`.\n")

    # Scenarios tagged @healing break selectors on purpose to prove healing works: their heals are expected.
    healed = [(s, h) for s in run["scenarios"] for h in s.get("heals", [])]
    real = [(s, h) for s, h in healed if EXPECTED_TAG not in s.get("tags", [])]
    expected = [(s, h) for s, h in healed if EXPECTED_TAG in s.get("tags", [])]
    if real:
        out.append("#### ⚠️ Healed locators\n")
        out.append("The tests kept running, but these selectors no longer match the app. Update the selectors in src/main/resources/locators.\n")
        for s, h in real:
            out.append(f"- `{h}` ({cell(s['scenario'])})")
            # One annotation per heal, shown on the run page and in the PR checks.
            print(f"::warning title=Locator healed::{h.replace(chr(10), ' ')}")
        out.append("")
    if expected:
        out.append(f"<details><summary>{len(expected)} expected heal(s) from {EXPECTED_TAG} scenarios</summary>\n")
        for s, h in expected:
            out.append(f"- `{h}` ({cell(s['scenario'])})")
        out.append("\n</details>\n")

    write(out)


def cell(text: str) -> str:
    """Keeps a value on one Markdown table row."""
    text = text.replace("|", "\\|").replace("\n", " ")
    return text if len(text) <= 200 else text[:197] + "..."


def write(lines: list) -> None:
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    text = "\n".join(lines) + "\n"
    if target:
        with open(target, "a", encoding="utf-8") as f:
            f.write(text)
    else:
        sys.stdout.write(text)


if __name__ == "__main__":
    main()
