# git-commit-history Specification

## Purpose
Let users review recent commits of a repository and their file changes without leaving the Git view.

## Requirements

### Requirement: Recent commit history
The view SHALL show, for the active repository, a list of recent commits on the current branch with abbreviated identifier, subject, author, relative date and whether each commit is already contained in the upstream. More commits SHALL load on demand. Selecting a commit SHALL list its changed files, and selecting a file SHALL show a read-only comparison for that commit.

#### Scenario: Inspect last commits
- **WHEN** the user opens the history of a repository
- **THEN** the most recent commits SHALL be listed with unpushed commits distinguishable from pushed ones

#### Scenario: View a file changed in a commit
- **WHEN** the user selects a commit and then one of its files
- **THEN** a read-only comparison between the commit's parent and the commit SHALL be shown, without staging controls
