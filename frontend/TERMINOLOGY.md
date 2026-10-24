# Interface terminology

Use English for application labels, help, confirmations and status messages.
Keep user content, command output and published release notes in their original language.

| Term | Meaning and wording |
| --- | --- |
| Workspace | A saved collection of Maven projects and its configuration. Deleting it keeps project files on disk. |
| Project | A Maven project registered in a workspace. Removing it also removes its submodules from the workspace and excludes its path from future scans. |
| Submodule | A child Maven project. Use **View submodules**, not “Dive” or “View Recursive”. |
| Duplicate workspace | Copy the saved workspace. Avoid “Clone”, which could suggest a Git clone. |
| Base folder | The folder scanned for Maven projects. Explain when scanning happens. |
| Build profile | A shared MvnBuilder configuration that supplies Maven arguments for builds. |
| Maven arguments | Arguments after `mvn`; examples must omit the executable itself. |
| Version profile ID | Optional POM profile used to resolve displayed versions. It does not replace `-PprofileId` in build arguments. |
| Skipped | A project excluded from operations that honor the enabled state. Do not imply that its files have been removed. |
| Refresh from disk | Rescan project files. Keep this distinct from Git Fetch or reading branch names. |
| Unstage changes | Remove changes from Git's staging area while keeping them in the working tree. |
| Discard changes | Discard unstaged changes to tracked files. State irreversibility; staged changes and untracked files are kept. |
| Repository (Git view) | A discovered Git working tree beneath the workspace base folder, independent of Maven project registration or build selection. |
| Changes | Unstaged or untracked changes; compare staging area to working tree. |
| Staged Changes | Changes prepared for the next commit; compare last commit to staging area. |
| Selected files | File checkboxes choose commit content; selecting Changes uses the working tree and selecting Staged Changes uses the index. A path has one selected source. |
| Commit scope | Distinguishes current staged changes, current selected files, and selected files across repositories. Multiple targets create separate commits with the common message. |
| Push selected | Push the repositories marked by repository checkboxes, independently of file checkboxes. |
| Commit staged changes | Create one repository commit from its reviewed index. Push is a separate action. |
| Local Maven repository (.m2) | Cached/local Maven artifacts. Deleting these files is different from removing a project registration. Do not promise that all artifacts can be downloaded again. |

Confirmation buttons should name the action and object. Counts use complete singular/plural messages. Error messages name the failed operation and a useful next step; HTML or JSON error pages should not become the primary message.
