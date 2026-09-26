package net.olaba.mvnbuilder.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesktopLauncherServiceTest {

    private final DesktopLauncherService desktopLauncherService = new DesktopLauncherService();

    @Test
    void terminalCommandsUsePlatformAppropriateLaunchers() {
        assertEquals(List.of(List.of("cmd.exe", "/c", "start", "", "cmd.exe", "/K", "cd", "/d", "C:\\Work Space")),
                desktopLauncherService.terminalCommands("windows 11", "C:\\Work Space"));
        assertEquals(List.of(List.of("open", "-a", "Terminal", "/Users/name/Work Space")),
                desktopLauncherService.terminalCommands("mac os x", "/Users/name/Work Space"));
        assertEquals(List.of("xdg-terminal-exec", "--working-directory", "/tmp/project"),
                desktopLauncherService.terminalCommands("linux", "/tmp/project").get(0));
    }

    @Test
    void existingShortcutsRetainPlatformCommands() {
        assertEquals(List.of("cmd.exe", "/c", "code", "C:\\project"),
                desktopLauncherService.vsCodeCommand("windows", "C:\\project"));
        assertEquals(List.of("open", "/tmp/project"),
                desktopLauncherService.explorerCommand("mac", "/tmp/project"));
        assertEquals(List.of("xdg-open", "/tmp/project"),
                desktopLauncherService.explorerCommand("linux", "/tmp/project"));
    }
}
