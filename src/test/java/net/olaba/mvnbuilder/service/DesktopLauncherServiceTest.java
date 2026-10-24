package net.olaba.mvnbuilder.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesktopLauncherServiceTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    private final DesktopLauncherService desktopLauncherService = new DesktopLauncherService();

    @Test
    void terminalCommandsUsePlatformAppropriateLaunchers() {
        var commands = desktopLauncherService.terminalCommands("windows 11", "C:\\Work Space");
        assertEquals("pwsh.exe", commands.get(0).get(0));
        assertEquals("powershell.exe", commands.get(1).get(0));
        assertEquals(List.of(List.of("open", "-a", "Terminal", "/Users/name/Work Space")),
                desktopLauncherService.terminalCommands("mac os x", "/Users/name/Work Space"));
        assertEquals(List.of("xdg-terminal-exec", "--dir=/tmp/project"),
                desktopLauncherService.terminalCommands("linux", "/tmp/project").get(0));
    }

    @Test
    void linuxTerminalReceivesDirectoryAsAnOptionInsteadOfAnExecutable() throws Exception {
        var project = java.nio.file.Files.createDirectory(directory.resolve("O'Brien [test]; $x & 日本"));
        var attempts = new java.util.ArrayList<List<String>>();
        Process started = mock(Process.class);
        DesktopLauncherService launcher = new DesktopLauncherService() {
            protected String currentOs() { return "linux"; }
            protected Process launch(List<String> command, java.io.File cwd) {
                assertEquals(project.toFile(), cwd);
                attempts.add(command);
                return started;
            }
        };

        launcher.openTerminal(project.toFile());

        assertEquals(List.of(List.of("xdg-terminal-exec", "--dir=" + project)), attempts);
        verifyNoInteractions(started);
    }

    @Test
    void linuxFallsBackToGnomeWhenXdgLauncherIsUnavailable() throws Exception {
        var attempts = new java.util.ArrayList<List<String>>();
        Process started = mock(Process.class);
        DesktopLauncherService launcher = new DesktopLauncherService() {
            protected String currentOs() { return "linux"; }
            protected Process launch(List<String> command, java.io.File cwd) throws java.io.IOException {
                assertEquals(directory.toFile(), cwd);
                attempts.add(command);
                if (command.getFirst().equals("xdg-terminal-exec")) {
                    throw new java.io.IOException("not installed");
                }
                return started;
            }
        };

        launcher.openTerminal(directory.toFile());

        assertEquals(List.of(
                List.of("xdg-terminal-exec", "--dir=" + directory),
                List.of("gnome-terminal", "--working-directory=" + directory)), attempts);
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

    @Test void windowsPathsRemainLiteralAcrossBothEncodingLayers() {
        String path="C:\\Work Space\\O'Brien [test]; $x & 日本";
        var commands=desktopLauncherService.terminalCommands("windows",path);
        for(var command:commands) {
            assertFalse(command.contains("cmd.exe"));
            String script=new String(java.util.Base64.getDecoder().decode(command.getLast()),java.nio.charset.StandardCharsets.UTF_16LE);
            assertTrue(script.contains("-WorkingDirectory '"+path.replace("'","''")+"'"));
            assertTrue(script.contains("Get-Command pwsh.exe")); assertTrue(script.contains("'-NoExit'"));
            var matcher=java.util.regex.Pattern.compile("'-EncodedCommand','([^']+)'").matcher(script); assertTrue(matcher.find());
            String inner=new String(java.util.Base64.getDecoder().decode(matcher.group(1)),java.nio.charset.StandardCharsets.UTF_16LE);
            assertEquals("Set-Location -LiteralPath '"+path.replace("'","''")+"'",inner);
        }
    }

    @Test void missingPwshFallsBackAndFailedLaunchIsReported() throws Exception {
        Process success=mock(Process.class); when(success.waitFor(anyLong(),any())).thenReturn(true); when(success.exitValue()).thenReturn(0);
        java.util.ArrayList<String> attempts=new java.util.ArrayList<>();
        DesktopLauncherService launcher=new DesktopLauncherService(){
            protected String currentOs(){return "windows";}
            protected Process launch(List<String> command,java.io.File cwd)throws java.io.IOException {
                assertEquals(directory.toFile(),cwd); attempts.add(command.getFirst());
                if(command.getFirst().equals("pwsh.exe"))throw new java.io.IOException("not installed"); return success;
            }
        };
        launcher.openTerminal(directory.toFile()); assertEquals(List.of("pwsh.exe","powershell.exe"),attempts);
        DesktopLauncherService unavailable=new DesktopLauncherService(){
            protected String currentOs(){return "windows";}
            protected Process launch(List<String> command,java.io.File cwd)throws java.io.IOException {throw new java.io.IOException("not installed");}
        };
        assertThrows(java.io.IOException.class,()->unavailable.openTerminal(directory.toFile()));
        assertThrows(java.io.IOException.class,()->launcher.openTerminal(directory.resolve("missing").toFile()));
    }
}
