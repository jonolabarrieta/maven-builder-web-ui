package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.service.ProcessExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for handling build process control actions.
 */
@Controller
@RequestMapping("/build")
@RequiredArgsConstructor
public class BuildController {

    private final ProcessExecutionService processExecutionService;

    /**
     * Terminates the currently running build process.
     * 
     * @return A success response.
     */
    @PostMapping("/kill")
    @ResponseBody
    public ResponseEntity<Void> killProcess() {
        processExecutionService.killCurrentProcess();
        return ResponseEntity.ok().build();
    }
}
