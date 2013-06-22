package com.github.goldin.plugins.ivy

import static com.github.goldin.plugins.common.GMojoUtils.*
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
@SuppressWarnings([ 'GrFinalVariableAccess' ])
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
    @Requires({ request && request.artifact.groupId.startsWith( IVY_PREFIX ) })
    @Ensures({ result })
    ArtifactResult resolveIvy( ArtifactRequest request )
    {
        final a            = request.artifact
        final organisation = a.groupId.substring( IVY_PREFIX.size())
        final name         = a.artifactId
        final pattern      = a.classifier
        final revision     = a.version
        final extension    = a.extension
        // Artifact may have no "file" set if resolution fails and helper's "failOnError" is "false"
        final artifact     = ivyHelper.resolve( organisation, name, revision, extension, pattern )
        final result       = new ArtifactResult( request )                                  // org.sonatype.aether.resolution.ArtifactResult
        result.artifact    = new DefaultArtifact( organisation, name, extension, revision ) // org.sonatype.aether.util.artifact.DefaultArtifact
        if ( artifact.file )
        {
            result.artifact = result.artifact.setFile( artifact.file )
        }
        result
    }


    @Override
    @Requires({ session && request })
    @Ensures({ result })
    ArtifactResult resolveArtifact ( RepositorySystemSession session, ArtifactRequest request )
    {
        request.artifact.groupId.startsWith( IVY_PREFIX ) ?
            resolveIvy( request ) :
            delegateResolver.resolveArtifact( session, request )
    }


    @Override
    @Requires({ session && ( requests != null ) })
    @Ensures({ result != null })
    List<ArtifactResult> resolveArtifacts ( RepositorySystemSession session, Collection<? extends ArtifactRequest> requests )
    {
        requests.collect { ArtifactRequest request -> resolveArtifact( session, request )}
    }
}
