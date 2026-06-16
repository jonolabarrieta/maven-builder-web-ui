package net.olaba.mvnbuilder.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO representing an asset attached to a release.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetInfo(
    @JsonProperty("name") String name,
    @JsonProperty("browser_download_url") String browserDownloadUrl
) {}
