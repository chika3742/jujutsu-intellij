---
name: jj-bookmarks
description: Reference for Jujutsu (jj) bookmarks — named pointers to revisions analogous to Git branches. Use when creating, listing, moving, deleting, renaming, or forgetting bookmarks; working with remote/tracked bookmarks and the <bookmark>@<remote> notation; or pushing/fetching bookmarks.
---

# Jujutsu Bookmarks

Bookmarks are named pointers to revisions, analogous to Git branches. Key difference: a
bookmark automatically advances when its target revision is rewritten (e.g. rebase) and is
deleted when its target is abandoned. There is **no** concept of an active/checked-out
bookmark in jj.

Full details: **https://docs.jj-vcs.dev/latest/bookmarks/**

## Core commands

- Create: `jj bookmark create BOOKMARK-NAME -r@` (shorthand `jj b c BOOKMARK-NAME -r@`)
- Move/set: `jj bookmark move` / `jj bookmark set` (used to resolve a conflicted local bookmark too)
- Delete / forget / rename: `jj bookmark delete`, `jj bookmark forget`, `jj bookmark rename`
  - For exact syntax (e.g. `--to`, `--from`, `--allow-backwards`), consult the CLI reference link — the bookmarks page does not spell these out.
- List: `jj bookmark list` (`--all`, `--tracked` / `-t`, optional `<bookmark name>` filter)

Shorthands: `jj b` = `jj bookmark`; subcommands take single-letter aliases (`jj b c` = create).

## Git mapping

When interacting with Git repos, jj translates bookmarks to/from Git branches.
`jj git push --bookmark foo` updates the remote branch; importing a Git repo creates local
bookmarks from remote branches.

## Remotes & tracking

Terminology (see doc for the precise distinctions):
- **Remote bookmark** — last-known position on a remote, addressed as `<bookmark>@<remote>` (e.g. `main@origin`); `main@git` for the colocated Git repo's branch.
- **Tracked remote bookmark** — a remote bookmark linked to a same-named local bookmark, synced on fetch.
- **Tracking local bookmark** — the local bookmark kept in sync with its remote.

Commands:
- Track: `jj bookmark track <bookmark name> --remote=<remote name>`
- Untrack: `jj bookmark untrack <bookmark name> --remote=<remote name>`
- List tracked: `jj bookmark list --tracked` / `-t`

Automatic tracking happens on `jj git clone` (default remote) and when pushing a local
bookmark. Config `remotes.<name>.auto-track-bookmarks = "*"` auto-tracks all newly fetched
bookmarks.

## Pushing — safety checks

A push is rejected unless: (1) the remote's actual position matches jj's last-recorded state
(like `git push --force-with-lease`); (2) the local bookmark has no conflict; (3) an existing
remote bookmark is tracked.

## Conflicts

A conflicted bookmark shows as `main??` in `jj log`. Resolve a local conflict with
`jj bookmark move`/`set`; resolve a remote conflict with `jj git fetch`.
