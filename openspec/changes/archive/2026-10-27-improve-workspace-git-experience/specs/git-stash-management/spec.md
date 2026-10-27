# Spec Delta

## Purpose

Let users see and reuse the stashes they create from the Git view instead of only creating them.

## ADDED Requirements

### Requirement: Stash list
The view SHALL list the stashes of the active repository with index, message, originating branch and date.

#### Scenario: Repository with stashes
- **WHEN** the active repository has two stashes
- **THEN** both SHALL be listed newest first with their message and branch

#### Scenario: No stashes
- **WHEN** the repository has no stashes
- **THEN** the list SHALL show an empty state

### Requirement: Apply, pop and drop stashes
The user SHALL be able to apply, pop or drop a listed stash. Drop SHALL require an application-rendered confirmation naming the stash and stating irreversibility. Applying or popping a stash that conflicts SHALL report the conflicts, and pop SHALL keep the stash when it cannot be applied cleanly. Every stash mutation SHALL validate that the targeted stash identity is still the one the user selected.

#### Scenario: Pop a stash cleanly
- **WHEN** the user pops a stash that applies without conflicts
- **THEN** its changes SHALL return to the working tree, the stash SHALL be removed and the status SHALL refresh

#### Scenario: Apply with conflicts
- **WHEN** applying a stash produces conflicts
- **THEN** the conflicts SHALL appear under Conflicts, the stash SHALL remain in the list and the outcome SHALL explain the next steps

#### Scenario: Stash list changed before drop
- **WHEN** another stash is created after the user opened the drop confirmation
- **THEN** the drop SHALL target the originally selected stash identity or be rejected as stale, never a different stash
