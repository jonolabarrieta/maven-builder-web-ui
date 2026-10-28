# workspace-git-discovery Specification

## Purpose
Define workspace-scoped discovery and validation of the Git working trees that contain the workspace's registered projects.

## Requirements

### Requirement: Workspace-wide Git repository discovery
The system SHALL derive the Git view's repositories from the projects registered in the workspace, including enabled and disabled projects and projects contributed by imported parent workspaces, regardless of whether they are inside the workspace base folder. For each project the system SHALL resolve the Git working-tree root that contains the project folder. Repositories that only exist on disk, including those under the base folder whose projects were removed from the workspace, SHALL NOT be listed. On entry to the Git view the system SHALL present the most recent discovery result for the workspace when one exists and the registered project set is unchanged, and SHALL run a fresh discovery when none exists, when projects were added or removed, or through an explicit Refresh repositories action. Discovery SHALL not fetch remotes.

#### Scenario: Non-Maven and skipped projects are included
- **WHEN** the workspace registers an enabled Maven project, a disabled Maven project and a folder project, each in its own repository
- **THEN** all three repositories SHALL appear in the Git view without changing any project registration

#### Scenario: Project outside the base folder
- **WHEN** a registered project lives in a repository outside the workspace base folder
- **THEN** that repository SHALL appear in the Git view

#### Scenario: Project imported from a parent workspace
- **WHEN** the workspace imports a parent workspace whose projects live in other repositories
- **THEN** those repositories SHALL appear in the Git view

#### Scenario: Removed project
- **WHEN** a project is removed from the workspace while its repository remains on disk under the base folder
- **THEN** its repository SHALL no longer appear after the next discovery, unless another registered project still belongs to it

#### Scenario: Unregistered repository under the base folder
- **WHEN** the base folder contains a repository that no registered project belongs to
- **THEN** that repository SHALL NOT appear in the Git view

#### Scenario: Base folder is a repository
- **WHEN** the base folder is itself a working-tree root containing a registered project, and it also contains an unregistered nested repository
- **THEN** the base folder repository SHALL be listed and the unregistered nested repository SHALL NOT

#### Scenario: Base folder changed
- **WHEN** the workspace base folder changes while the registered projects stay the same
- **THEN** the same repositories SHALL remain listed with the same identities

#### Scenario: Reopen the Git view
- **WHEN** the user opens the Git view again after a completed discovery and the registered projects have not changed
- **THEN** the previous repository list SHALL be shown without a new discovery, and Refresh repositories SHALL still run one

### Requirement: Repository identity and nested working trees
The system SHALL resolve each project folder to its containing working-tree root using Git, and SHALL deduplicate canonical working-tree roots so that several projects or modules in one working tree map to one repository entry. A nested repository or initialized submodule SHALL appear only when a registered project resolves to it. Linked worktrees SHALL remain distinct even when they share Git metadata. Each entry SHALL expose a display name equal to the working-tree root's folder name and its absolute path.

#### Scenario: Modules share a repository
- **WHEN** three registered projects belong to one working tree
- **THEN** discovery SHALL expose one repository entry rather than three independently actionable duplicates

#### Scenario: Project in a subfolder of a repository
- **WHEN** a registered project folder is a subdirectory of a working tree
- **THEN** the entry SHALL represent the working-tree root, named after the root folder

#### Scenario: Nested repository, submodule, and linked worktree
- **WHEN** registered projects belong to an outer repository, to a repository nested inside it, to an initialized submodule and to a linked worktree
- **THEN** each distinct working-tree root SHALL have its own stable workspace-scoped identity and display name

### Requirement: Bounded discovery with explicit outcomes
Discovery SHALL resolve projects with bounded concurrency and timeouts and SHALL report, without failing the whole result, each project whose folder is missing, unreadable or not inside a Git working tree, naming the project. Bare repositories SHALL be reported as having no editable working tree. Long discoveries SHALL expose progress without blocking the rest of the page. Incomplete results SHALL be labelled as incomplete rather than represented as complete.

#### Scenario: Unreadable directory during a long scan
- **WHEN** discovery resolves several projects and one project folder cannot be read
- **THEN** the resolved repositories SHALL remain usable and the view SHALL show a warning naming that project together with the completion state

#### Scenario: Project folder is not a repository
- **WHEN** a registered project folder is not inside any Git working tree
- **THEN** the other repositories SHALL remain usable and the view SHALL show a warning naming that project

#### Scenario: Project folder is missing
- **WHEN** a registered project's folder no longer exists
- **THEN** discovery SHALL list a warning for that project and continue with the remaining projects

#### Scenario: Missing base folder or no repositories
- **WHEN** the workspace has no registered projects, or its base folder is missing while its projects still resolve
- **THEN** a workspace without projects SHALL show an empty state explaining that repositories come from workspace projects, and a missing base folder SHALL not prevent listing the repositories of resolvable projects

#### Scenario: Large build output folders
- **WHEN** project repositories contain large `target` and `node_modules` folders
- **THEN** discovery SHALL not traverse those folders, because it only resolves registered project folders

### Requirement: Workspace-scoped repository access
Every repository, status, diff and mutation request SHALL resolve its repository identity server-side and validate that the working tree still belongs to at least one project currently registered in the workspace. The system SHALL reject stale identities, cross-workspace targets, administrative paths, traversal requests and arbitrary client-supplied filesystem paths.

#### Scenario: Repository is removed or moved outside the workspace
- **WHEN** a request references a repository whose only project was removed from the workspace after discovery
- **THEN** the request SHALL fail without executing its Git action and the view SHALL request a discovery refresh

#### Scenario: Base folder is inside an enclosing repository
- **WHEN** a registered project folder is a subdirectory of a repository rooted outside the base folder
- **THEN** that enclosing repository SHALL be actionable because a registered project belongs to it, while enclosing repositories of unregistered folders SHALL not

#### Scenario: Linked worktree has external metadata
- **WHEN** a registered project's working tree references its Git metadata in another location
- **THEN** the system SHALL allow that working tree while continuing to reject arbitrary client-supplied filesystem paths

#### Scenario: Repository of another workspace
- **WHEN** a request supplies a repository identity discovered for a different workspace
- **THEN** the request SHALL be rejected

#### Scenario: Client supplies a path
- **WHEN** a request tries to target a filesystem path that is not a discovered repository of the workspace
- **THEN** the system SHALL reject it without running Git

### Requirement: Automatic rediscovery of unknown identities
When a repository request fails because the server does not know the repository identity, for example after a server restart, the view SHALL run one discovery automatically and retry the original read once if the repository is found again. Mutations SHALL not be retried automatically; the user SHALL be asked to review the refreshed state.

#### Scenario: Server restarted while the page was open
- **WHEN** the user selects a file after the server restarted
- **THEN** the view SHALL rediscover repositories and load the comparison without requiring a manual refresh

#### Scenario: Mutation after restart
- **WHEN** a stage request fails because the repository identity is unknown
- **THEN** the view SHALL rediscover repositories and ask the user to repeat the action after reviewing the refreshed state
