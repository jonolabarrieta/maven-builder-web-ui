package net.olaba.mvnbuilder;

import net.olaba.mvnbuilder.model.BuildFailure;
import net.olaba.mvnbuilder.model.LogMessage;
import net.olaba.mvnbuilder.model.M2ProjectInfo;
import net.olaba.mvnbuilder.model.ProcessInfo;
import net.olaba.mvnbuilder.service.FileSystemService;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the MvnBuilder application.
 */
@SpringBootApplication
@EnableAsync
@RegisterReflectionForBinding({
    FileSystemService.FileItem.class,
    M2ProjectInfo.class,
    BuildFailure.class,
    LogMessage.class,
    ProcessInfo.class
})
public class MvnBuilderApplication {

    /**
     * Main method to start the Spring Boot application.
     * 
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        SpringApplication.run(MvnBuilderApplication.class, args);
    }

}
