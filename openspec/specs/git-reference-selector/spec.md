# git-reference-selector Specification

## Purpose
TBD - created by archiving change git-branch-selector-improvements. Update Purpose after archive.
## Requirements
### Requirement: Remote Branch Listing
The system SHALL retrieve and list remote Git branches for a selected project.

#### Scenario: View remote branches
- **WHEN** the user opens the branch selector modal for a project and switches to the "Remote Branches" tab
- **THEN** the modal displays all remote branches (excluding the symbolic HEAD reference)

### Requirement: Git Fetch and Refresh
The system SHALL provide a Fetch button that executes a Git fetch operation synchronously on the project repository and refreshes the modal content with any newly fetched references.

#### Scenario: Synchronously fetch and refresh references
- **WHEN** the user clicks the "Fetch" button inside the branch selector modal
- **THEN** the system executes `git fetch` synchronously and reloads the modal content with the latest references without full page reload

### Requirement: Git Tag Listing
The system SHALL retrieve and list Git tags for a selected project, sorted with version-based sorting descending so that the latest tags appear first.

#### Scenario: View git tags
- **WHEN** the user selects the "Tags" tab in the branch selector modal
- **THEN** the modal lists all tags for the project

### Requirement: Reference Checkout Confirmation

The system SHALL require user confirmation through a modal dialog before performing any checkout to a local branch, remote branch, or tag. The confirmation SHALL identify the selected reference and its destination state, including the local branch and upstream for remote choices and detached HEAD for tags.

#### Scenario: Confirm and checkout reference
- **WHEN** the user clicks on a reference (local branch, remote branch, or tag), confirms the operation on the modal dialog, and the checkout succeeds
- **THEN** the system SHALL perform the checkout according to the selected reference type and reload the main workspace page to reflect the actual new active reference

#### Scenario: Confirm a remote branch destination
- **WHEN** the user selects remote branch `origin/feature/demo`
- **THEN** the confirmation SHALL identify the destination local branch `feature/demo` and upstream `origin/feature/demo`
- **AND** it SHALL explain that switching does not pull or reset the local branch

#### Scenario: Confirm a tag destination
- **WHEN** the user selects tag `v1.0.0`
- **THEN** the confirmation SHALL identify `v1.0.0` and explain that the destination is a detached HEAD at the tag's commit

#### Scenario: Cancel a checkout
- **WHEN** the user cancels or dismisses the checkout confirmation
- **THEN** the system SHALL send no checkout request and SHALL preserve the current Git state

### Requirement: Typed Reference Selection

The single-project selector SHALL preserve each choice's reference type and exact Git ref identity from listing through checkout. The server SHALL validate that the reference exists in the namespace corresponding to its type before mutating the repository. An omitted type on the existing checkout endpoint SHALL retain support for an existing local branch and SHALL NOT guess a tag or remote reference.

#### Scenario: A branch and tag have the same name
- **WHEN** a local branch and tag both named `release` exist at different commits and the user selects `release` in Tags
- **THEN** the system SHALL check out the tag's commit in detached HEAD
- **AND** selecting `release` in Local SHALL instead activate the local branch

#### Scenario: A local branch resembles a remote ref
- **WHEN** local branch `origin/feature/demo` and remote branch `origin/feature/demo` are both available and the user selects the remote choice
- **THEN** the system SHALL resolve the remote ref and apply the remote tracking branch rules
- **AND** it SHALL NOT select the similarly named local ref through Git's name guessing

#### Scenario: Invalid or unavailable reference
- **WHEN** a checkout request contains an unknown type, a ref from the wrong namespace, or a reference that no longer exists
- **THEN** the system SHALL return an actionable failure without switching the repository or updating its cached reference label

#### Scenario: Existing local checkout request
- **WHEN** a request to the existing single-project checkout endpoint names an existing local branch without a reference type
- **THEN** the system SHALL activate that local branch and resolve its actual current state

### Requirement: Remote Tracking Branch Checkout

When a remote branch is selected in the single-project selector, the system SHALL activate a local branch with that remote branch as its upstream. The candidate local name SHALL preserve the complete branch path after removing the configured remote name. Existing local branches SHALL be reused only when their upstream is exactly the selected remote branch. The system SHALL NOT implicitly fetch, pull, reset, force checkout, or change an existing branch's upstream.

#### Scenario: Create a tracking branch
- **WHEN** the user confirms remote branch `origin/feature/demo` and local branch `feature/demo` does not exist
- **THEN** the system SHALL create and activate local branch `feature/demo` at the selected remote ref's commit
- **AND** its upstream SHALL be `origin/feature/demo`
- **AND** HEAD SHALL be attached to `feature/demo`

