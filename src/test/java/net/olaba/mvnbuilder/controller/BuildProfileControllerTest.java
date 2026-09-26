package net.olaba.mvnbuilder.controller;

import jakarta.servlet.http.HttpServletResponse;
import net.olaba.mvnbuilder.entities.BuildProfile;
import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import net.olaba.mvnbuilder.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildProfileControllerTest {

    @Mock
    BuildProfileRepository repository;

    @Mock
    WorkspaceService workspaceService;

    @InjectMocks
    BuildProfileController controller;

    @Test
    void activatingProfileRecalculatesVersionsWithoutChangingBuildCommand() {
        final BuildProfile oldProfile = BuildProfile.builder().withId(1L).withName("Default")
                .withCommand("-B clean install").withIsDefault(true).build();
        final BuildProfile selected = BuildProfile.builder().withId(2L).withName("CI")
                .withCommand("-B -Pother install").withVersionProfileId("batsdlc").build();
        when(repository.findById(2L)).thenReturn(Optional.of(selected));
        when(repository.findAll()).thenReturn(List.of(oldProfile, selected));

        assertEquals("Activated", controller.activateProfile(2L));

        assertTrue(selected.isDefault());
        assertEquals("-B -Pother install", selected.getCommand());
        verify(repository).saveAll(List.of(oldProfile, selected));
        verify(workspaceService).refreshDisplayedVersions("batsdlc");
    }

    @Test
    void editingActiveVersionProfileRefreshesWorkspaceView() {
        final BuildProfile profile = BuildProfile.builder().withId(1L).withName("Default")
                .withCommand("-B clean install").withIsDefault(true).build();
        when(repository.findById(1L)).thenReturn(Optional.of(profile));
        when(repository.findAll()).thenReturn(List.of(profile));
        final HttpServletResponse response = new MockHttpServletResponse();

        controller.updateVersionProfile(1L, " batsdlc ", new ExtendedModelMap(), response);

        assertEquals("batsdlc", profile.getVersionProfileId());
        assertEquals("-B clean install", profile.getCommand());
        assertEquals("true", response.getHeader("HX-Refresh"));
        verify(repository).save(profile);
        verify(workspaceService).refreshDisplayedVersions("batsdlc");
    }
}
