package net.olaba.mvnbuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MvnBuilderApplicationTest {

    @Test
    void testShouldRunAsServiceWhenJarAndNoArgs() {
        String[] args = new String[]{};
        assertTrue(MvnBuilderApplication.shouldRunAsService(args, true));
    }

    @Test
    void testShouldNotRunAsServiceWhenNotJarAndNoArgs() {
        String[] args = new String[]{};
        falseIf: assertFalse(MvnBuilderApplication.shouldRunAsService(args, false));
    }

    @Test
    void testShouldNotRunAsServiceWhenNoServiceArgProvided() {
        String[] args1 = new String[]{"--no-service"};
        String[] args2 = new String[]{"no-service"};
        String[] args3 = new String[]{"-no-service"};

        assertFalse(MvnBuilderApplication.shouldRunAsService(args1, true));
        assertFalse(MvnBuilderApplication.shouldRunAsService(args2, true));
        assertFalse(MvnBuilderApplication.shouldRunAsService(args3, true));
    }

    @Test
    void testShouldNotRunAsServiceWhenNotJarAndNoServiceArgProvided() {
        String[] args = new String[]{"--no-service"};
        assertFalse(MvnBuilderApplication.shouldRunAsService(args, false));
    }

    @Test
    void testAutostartContentsContainNoServiceParameter() {
        String jarPath = "/path/to/my-app.jar";
        String javaExe = MvnBuilderApplication.getJavaExecutablePath();
        String expectedParentDir = "/path/to";

        String windowsContent = MvnBuilderApplication.getWindowsAutostartContent(jarPath);
        assertTrue(windowsContent.contains("--no-service"), "Windows bat content must contain '--no-service'");
        assertTrue(windowsContent.contains(jarPath), "Windows bat content must contain the jar path");
        assertTrue(windowsContent.contains(javaExe), "Windows bat content must contain the java executable path");
        assertTrue(windowsContent.contains("cd /d \"" + expectedParentDir + "\""), "Windows bat content must change directory to JAR parent");

        String linuxContent = MvnBuilderApplication.getLinuxAutostartContent(jarPath);
        assertTrue(linuxContent.contains("--no-service"), "Linux service content must contain '--no-service'");
        assertTrue(linuxContent.contains(jarPath), "Linux service content must contain the jar path");
        assertTrue(linuxContent.contains(javaExe), "Linux service content must contain the java executable path");
        assertTrue(linuxContent.contains("WorkingDirectory=" + expectedParentDir), "Linux service content must set WorkingDirectory");

        String macContent = MvnBuilderApplication.getMacAutostartContent(jarPath);
        assertTrue(macContent.contains("--no-service"), "Mac plist content must contain '--no-service'");
        assertTrue(macContent.contains(jarPath), "Mac plist content must contain the jar path");
        assertTrue(macContent.contains(javaExe), "Mac plist content must contain the java executable path");
        assertTrue(macContent.contains("<key>WorkingDirectory</key>\n    <string>" + expectedParentDir + "</string>"), "Mac plist content must set WorkingDirectory");
    }
}
