package com.goldin.plugins.common

import static com.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.artifact.Artifact
import org.jfrog.maven.annomojo.annotations.MojoComponent
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.RepositorySystemSession
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.resolution.ArtifactRequest
import org.sonatype.aether.util.artifact.DefaultArtifact


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
     * Resolves Maven {@link Artifact} to local {@link File}.
     *
     * @param artifact Maven artifact to resolve
     * @return local file where it is downloaded and stored
     */
    protected final File resolveArtifact( Artifact a )
    {
        final request = new ArtifactRequest( new DefaultArtifact( a.groupId, a.artifactId, a.classifier, a.type, a.version ),
                                             remoteRepos, null )
        verify().file( repoSystem.resolveArtifact( repoSession, request ).artifact.file )
    }
}
