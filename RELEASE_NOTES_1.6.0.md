# MvnBuilder v1.6.0

This release makes large Maven workspaces more reliable to navigate and refresh.

## Highlights

- **Stable project selection:** Background Git branch updates now change only branch labels. Project rows and checkboxes stay in place, so a selection is not lost while you click.
- **Accurate Refresh progress:** Refresh shows separate project scan and Git update phases. The loading indicator remains visible until both phases finish, including when a workspace has many modules. Errors are reported and Refresh can be retried.
- **Consistent bulk actions:** Selected projects, the selection count, and Select All stay in sync when an explicit Refresh changes the project list. Removed projects are dropped from the selection.
- **Less work for large workspaces:** Project hierarchy and child indicators are calculated together, and excluded directories are skipped during POM discovery. Automatic Git updates send a small batch of branch data instead of rebuilding the project table.

## Other changes

- The normal form submission remains available if HTMX is unavailable.
- The README development credits have been updated.

**Full changelog:** [v1.5.0...v1.6.0](https://github.com/jonolabarrieta/maven-builder-web-ui/compare/v1.5.0...v1.6.0)
