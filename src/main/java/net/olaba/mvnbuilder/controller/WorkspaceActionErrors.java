package net.olaba.mvnbuilder.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Preserve actionable validation errors for both ordinary forms and async actions. */
@ControllerAdvice(assignableTypes={WorkspaceController.class, WorkspaceGroupController.class, ParentWorkspaceController.class})
public class WorkspaceActionErrors {
    @ExceptionHandler({ResponseStatusException.class, IllegalArgumentException.class})
    public Object invalid(RuntimeException error, HttpServletRequest request, RedirectAttributes redirect) {
        String path=request.getRequestURI();
        String message=error instanceof ResponseStatusException response ? response.getReason() : error.getMessage();
        if ("POST".equals(request.getMethod()) && (path.matches("/workspaces/\\d+/(groups(?:/.*)?|delete)")
                || path.equals("/parent-workspaces")) && !path.endsWith("/action") && !path.endsWith("/order") && !"true".equals(request.getHeader("X-Group-Refresh"))) {
            redirect.addFlashAttribute("errorMessage",message);
            String id=path.replaceFirst("^/workspaces/(\\d+).*", "$1");
            return new ModelAndView("redirect:"+(path.equals("/parent-workspaces") ? "/parent-workspaces" : "/workspaces/"+id));
        }
        int status=error instanceof ResponseStatusException response ? response.getStatusCode().value() : 400;
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.TEXT_PLAIN).body(message == null ? "Invalid workspace action." : message);
    }
}
