package net.olaba.mvnbuilder.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DTO representing release update information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateInfo(
    @JsonProperty("tag_name") String tagName,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("body") String body,
    @JsonProperty("assets") List<AssetInfo> assets
) {}
