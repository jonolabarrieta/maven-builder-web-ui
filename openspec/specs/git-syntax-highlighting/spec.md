# git-syntax-highlighting Specification

## Purpose
Make comparisons readable like an IDE by highlighting source syntax by file type and pinpointing changed words within modified lines.

## Requirements

### Requirement: Language-aware highlighting
Text comparisons SHALL apply syntax highlighting chosen from the file name or extension, covering at least Java, JSON, XML (including `pom.xml`), YAML, properties, SQL, JavaScript, TypeScript, CSS, HTML, shell scripts and Markdown. Unknown types SHALL render as plain text. Highlighting SHALL be available in split and unified modes and SHALL not change line identities, numbering, selection or staging behavior. Highlighted output SHALL never execute or interpret file content as markup.

#### Scenario: Java file comparison
- **WHEN** the user opens a comparison for a `.java` file
- **THEN** keywords, strings, comments and annotations SHALL be visually distinguished while added and deleted line backgrounds remain visible

#### Scenario: Content containing markup
- **WHEN** a compared file contains HTML or script tags
- **THEN** the text SHALL be displayed literally and no markup from the file SHALL be rendered or executed

#### Scenario: Unknown file type
- **WHEN** the file extension is not recognized
- **THEN** the comparison SHALL be rendered as plain text without errors

### Requirement: Multi-line constructs
Constructs spanning several lines, such as block comments, Java text blocks and XML CDATA, SHALL be highlighted consistently across the lines shown in a change block.

#### Scenario: Block comment inside a change block
- **WHEN** a change block contains a block comment that spans several lines
- **THEN** every displayed line of that comment SHALL be highlighted as a comment

### Requirement: Word-level change marking
For paired deleted and added lines in the same change block, the view SHALL additionally mark the changed words or characters within each line.

#### Scenario: Single identifier renamed
- **WHEN** a line changes only one identifier
- **THEN** the deleted and added lines SHALL emphasize that identifier, not the whole line

### Requirement: Highlighting does not degrade large comparisons
Highlighting SHALL be skipped or deferred above a size threshold so that large comparisons still render progressively and remain interactive.

#### Scenario: Very large diff
- **WHEN** a comparison exceeds the highlighting threshold
- **THEN** it SHALL render as plain text with a notice, and staging controls SHALL remain available
