# Spec Delta

## Purpose

Let users review recent commits of a repository and correct the last commit before it is pushed, without leaving the Git view.

## ADDED Requirements

### Requirement: Recent commit history
The view SHALL show, for the active repository, a list of recent commits on the current branch with abbreviated identifier, subject, author, relative date and whether each commit is already contained in the upstream. More commits SHALL load on demand. Selecting a commit SHALL list its changed files, and selecting a file SHALL show a read-only comparison for that commit.

#### Scenario: Inspect last commits
- **WHEN** the user opens the history of a repository
- **THEN** the most recent commits SHALL be listed with unpushed commits distinguishable from pushed ones

#### Scenario: View a file changed in a commit
- **WHEN** the user selects a commit and then one of its files
- **THEN** a read-only comparison between the commit's parent and the commit SHALL be shown, without staging controls

### Requirement: Amend last unpushed commit
The commit form SHALL offer an Amend option that replaces the last commit with the current staged changes and the given message, prefilled from the last commit's message. Amend SHALL be unavailable when the repository has no commits, when the last commit is already contained in its upstream, or during an unfinished merge, rebase, cherry-pick or revert. Amend SHALL validate the reviewed HEAD and staged snapshot and SHALL never force push.

#### Scenario: Fix the last commit message
- **WHEN** the last commit is not pushed and the user amends with a new message
- **THEN** the last commit SHALL be replaced with the new message, including any staged changes

#### Scenario: Last commit already pushed
- **WHEN** the last commit is contained in the upstream
- **THEN** the Amend option SHALL be disabled with an explanation

#### Scenario: HEAD changed after review
- **WHEN** HEAD changed between opening the form and submitting the amend
- **THEN** the amend SHALL be rejected as stale and the draft preserved
