## REMOVED Requirements

### Requirement: Amend last unpushed commit
**Reason**: Commits now consist only of marked files. Amend replaced the last commit with staged content, which contradicts that model and is not supported by the marked-file commit.
**Migration**: To correct the last commit, use Git directly through Open in VS Code or a terminal. The history and log stay available to review commits.
