package com.github.goldin.plugins.copy

import org.apache.maven.artifact.Artifact
import org.apache.maven.shared.artifact.filter.collection.AbstractArtifactsFilter
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter
import org.gcontracts.annotations.Requires


/**
 * {@link ArtifactsFilter} implementation filtering artifacts according to their scope,
 * similarly to
 * {@link org.eclipse.aether.util.graph.selector.ScopeDependencySelector#selectDependency(org.eclipse.aether.graph.Dependency)}
 * and not
 * {@link org.apache.maven.shared.artifact.filter.collection.ScopeFilter#filter(java.util.Set)}.
 *
 * See http://evgeny-goldin.org/youtrack/issue/pl-679.
 */
class ScopeArtifactsFilter extends AbstractArtifactsFilter
{
    private final List<String> includeScopes
    private final List<String> excludeScopes


    @Requires({( includeScopes != null ) && ( excludeScopes != null )})
    ScopeArtifactsFilter ( List<String> includeScopes, List<String> excludeScopes )
    {
        this.includeScopes = includeScopes
        this.excludeScopes = excludeScopes
    }

    @Override
    Set filter ( Set artifacts ) throws ArtifactFilterException
    {
        artifacts.findAll { Artifact a ->
            a.scope.with {
                ( includeScopes.empty || (   includeScopes.contains( delegate ))) &&
                ( excludeScopes.empty || ( ! excludeScopes.contains( delegate )))
            }
        }
    }
}
