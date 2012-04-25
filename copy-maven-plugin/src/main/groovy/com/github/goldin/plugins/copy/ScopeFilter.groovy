package com.github.goldin.plugins.copy

import org.apache.maven.artifact.Artifact
import org.apache.maven.shared.artifact.filter.collection.AbstractArtifactsFilter
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


/**
 * Re-implementation of original Maven's {@link ScopeFilter} working with both include and exclude scopes.
 * Original implementation only respects one of them: http://goo.gl/8c2DJ.
 */
class ScopeFilter extends AbstractArtifactsFilter
{
    private final List<String> includeScope = []
    private final List<String> excludeScope = []

    ScopeFilter ( List<String> includeScope, List<String> excludeScope )
    {
        this.includeScope.addAll( includeScope )
        this.excludeScope.addAll( excludeScope )
    }


    @Override
    @Requires({ artifacts })
    @Ensures({ result != null })
    Set filter ( Set artifacts )
    {
        artifacts.findAll {
            Artifact a ->
            ( includeScope.empty ||   ( a.scope in includeScope )) &&
            ( excludeScope.empty || ! ( a.scope in excludeScope ))
        }.
        toSet()
    }
}
