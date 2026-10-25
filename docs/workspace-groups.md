# Parent workspaces and project groups

Use **Parent workspaces** on the home page to maintain a shared project collection.
Create a parent, choose its base folder or add projects manually, and order its
projects just as in an ordinary workspace. The parent editor lists its consumers.

In an ordinary workspace, use **Create new group** to open a dialog with every
local project. Select one or more projects and create the group. **Edit group**
in the group header opens the same dialog so more projects can be added. Groups
are visual organization only: they never change a project's execution order.

Use **Add parent workspace** to open a dialog with the parent workspaces that are
still available. The dialog also lists linked parents so they can be removed.
An imported parent is displayed as a group named after the parent workspace, so
adding it does not ask for an internal label. Removing that group removes its
projects from the child immediately; the parent workspace and files on disk are
kept. Editing the parent's membership, paths or order is reflected when consumers
are opened or refreshed.

Groups contain project roots, not other groups. Parent workspaces do not contain
groups or import other parents. Existing workspaces remain ordinary workspaces.

## Selection, order and actions

Expand or collapse a group using its disclosure button. The browser remembers
collapsed groups per workspace. Collapsing does not deselect or disable projects.
The group checkbox has a mixed state when only some members are selected. Select
All includes projects in collapsed groups. Refresh retains existing selections,
removes vanished entries and leaves newly added members unselected.

**Build group** runs eligible Maven roots in group order. Individual projects keep
their own actions, module browser, properties, M2 tools and local shortcuts.
**Group actions and settings** provides fetch, pull, checkout or branch creation,
stash (including optional untracked files), discard, unstage, enable and disable.
Use **Selected members only** to narrow the action to selected members of that group.
Git actions identify shared working-tree effects before consequential operations.
One operation targets each canonical repository root once; separate worktrees
remain separate targets.

Move groups or ungrouped projects as visual blocks; reorder members inside local
groups. These controls only change the view. Build All always follows the flat
project order (`Order` column), never the visual group order. Imported projects
receive order numbers in the child workspace after its local projects, preserving
their relative order from the parent. Because a grouped row can appear above a
project with a lower order number, duplicate or non-consecutive numbers are
expected and displayed as stored. Range builds from a grouped entry stay inside
that group. Builds use the consumer's Java installation, then the global default,
and the active build profile. Explicit non-Maven folders are retained but not
built. Unavailable targets are reported.

Enabled overrides on imported projects affect only that consumer. A group-wide
change applies to current members; future members inherit their parent's state.
Git and build commands operate on actual local directories, so filesystem or Git
changes can be visible in every workspace referencing those same directories.

Retry and skip preserve the captured pending build queue and Java configuration,
even after reordering. Removing a group/import or pending membership invalidates
its continuation. Skipping never changes persistent enabled state or makes the
original failed build successful.

Dependency ordering updates project execution order only. Visual groups are not
split, merged or reordered by dependency calculation.

## Removing and duplicating

Removing a local group asks whether its projects should stay ungrouped or be
deleted from the workspace; files on disk are always kept. Removing an imported
parent deletes its projects from the child immediately and preserves the parent.
Deleting a consumer never deletes shared parents. A parent with consumers cannot
be deleted: remove its imports first.

Duplicating a consumer copies local definitions and enabled overrides while keeping
references to the same shared parents. Duplicating a parent copies its projects
without copying its consumers.

## Portable text format

Existing plain path files and full legacy configurations still import, including
`NonMavenProject: <absolute-path>` entries. Unmarked paths without a POM are warned
and skipped. Ungrouped ordinary workspaces retain their legacy export format.

A grouped workspace or parent exports as UTF-8 text with the first line:

```text
MvnBuilderWorkspace: 2
```

The remaining content is JSON with `version: 2`, `workspace` (one Definition), and
`parents` (an array of included Definitions). A Definition contains:

| Field | Meaning |
| --- | --- |
| `key` | File-local identity; never a database ID |
| `name`, `basePath`, `exclusions`, `parent` | Workspace configuration |
| `projects` | Ordered `{key, path, nonMaven, enabled}` registrations |
| `blocks` | Ordered `{kind, name, parent, members, overrides}` composition |

A `PROJECT` block has one local project key in `members`; a `LOCAL` block has an
ordered list of local root keys. An `IMPORTED` block identifies an included parent
with `parent`, has empty `members`, and maps that parent's project keys to local
booleans in `overrides`. Parent Definitions have no blocks. Modules are registrations
whose hierarchy is recalculated from their paths/POMs; they do not occupy separate
blocks. Project and group names and paths must not contain line breaks.

For example, a minimal local group looks like:

```json
{
  "version": 2,
  "workspace": {
    "key": "workspace", "name": "Tools", "basePath": null,
    "exclusions": [], "parent": false,
    "projects": [
      {"key": "project0", "path": "/home/dev/scripts", "nonMaven": true, "enabled": true}
    ],
    "blocks": [
      {"kind": "LOCAL", "name": "Scripts", "parent": null,
       "members": ["project0"], "overrides": {}}
    ]
  },
  "parents": []
}
```

Each referenced parent is embedded once. Import creates new workspace and parent
identities and reconstructs references; matching display names never overwrite or
link existing parents. Structural errors and unsupported versions fail before
persistence. Missing directories produce warnings while valid entries retain order.
Derived Maven/Git metadata is read from local files, not copied from the export.
**Export Workspace Paths** emits only effective absolute paths, once per canonical
directory, without group metadata or type markers.

## Existing databases and rollback

Before upgrading a file-based installation, stop MvnBuilder and copy its
`data/mvnbuilder.mv.db` file. Hibernate adds the workspace kind, group, layout and
override tables. Existing workspaces receive kind NORMAL; an empty layout resolves
from existing execution order, retaining enabled flags, exclusions and module
hierarchy. Layout entries are persisted when grouping or reordering is used.

After using groups, an older release cannot interpret references or block order.
To roll back, stop the application and restore the pre-upgrade database copy.
