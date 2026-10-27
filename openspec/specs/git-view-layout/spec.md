# git-view-layout Specification

## Purpose
Let users size the Git view's panes to fit long paths, wide comparisons and long commit messages, and keep that layout between visits.

## Requirements

### Requirement: Resizable panes
On wide viewports the view SHALL provide drag handles to resize the repository sidebar width and the height of the commit and operation area relative to the comparison. In split mode it SHALL also allow adjusting the left/right ratio. Each handle SHALL be keyboard-operable with arrow keys and expose its orientation and current value to assistive technology. Sizes SHALL be clamped to keep every pane usable.

#### Scenario: Widen the sidebar
- **WHEN** the user drags the sidebar handle to the right
- **THEN** the sidebar SHALL widen beyond its default maximum, up to a limit that keeps the comparison usable

#### Scenario: Keyboard resize
- **WHEN** the sidebar handle has focus and the user presses the right or left arrow key
- **THEN** the sidebar width SHALL change by a fixed step and the new value SHALL be announced

### Requirement: Collapsible sidebar
The view SHALL provide a control and a keyboard shortcut to hide and restore the repository sidebar, giving the comparison the full width.

#### Scenario: Focus on a comparison
- **WHEN** the user hides the sidebar
- **THEN** the comparison SHALL use the full width, and restoring the sidebar SHALL return it to its previous width

### Requirement: Persisted layout
Pane sizes, split ratio and sidebar visibility SHALL persist in the browser across page loads. On narrow viewports the existing back-navigation layout SHALL apply and resize handles SHALL not be shown.

#### Scenario: Reload keeps layout
- **WHEN** the user resizes panes and reloads the Git page
- **THEN** the panes SHALL use the previously chosen sizes

#### Scenario: Narrow viewport
- **WHEN** the viewport is narrow
- **THEN** resize handles SHALL be hidden and the stacked layout with an explicit back control SHALL be used
