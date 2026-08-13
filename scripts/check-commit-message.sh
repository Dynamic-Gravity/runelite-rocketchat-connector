#!/usr/bin/env bash
# Validates a commit subject against the same regex the release job in
# .gitlab-ci.yml uses to decide feat/fix/major bumps. A typo like
# "fix (Scope): ..." (space before the paren) silently matches nothing,
# so the release job just skips releasing — no error, no warning, just
# no tag. This blocks that class of typo at commit time instead.
#
# Install as a commit-msg hook:
#   ./scripts/check-commit-message.sh --install
#
# Runs automatically thereafter on every `git commit`. Bypass once with
# `git commit --no-verify` if you really need to.
set -euo pipefail

KNOWN_TYPES="feat|fix|chore|docs|style|refactor|perf|test|build|ci|revert"

install() {
	hook_dir="$(git rev-parse --git-path hooks)"
	hook_path="$hook_dir/commit-msg"
	script_path="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
	ln -sf "$script_path" "$hook_path"
	echo "Installed commit-msg hook -> $hook_path"
}

if [[ "${1:-}" == "--install" ]]; then
	install
	exit 0
fi

msg_file="${1:?usage: check-commit-message.sh <commit-msg-file>|--install}"
subject="$(head -n1 "$msg_file")"

# Merge commits and fixups aren't conventional commits, don't police them.
if [[ "$subject" =~ ^(Merge|fixup!|squash!) ]]; then
	exit 0
fi

# Matches the release job's bump detection (.gitlab-ci.yml). Stored in a
# variable - bash's [[ =~ ]] parser chokes on escaped parens written inline.
conventional_re='^[a-zA-Z]+(\([^)]*\))?!?:'
if [[ "$subject" =~ $conventional_re ]]; then
	exit 0
fi

# Looks like an attempted conventional-commit type but malformed
# (e.g. space before "(", missing colon) - this is the typo that
# breaks the release job silently. Block it.
typo_re="^($KNOWN_TYPES)[^a-zA-Z0-9]"
if [[ "$subject" =~ $typo_re ]]; then
	echo "error: commit subject looks like a conventional-commit type but doesn't match the release regex:" >&2
	echo "  $subject" >&2
	echo "expected format: type(optional-scope): description   (no space before the parens)" >&2
	echo "example: fix(LevelNotifier): show the player name instead of a generic" >&2
	exit 1
fi

exit 0
