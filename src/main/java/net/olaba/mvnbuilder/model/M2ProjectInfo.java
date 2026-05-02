package net.olaba.mvnbuilder.model;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * DTO containing information about a project in the local M2 repository.
 */
@Getter
@Setter
public class M2ProjectInfo {
    /** The unique identifier of the project in the database. */
    private Long projectId;
    
    /** Maven group ID. */
    private String groupId;
    
    /** Maven artifact ID. */
    private String artifactId;
    
    /** List of installed versions in the local repository. */
    private List<String> versions;
    
    /** Absolute filesystem path to the project directory in the M2 repository. */
    private String absolutePath;
}
