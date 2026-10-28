## MODIFIED Requirements

### Requirement: Repository overview and independent selection
The view SHALL list clean and changed repositories by folder name without a path. Each entry SHALL show its branch, or a detached or unborn state, as a visually prominent label, along with upstream availability, cached-remote ahead/behind counts when available and pending-file counts. When two repositories share a folder name, the entry SHALL add the shortest parent segment that distinguishes them. The absolute path SHALL be available as a tooltip. The view SHALL offer search by repository name, branch and file path, and an optional changed-only filter. Git repository selections SHALL be independent of Maven project and build selections.

#### Scenario: Repository shown by name
- **WHEN** a repository lives at `/work/apps/billing-service`
- **THEN** its entry SHALL read `billing-service` with its branch label and SHALL not show the path as text

#### Scenario: Duplicate folder names
- **WHEN** two repositories are both named `api` under `/work/a/api` and `/work/b/api`
- **THEN** their entries SHALL read `a/api` and `b/api`

#### Scenario: Select repositories for an action
- **WHEN** the user selects two repositories in the Git view
- **THEN** the action target SHALL consist of those repositories regardless of the associated Maven projects' selected, disabled or skipped state

#### Scenario: Upstream is absent
- **WHEN** a repository has no configured upstream
- **THEN** the view SHALL show a no-upstream state rather than implying that it is synchronized or displaying fabricated ahead/behind counts

### Requirement: File selection and commit scope
The view SHALL provide commit-selection checkboxes for file entries, file groups and repositories, preserving them across repository switches and status refreshes while the entries remain available. Selecting a path from one area SHALL deselect its other area. Checking a repository's checkbox SHALL mark all of its eligible files, and unchecking it SHALL unmark them. The checkbox SHALL show a mixed state when only some files are marked. The commit target SHALL always be the marked files of the repositories that contain marked files; there SHALL be no commit scope selector. The view SHALL display the marked file and repository counts before submission and SHALL retain failed selections and drafts. Repository checkboxes SHALL also define the targets of bulk repository actions, including clean repositories checked by the user.

#### Scenario: Check a repository
- **WHEN** the user checks the checkbox of a repository with five changed files
- **THEN** all five files SHALL be marked for commit and the counts SHALL update

#### Scenario: Unmark one file of a checked repository
- **WHEN** the user unmarks one file of a fully checked repository
- **THEN** the repository checkbox SHALL show a mixed state and the remaining files SHALL stay marked

#### Scenario: Select files across repository switches
- **WHEN** the user marks files in one repository, switches to another, marks more files and returns
- **THEN** the original selections SHALL remain visible, and the commit SHALL target exactly the repositories containing marked files

#### Scenario: Push selected repositories
- **WHEN** the user checks several repository checkboxes
- **THEN** a visible Push selected control SHALL identify the target count and push those repositories with independent results
