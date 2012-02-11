package com.goldin.plugins.ivy

import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.sonatype.aether.RepositorySystemSession
import org.sonatype.aether.impl.ArtifactResolver
import org.sonatype.aether.resolution.ArtifactRequest
import org.sonatype.aether.resolution.ArtifactResult
import org.sonatype.aether.util.artifact.DefaultArtifact

/**
 * Resolved Ivy artifacts using the settings file specified.
 */
class IvyArtifactResolver implements ArtifactResolver {

    private final ArtifactResolver delegateResolver
    private final IvyHelper        ivyHelper


    @Requires({ delegateResolver && ivyHelper })
    IvyArtifactResolver ( ArtifactResolver delegateResolver, IvyHelper ivyHelper )
    {
        this.delegateResolver = delegateResolver
        this.ivyHelper        = ivyHelper
    }


    /**
     * Attempts to resolve the request using Ivy.
     *
     * @param request artifact request
     * @return artifact result resolved or {@code null} if resolution fails.
     */
    @Requires({ request && request.artifact.groupId.startsWith( IvyMojo.IVY_PREFIX ) })
    ArtifactResult resolveIvy( ArtifactRequest request )
    {
        final a                = request.artifact
        final organisation     = a.groupId.substring( IvyMojo.IVY_PREFIX.size())
        final name             = a.artifactId
        final pattern          = a.classifier
        final revision         = a.version
        final extension        = a.extension
        final resolvedArtifact = ivyHelper.resolve( organisation, name, revision, pattern, extension )

        if ( ! resolvedArtifact ) { return null }

        final result    = new ArtifactResult( request )                                                                   // org.sonatype.aether.resolution.ArtifactResult
        result.artifact = new DefaultArtifact( organisation, name, extension, revision ).setFile( resolvedArtifact.file ) // org.sonatype.aether.util.artifact.DefaultArtifact
        result
    }


    @Override
    @Requires({ session && request })
    ArtifactResult resolveArtifact ( RepositorySystemSession session, ArtifactRequest request )
    {
        request.artifact.groupId.startsWith( IvyMojo.IVY_PREFIX ) ?
            resolveIvy( request ) : // May return null.
            delegateResolver.resolveArtifact( session, request )
    }


    @Override
    @Requires({ session && requests })
    @Ensures({ result != null })
    List<ArtifactResult> resolveArtifacts ( RepositorySystemSession session, Collection<? extends ArtifactRequest> requests )
    {
        requests.collect { ArtifactRequest request -> resolveArtifact( session, request )}.grep()
    }
}
