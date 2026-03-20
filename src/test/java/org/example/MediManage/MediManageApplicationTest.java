package org.example.MediManage;

import org.example.MediManage.util.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediManageApplicationTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY);
    }

    @Test
    void pythonExecutableCandidatesPreferLinuxInterpreters() {
        System.setProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY, "Linux");

        List<Path> candidates = MediManageApplication.pythonExecutableCandidates(Path.of("/opt/medimanage/ai_engine/python"));

        assertEquals(List.of(
                Path.of("/opt/medimanage/ai_engine/python/bin/python3"),
                Path.of("/opt/medimanage/ai_engine/python/bin/python"),
                Path.of("/opt/medimanage/ai_engine/python/python3"),
                Path.of("/opt/medimanage/ai_engine/python/python")), candidates);
    }

    @Test
    void pythonExecutableCandidatesPreferWindowsInterpretersOnWindows() {
        System.setProperty(AppPaths.OS_NAME_OVERRIDE_PROPERTY, "Windows 11");

        List<Path> candidates = MediManageApplication.pythonExecutableCandidates(Path.of("C:/MediManage/ai_engine/python"));

        assertEquals(List.of(
                Path.of("C:/MediManage/ai_engine/python/python.exe"),
                Path.of("C:/MediManage/ai_engine/python/Scripts/python.exe"),
                Path.of("C:/MediManage/ai_engine/python/bin/python.exe"),
                Path.of("C:/MediManage/ai_engine/python/bin/python")), candidates);
    }
}
