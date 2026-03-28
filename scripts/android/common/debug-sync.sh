#!/usr/bin/env bash

read_prop() {
    local props_file="$1"
    local key="$2"
    awk -F'=' -v k="$key" '
        $0 !~ /^[[:space:]]*#/ {
            name=$1
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", name)
            if (name == k) {
                val=$0
                sub(/^[^=]*=/, "", val)
                gsub(/^[[:space:]]+|[[:space:]]+$/, "", val)
                print val
            }
        }
    ' "$props_file" | tail -1
}

bump_patch_version() {
    local version="$1"
    if [[ ! "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
        echo "Error: APP_VERSION_NAME must use semantic version format x.y.z. Found: $version" >&2
        return 1
    fi

    local major="${BASH_REMATCH[1]}"
    local minor="${BASH_REMATCH[2]}"
    local patch="${BASH_REMATCH[3]}"
    echo "${major}.${minor}.$((patch + 1))"
}

resolve_debug_version_from_props() {
    local props_file="$1"

    local release_version_name
    local release_version_code
    release_version_name="$(read_prop "$props_file" APP_VERSION_NAME)"
    release_version_code="$(read_prop "$props_file" APP_VERSION_CODE)"

    if [[ -z "$release_version_name" || -z "$release_version_code" ]]; then
        echo "Error: APP_VERSION_NAME and APP_VERSION_CODE must be set in $props_file" >&2
        return 1
    fi

    local debug_version_name
    debug_version_name="$(bump_patch_version "$release_version_name")" || return 1
    local debug_version_code
    debug_version_code="$((release_version_code + 1))"

    printf '%s\n' "$release_version_name"
    printf '%s\n' "$release_version_code"
    printf '%s\n' "$debug_version_name"
    printf '%s\n' "$debug_version_code"
}