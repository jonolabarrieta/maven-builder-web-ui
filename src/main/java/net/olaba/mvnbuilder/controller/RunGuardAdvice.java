package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.service.BuildGuardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

/** Tells the page that a build needs the user's decision about running RUN entries. */
@ControllerAdvice
public class RunGuardAdvice {
    /** Body of the 409 response; the page re-submits the build with {@code runGuard}. */
    public record GuardResponse(boolean runGuard, String message, List<BuildGuardService.RunningEntry> entries) { }

    @ExceptionHandler(BuildGuardService.GuardRequiredException.class)
    public ResponseEntity<GuardResponse> guardRequired(final BuildGuardService.GuardRequiredException required) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
            .body(new GuardResponse(true, required.getMessage(), required.getEntries()));
    }
}
