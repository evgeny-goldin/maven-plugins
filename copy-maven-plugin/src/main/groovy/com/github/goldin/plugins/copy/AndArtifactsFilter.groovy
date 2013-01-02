package com.github.goldin.plugins.copy

import org.apache.maven.artifact.Artifact
import org.apache.maven.shared.artifact.filter.collection.AbstractArtifactsFilter
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter
import org.gcontracts.annotations.Requires


/**
 * {@link ArtifactsFilter} implementation composing other filters.
 */
class AndArtifactsFilter extends AbstractArtifactsFilter
{
    private final List<ArtifactsFilter> filters


    @Requires({ filters != null })
    AndArtifactsFilter ( List<ArtifactsFilter> filters )
    {
        this.filters = filters
    }


    @Override
    Set filter ( Set artifacts ) throws ArtifactFilterException
    {
        artifacts.findAll {
            Artifact a ->
            this.filters.every { ArtifactsFilter f -> f.isArtifactIncluded( a )}
        }
    }
}
