package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter
import org.gcontracts.annotations.Requires
import org.sonatype.aether.collection.DependencyCollectionContext
import org.sonatype.aether.collection.DependencySelector
import org.sonatype.aether.graph.Dependency


/**
 * {@link DependencySelector} implementation based on list of {@link ArtifactsFilter}s.
 */
class ArtifactFiltersDependencySelector implements DependencySelector
{

    final List<ArtifactsFilter> filters

    @Requires({ filters != null })
    ArtifactFiltersDependencySelector ( List<ArtifactsFilter> filters )
    {
        this.filters = new ArrayList<ArtifactsFilter>( filters ).asImmutable()
    }


    @Requires({ dependency })
    @Override
    boolean selectDependency ( Dependency dependency )
    {
        final mavenArtifact = toMavenArtifact( dependency )
        filters.every { it.isArtifactIncluded( mavenArtifact )}
    }


    @Override
    DependencySelector deriveChildSelector ( DependencyCollectionContext context ){ this }
}
