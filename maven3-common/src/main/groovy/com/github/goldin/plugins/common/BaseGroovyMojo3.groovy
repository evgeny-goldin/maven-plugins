package com.github.goldin.plugins.common

import org.apache.maven.artifact.Artifact
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.RepositorySystemSession
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.resolution.ArtifactRequest
import org.sonatype.aether.util.artifact.DefaultArtifact
import com.github.goldin.plugins.common.BaseGroovyMojo

/**
 * Base class for all Mojo3-based plugins.
 */
abstract class BaseGroovyMojo3 extends BaseGroovyMojo
{

    /**
     * Aether components:
     * http://aether.sonatype.org/using-aether-in-maven-plugins.html
     * https://docs.sonatype.org/display/AETHER/Home
     */

    @MojoComponent
    public RepositorySystem repoSystem

    @MojoParameter ( defaultValue = '${repositorySystemSession}', readonly = true )
    public RepositorySystemSession repoSession

    @MojoParameter ( defaultValue = '${project.remoteProjectRepositories}', readonly = true )
    public List<RemoteRepository> remoteRepos


    /**
     * Resolves local {@link File} of Maven {@link Artifact} and updates it.
     *
     * @param artifact Maven artifact to resolve
     * @return same artifact with its local file set
     */
    @Requires({ a })
    @Ensures({ a.file.file })
    protected final Artifact resolveArtifact( Artifact a )
    {
        if ( ! a.file )
        {
            final request = new ArtifactRequest( new DefaultArtifact( a.groupId, a.artifactId, a.classifier, a.type, a.version ),
                                                 remoteRepos, null )
            a.file = repoSystem.resolveArtifact( repoSession, request )?.artifact?.file
            // File may not be resolved when "failOnError" is "false" and resolution doesn't succeed.
        }

        a
    }
}
