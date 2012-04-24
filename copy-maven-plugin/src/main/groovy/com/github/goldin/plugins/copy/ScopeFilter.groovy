package com.github.goldin.plugins.copy

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.artifact.Artifact
import org.apache.maven.shared.artifact.filter.collection.AbstractArtifactsFilter
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.sonatype.aether.collection.DependencySelector
import org.sonatype.aether.graph.Dependency
import org.sonatype.aether.util.graph.selector.ScopeDependencySelector


/**
 * Re-implementation of original Maven's {@link ScopeFilter} working with both include and exclude scopes.
 * Original implementation only respects one of them: http://goo.gl/8c2DJ.
 */
class ScopeFilter extends AbstractArtifactsFilter
{
    private final DependencySelector selector

    ScopeFilter ( List<String> includeScope, List<String> excludeScope )
    {   /**
         * Aether dependency selector works correctly with both scopes
         * See {@link ScopeDependencySelector#selectDependency}.
         */
        selector = new ScopeDependencySelector( includeScope, excludeScope )
    }


    @Override
    @Requires({ artifacts })
    @Ensures({ result != null })
    Set filter ( Set artifacts )
    {
        artifacts.findAll {
            Artifact a ->
            selector.selectDependency( new Dependency( toAetherArtifact( a ), null ))
        }.
        toSet()
    }
}
