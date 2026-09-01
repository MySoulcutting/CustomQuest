#!/usr/bin/env bash
set -euo pipefail

current_tag="${1:?Current release tag is required}"
output_path="${2:-release-notes.md}"
repository="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
repository_url="${GITHUB_SERVER_URL:-https://github.com}/${repository}"

git rev-parse --verify "${current_tag}^{commit}" >/dev/null

previous_tag="$(git describe --tags --abbrev=0 "${current_tag}^{commit}^" 2>/dev/null || true)"
if [[ -n "$previous_tag" ]]; then
    range="${previous_tag}..${current_tag}"
else
    range="$current_tag"
fi

feature_lines=()
fix_lines=()
other_lines=()
while IFS=$'\t' read -r full_sha short_sha subject; do
    [[ -n "$full_sha" ]] || continue
    line="- ${subject} ([${short_sha}](${repository_url}/commit/${full_sha}))"
    case "$subject" in
        feat*|Feat*|FEAT*|新增*|添加*|实现*)
            feature_lines+=("$line")
            ;;
        fix*|Fix*|FIX*|修复*)
            fix_lines+=("$line")
            ;;
        *)
            other_lines+=("$line")
            ;;
    esac
done < <(git log --no-merges --pretty=tformat:'%H%x09%h%x09%s' "$range")

{
    echo "## 更新记录"
    echo
    if ((${#feature_lines[@]} > 0)); then
        echo "### 新增功能"
        printf '%s\n' "${feature_lines[@]}"
        echo
    fi
    if ((${#fix_lines[@]} > 0)); then
        echo "### 问题修复"
        printf '%s\n' "${fix_lines[@]}"
        echo
    fi
    if ((${#other_lines[@]} > 0)); then
        echo "### 其他变动"
        printf '%s\n' "${other_lines[@]}"
        echo
    fi
    if ((${#feature_lines[@]} == 0 && ${#fix_lines[@]} == 0 && ${#other_lines[@]} == 0)); then
        echo "- 本版本没有检测到非合并提交。"
        echo
    fi
    if [[ -n "$previous_tag" ]]; then
        echo "**完整变动**: [${previous_tag}...${current_tag}](${repository_url}/compare/${previous_tag}...${current_tag})"
    else
        echo "**完整记录**: [${current_tag}](${repository_url}/commits/${current_tag})"
    fi
} > "$output_path"
