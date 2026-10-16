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
| Local Maven repository (.m2) | Cached/local Maven artifacts. Deleting these files is different from removing a project registration. Do not promise that all artifacts can be downloaded again. |

Confirmation buttons should name the action and object. Counts use complete singular/plural messages. Error messages name the failed operation and a useful next step; HTML or JSON error pages should not become the primary message.