#### Scenario: Reuse a compatible tracking branch
- **WHEN** local branch `feature/demo` already tracks `origin/feature/demo` and that remote branch is selected
- **THEN** the system SHALL activate the existing local branch
- **AND** it SHALL preserve that branch's tip and upstream even if it is ahead of, behind, or diverged from the remote ref

#### Scenario: Refuse a conflicting local name
- **WHEN** the candidate local branch already exists without an upstream or with an upstream different from the selected remote branch
- **THEN** the system SHALL report the name conflict before checkout
- **AND** it SHALL preserve the active branch, local branch tips, upstream configuration, and cached reference label

#### Scenario: Select a remote other than origin
- **WHEN** the user selects remote branch `upstream/team/feature/demo` and the candidate local name is available
- **THEN** the system SHALL activate local branch `team/feature/demo` tracking `upstream/team/feature/demo`
- **AND** it SHALL NOT assume that the remote is named `origin`

#### Scenario: Git refuses the checkout
- **WHEN** local modifications or another normal Git protection prevents the requested branch switch
- **THEN** the system SHALL report Git's failure and restore the selector's usable state
- **AND** it SHALL NOT force checkout, discard files, or cache the requested destination as a successful current reference

### Requirement: Explicit Tag Checkout

When a tag is selected, the system SHALL check out the exact commit identified by that tag in detached HEAD. It SHALL support lightweight and annotated tags and SHALL NOT create or activate a same-named branch as a side effect.

#### Scenario: Checkout a lightweight tag
- **WHEN** the user confirms a lightweight tag at a commit different from the current branch tip
- **THEN** HEAD SHALL become detached at that tag's exact commit
- **AND** the current-reference display SHALL identify the tag and detached state

#### Scenario: Checkout an annotated tag
- **WHEN** the user confirms an annotated tag
- **THEN** HEAD SHALL become detached at the tag's peeled commit
- **AND** the system SHALL identify the tag rather than displaying only `HEAD`

### Requirement: Current Git Reference Identification

The system SHALL derive the current reference from actual Git state after checkout and during workspace reload, background Git refresh, and workspace scans. An attached HEAD SHALL be identified by its local branch. A detached HEAD SHALL be identified by exact matching tag information and its detached state, or by an abbreviated commit and its detached state when no exact tag exists. A valid detached state SHALL NOT be displayed solely as `HEAD`. Selector active markers SHALL use reference identity and actual state rather than comparisons between formatted labels and raw names.

#### Scenario: Remote selection remains attached after refresh
- **WHEN** a remote selection activates local tracking branch `feature/demo` and the workspace reloads or refreshes its Git status
- **THEN** the reference indicator SHALL show `feature/demo`
- **AND** the selector SHALL identify that local branch as active and associate it with its actual upstream

#### Scenario: Tag identification survives reload and refresh
- **WHEN** HEAD is detached at the commit of tag `v1.0.0`, no other tag identifies that commit, and the workspace reloads, refreshes Git status, or rescans projects
- **THEN** the resulting current-reference label SHALL include `v1.0.0` and an explicit detached-state indication
- **AND** Tags SHALL identify `v1.0.0` as a current exact match

#### Scenario: Several tags identify a detached commit
- **WHEN** HEAD is detached and multiple tags identify its exact commit
- **THEN** the system SHALL choose a deterministic primary tag using descending version ordering for the reference label
- **AND** the selector SHALL expose all exact matching tags as current matches

#### Scenario: Detached commit has no exact tag
- **WHEN** HEAD is detached at a commit without an exact matching tag
- **THEN** the reference label SHALL include an abbreviated commit identifier and detached-state indication
- **AND** it SHALL NOT present a nearby ancestor tag as the current tag

#### Scenario: Attached branch also has a tag
- **WHEN** a local branch is active and its tip also has one or more tags
- **THEN** the reference indicator SHALL identify the active local branch without describing it as detached

#### Scenario: Repository changes outside the application
- **WHEN** Git is switched externally to another branch, tag commit, or untagged detached commit and the application refreshes Git state
- **THEN** the display and subsequent selector state SHALL reflect the actual repository state rather than the last submitted checkout name

#### Scenario: Targeted background reference update
- **WHEN** a background Git request returns current-reference labels for a workspace
- **THEN** the system SHALL update only each matching project's reference indicator and the existing contextual status
- **AND** it SHALL preserve project rows, selected project IDs, sorting controls, and the existing stale-response protection
- **AND** reference labels SHALL be rendered as text
