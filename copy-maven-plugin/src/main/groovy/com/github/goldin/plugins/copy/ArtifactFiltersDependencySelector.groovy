package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.ConversionUtils.*
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter
import org.gcontracts.annotations.Ensures
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


    @Requires({ filters      != null })
    @Ensures ({ this.filters != null })
    ArtifactFiltersDependencySelector ( List<ArtifactsFilter> filters )
    {
        this.filters = new ArrayList<ArtifactsFilter>( filters ).asImmutable()
    }


    @Requires({ dependency && ( this.filters != null ) })
    @Override
    boolean selectDependency ( Dependency dependency )
    {
        final mavenArtifact = toMavenArtifact( dependency )
        filters.every { it.isArtifactIncluded( mavenArtifact )}
    }


    @Override
    @Ensures ({ result })
    DependencySelector deriveChildSelector ( DependencyCollectionContext context ){ this }


    @Override
    int hashCode () { this.filters.hashCode() }


    @Override
    @SuppressWarnings([ 'JavaStylePropertiesInvocation', 'GroovyGetterCallCanBePropertyAccess' ])
    boolean equals ( Object o )
    {
        if ( this.is( o )) { return true }

        if (( o == null ) || ( ! ( this.getClass() == o.getClass()))) { return false }

        (( ArtifactFiltersDependencySelector ) o ).filters == this.filters
    }
}
