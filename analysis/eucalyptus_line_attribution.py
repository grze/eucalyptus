#!/usr/bin/env python3
"""Count line ownership for a Git snapshot using incremental blame."""

from __future__ import annotations

import argparse
import collections
import concurrent.futures
import csv
import datetime as dt
import json
import os
import pathlib
import re
import subprocess
from dataclasses import dataclass

HEADER_RE = re.compile(r"^([0-9a-f]{40})\s+(\d+)\s+(\d+)\s+(\d+)$")
TARGET_TOKENS = ("grze", "decker", "chris", "root")
PROJECT_EXCLUDE_PARTS = {
    ".git", ".idea", ".settings", "node_modules", "vendor", "vendors",
    "third_party", "third-party", "external", "externals", "generated",
    "gen", "build", "dist", "target", "out", "coverage",
}
PROJECT_EXCLUDE_PREFIXES = (
    "console/static/lib/",
    "console/static/js/lib/",
    "clc/modules/www/src/main/webapp/lib/",
    "clc/modules/www/src/main/webapp/js/lib/",
)


@dataclass(frozen=True)
class Identity:
    name: str
    email: str

    @property
    def label(self) -> str:
        if self.email:
            return f"{self.name} <{self.email}>" if self.name else f"<{self.email}>"
        return self.name or "(unknown)"

    @property
    def target_match(self) -> bool:
        haystack = f"{self.name}\n{self.email}".lower()
        return any(token in haystack for token in TARGET_TOKENS)


@dataclass
class FileResult:
    path: str
    lines_by_identity: collections.Counter[Identity]
    error: str | None = None

    @property
    def total_lines(self) -> int:
        return sum(self.lines_by_identity.values())


def run(repo: pathlib.Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        ["git", "-C", str(repo), *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=check,
    )


def tracked_files(repo: pathlib.Path, commit: str) -> list[str]:
    proc = run(repo, "ls-tree", "-r", "-z", "--name-only", commit)
    return [p.decode("utf-8", "surrogateescape") for p in proc.stdout.split(b"\0") if p]


def is_text_blob(repo: pathlib.Path, commit: str, path: str) -> bool:
    proc = run(repo, "show", f"{commit}:{path}", check=False)
    if proc.returncode != 0:
        return False
    data = proc.stdout
    if not data:
        return True
    return b"\0" not in data[:8000]


def parse_incremental_blame(output: bytes, path: str) -> FileResult:
    text = output.decode("utf-8", "replace")
    commit_identity: dict[str, Identity] = {}
    counts: collections.Counter[Identity] = collections.Counter()
    current_sha: str | None = None
    current_count = 0
    current_name: str | None = None
    current_email: str | None = None

    for line in text.splitlines():
        match = HEADER_RE.match(line)
        if match:
            current_sha = match.group(1)
            current_count = int(match.group(4))
            current_name = None
            current_email = None
            continue
        if current_sha is None:
            continue
        if line.startswith("author "):
            current_name = line[7:]
        elif line.startswith("author-mail "):
            value = line[12:].strip()
            if value.startswith("<") and value.endswith(">"):
                value = value[1:-1]
            current_email = value
        elif line.startswith("filename "):
            identity = commit_identity.get(current_sha)
            if current_name is not None or current_email is not None:
                identity = Identity(current_name or "", current_email or "")
                commit_identity[current_sha] = identity
            if identity is None:
                identity = Identity("(unknown)", "")
            counts[identity] += current_count
            current_sha = None
            current_count = 0
            current_name = None
            current_email = None
    return FileResult(path=path, lines_by_identity=counts)


def blame_file(repo: pathlib.Path, commit: str, path: str) -> FileResult:
    if not is_text_blob(repo, commit, path):
        return FileResult(path=path, lines_by_identity=collections.Counter())
    proc = run(repo, "blame", "--incremental", commit, "--", path, check=False)
    if proc.returncode != 0:
        return FileResult(
            path=path,
            lines_by_identity=collections.Counter(),
            error=proc.stderr.decode("utf-8", "replace").strip(),
        )
    return parse_incremental_blame(proc.stdout, path)


def project_path(path: str) -> bool:
    normalized = path.replace("\\", "/")
    lower = normalized.lower()
    if any(lower.startswith(prefix) for prefix in PROJECT_EXCLUDE_PREFIXES):
        return False
    parts = {part.lower() for part in pathlib.PurePosixPath(normalized).parts}
    if parts & PROJECT_EXCLUDE_PARTS:
        return False
    excluded_suffixes = {
        ".jar", ".war", ".ear", ".class", ".o", ".a", ".so", ".dll",
        ".exe", ".zip", ".gz", ".tgz", ".bz2", ".xz", ".pdf", ".png",
        ".jpg", ".jpeg", ".gif", ".ico", ".woff", ".woff2", ".ttf",
    }
    return pathlib.PurePosixPath(lower).suffix not in excluded_suffixes


def write_distribution(path: pathlib.Path, counts: collections.Counter[Identity], total: int) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    ordered = sorted(counts.items(), key=lambda item: (-item[1], item[0].label.lower()))
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["rank", "author_name", "author_email", "identity", "lines", "percent", "target_match"],
        )
        writer.writeheader()
        for rank, (identity, lines) in enumerate(ordered, 1):
            row = {
                "rank": rank,
                "author_name": identity.name,
                "author_email": identity.email,
                "identity": identity.label,
                "lines": lines,
                "percent": (100.0 * lines / total) if total else 0.0,
                "target_match": identity.target_match,
            }
            rows.append(row)
            writer.writerow({**row, "percent": f"{row['percent']:.8f}"})
    return rows


def top_level(path: str) -> str:
    parts = pathlib.PurePosixPath(path).parts
    return parts[0] if parts else "(root)"


def summary_section(title: str, rows: list[dict[str, object]], counts: collections.Counter[Identity]) -> list[str]:
    total = sum(counts.values())
    target_lines = sum(lines for ident, lines in counts.items() if ident.target_match)
    target_percent = 100.0 * target_lines / total if total else 0.0
    aggregate_rank = 1 + sum(1 for _, lines in counts.items() if lines > target_lines)
    output = [
        f"## {title}", "",
        f"- Total attributed lines: **{total:,}**",
        f"- Matching aggregate (`grze|decker|chris|root`, case-insensitive): **{target_lines:,}** ({target_percent:.4f}%)",
        f"- All other identities: **{total - target_lines:,}** ({100.0 - target_percent:.4f}%)",
        f"- Aggregate rank-equivalent among {len(counts):,} exact identities: **{aggregate_rank}**",
        "", "### Matching identities", "",
        "| Exact identity | Lines | Share of snapshot |", "|---|---:|---:|",
    ]
    matched = [r for r in rows if bool(r["target_match"])]
    for row in matched:
        output.append(f"| {row['identity']} | {int(row['lines']):,} | {float(row['percent']):.4f}% |")
    if not matched:
        output.append("| *(none)* | 0 | 0.0000% |")
    output += [
        "", "### Leading exact identities", "",
        "| Rank | Exact identity | Lines | Share | Target match |",
        "|---:|---|---:|---:|:---:|",
    ]
    for row in rows[:30]:
        output.append(
            f"| {int(row['rank'])} | {row['identity']} | {int(row['lines']):,} | {float(row['percent']):.4f}% | {'yes' if row['target_match'] else ''} |"
        )
    output.append("")
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True, type=pathlib.Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--out", required=True, type=pathlib.Path)
    parser.add_argument("--workers", type=int, default=max(2, min(8, os.cpu_count() or 2)))
    args = parser.parse_args()
    repo = args.repo.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)

    commit = run(repo, "rev-parse", f"{args.commit}^{{commit}}").stdout.decode().strip()
    commit_date = run(repo, "show", "-s", "--format=%cI", commit).stdout.decode().strip()
    files = tracked_files(repo, commit)
    results: list[FileResult] = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {executor.submit(blame_file, repo, commit, path): path for path in files}
        for completed, future in enumerate(concurrent.futures.as_completed(futures), 1):
            path = futures[future]
            try:
                result = future.result()
            except Exception as exc:
                result = FileResult(path=path, lines_by_identity=collections.Counter(), error=repr(exc))
            results.append(result)
            if completed % 250 == 0 or completed == len(files):
                print(f"processed {completed}/{len(files)} files", flush=True)

    all_counts: collections.Counter[Identity] = collections.Counter()
    project_counts: collections.Counter[Identity] = collections.Counter()
    per_top_all: collections.Counter[str] = collections.Counter()
    per_top_target: collections.Counter[str] = collections.Counter()
    errors: list[dict[str, str]] = []
    text_files = 0

    for result in results:
        if result.error:
            errors.append({"path": result.path, "error": result.error})
            continue
        if result.total_lines == 0:
            continue
        text_files += 1
        all_counts.update(result.lines_by_identity)
        top = top_level(result.path)
        per_top_all[top] += result.total_lines
        per_top_target[top] += sum(lines for identity, lines in result.lines_by_identity.items() if identity.target_match)
        if project_path(result.path):
            project_counts.update(result.lines_by_identity)

    all_total = sum(all_counts.values())
    project_total = sum(project_counts.values())
    all_rows = write_distribution(out / "distribution_all_tracked_text.csv", all_counts, all_total)
    project_rows = write_distribution(out / "distribution_project_text.csv", project_counts, project_total)

    target_by_top: list[dict[str, object]] = []
    for path, all_lines in sorted(per_top_all.items(), key=lambda item: (-per_top_target[item[0]], -item[1], item[0])):
        target_lines = per_top_target[path]
        target_by_top.append({
            "path": path,
            "target_lines": target_lines,
            "all_lines": all_lines,
            "target_percent": 100.0 * target_lines / all_lines if all_lines else 0.0,
        })
    with (out / "target_by_top_level.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["path", "target_lines", "all_lines", "target_percent"])
        writer.writeheader()
        for row in target_by_top:
            writer.writerow({**row, "target_percent": f"{float(row['target_percent']):.8f}"})

    metadata = {
        "generated_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "repository": "eucalyptus/eucalyptus",
        "requested_cutoff": "2014-11-30T23:59:59Z",
        "snapshot_commit": commit,
        "snapshot_commit_timestamp": commit_date,
        "tracked_files": len(files),
        "text_files_analyzed": text_files,
        "binary_or_empty_files_skipped": len(files) - text_files - len(errors),
        "blame_errors": errors,
        "workers": args.workers,
        "target_tokens": list(TARGET_TOKENS),
        "all_tracked_text_total_lines": all_total,
        "project_text_total_lines": project_total,
    }
    (out / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")

    output = [
        "# Eucalyptus line attribution — November 2014", "",
        f"- Snapshot commit: `{commit}`",
        f"- Snapshot commit timestamp: `{commit_date}`",
        "- Attribution: `git blame --incremental` at the snapshot commit.",
        "- Identity: exact Git author name plus author email from blame.",
        "- Target match: case-insensitive substring match against author name or email for `grze`, `decker`, `chris`, or `root`.",
        f"- Tracked files: **{len(files):,}**; text files analyzed: **{text_files:,}**; binary or empty files skipped: **{metadata['binary_or_empty_files_skipped']:,}**; blame errors: **{len(errors):,}**.",
        "",
    ]
    output += summary_section("All tracked text", all_rows, all_counts)
    output += summary_section("Project-text view", project_rows, project_counts)
    output += [
        "## Matching aggregate by top-level path — all tracked text", "",
        "| Path | Matching lines | All lines | Matching share within path |",
        "|---|---:|---:|---:|",
    ]
    for row in target_by_top:
        output.append(f"| {row['path']} | {int(row['target_lines']):,} | {int(row['all_lines']):,} | {float(row['target_percent']):.4f}% |")
    output += [
        "", "## Interpretation boundary", "",
        "This result measures lines present in the November 2014 tree and assigns them to the Git blame author. It does not measure gross additions over history. Imported or squashed Bazaar history can place multiple historical changes inside one Git commit; Git blame can only use the authorship encoded in Git objects.", "",
    ]
    if errors:
        output += ["## Blame errors", "", "```json", json.dumps(errors, indent=2), "```", ""]
    summary = "\n".join(output)
    (out / "SUMMARY.md").write_text(summary, encoding="utf-8")
    print(summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
