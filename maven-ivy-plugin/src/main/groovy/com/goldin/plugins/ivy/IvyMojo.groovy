package com.goldin.plugins.ivy

import com.goldin.gcommons.GCommons
import com.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.artifact.versioning.VersionRange
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.sonatype.aether.RepositorySystem
import org.jfrog.maven.annomojo.annotations.*


/**
 * Plugin that delegates artifacts resolving to Ivy, adds dependencies resolved to the Maven scope or
 * copies them to local directory.
 */
@MojoGoal ( 'ivy' )
@MojoPhase ( 'initialize' )
@MojoRequiresDependencyResolution( 'test' )
class IvyMojo extends BaseGroovyMojo
{
   /**
    * Ivy settings file: http://ant.apache.org/ivy/history/latest-milestone/settings.html.
    */
    @MojoParameter ( required = true )
    public File ivyconf

    /**
     * Ivy file: http://ant.apache.org/ivy/history/latest-milestone/ivyfile.html.
     */
    @MojoParameter ( required = false )
    public File ivy

    /**
     * Maven-style {@code <dependencies>}.
     */
    @MojoParameter ( required = false )
    public ArtifactItem[] dependencies

    /**
     * Maven scope to add the dependencies resolved to: "compile", "runtime", "test", etc.
     * Similar to Ivy's <cachepath>: http://ant.apache.org/ivy/history/latest-milestone/use/cachepath.html.
     */
    @MojoParameter ( required = false )
    public String scope

    /**
     * Directory to copy resolved dependencies to.
     */
    @MojoParameter ( required = false )
    public File dir

    /**
     * Aether components:
     * http://aether.sonatype.org/using-aether-in-maven-plugins.html
     * https://docs.sonatype.org/display/AETHER/Home
     */

    @MojoComponent
    public RepositorySystem repoSystem


    private final DefaultArtifactHandler handler = new DefaultArtifactHandler()


    @Override
    @Requires({ ivyconf.file })
    void doExecute ()
    {
        handler.addedToClasspath = true

        // Ain't that cool to assign to private?!
        repoSystem.artifactResolver = new IvyArtifactResolver( repoSystem.artifactResolver, ivyconf )

        if ( scope || dir )
        {
            final dependencies = resolveDependencies( ivy, dependencies )
            if ( scope ){ addArtifacts  ( scope, dependencies ) }
            if ( dir  ) { copyArtifacts ( dir,   dependencies ) }
        }
    }


    /**
     * Resolves dependencies specified and retrieves their local paths.
     *
     * @param ivyFile      "ivy.xml" file
     * @param dependencies Maven-style dependencies
     * @return             local paths of dependencies resolved
     */
    @Requires({ ivyFile?.file || dependencies })
    @Ensures({ result && result.every{ it.file.file } })
    List<Artifact> resolveDependencies( File ivyFile, ArtifactItem[] dependencies )
    {
        final log4j = new DefaultArtifact( 'log4j', 'log4j', VersionRange.createFromVersion( '1.2.16' ), 'compile', 'jar', null, handler )
        log4j.file  = new File( '/Users/evgenyg/.m2/repository/log4j/log4j/1.2.16/log4j-1.2.16.jar' )

        [ log4j ]
    }


    /**
     * Adds artifacts to the scope specified.
     *
     * @param scope     Maven scope to add artifacts to: "compile", "runtime", "test", etc.
     * @param artifacts dependencies to add to the scope
     */
    @Requires({ scope && artifacts && artifacts.every{ it.file.file } })
    void addArtifacts ( String scope, List<Artifact> artifacts )
    {
        artifacts.each { it.scope   = scope }
        project.artifacts           = new HashSet<Artifact>( project.artifacts           + artifacts )
        project.dependencyArtifacts = new HashSet<Artifact>( project.dependencyArtifacts + artifacts )
        log.info( "${ artifacts.size() } artifact${ GCommons.general().s( artifacts.size())} added to \"$scope\" scope: " +
                  artifacts )
    }


    /**
     * Copies artifacts to directory specified.
     *
     * @param directory directory to copy the artifacts to
     * @param artifacts artifacts to copy
     */
    @Requires({ directory && artifacts && artifacts.every{ it.file.file } })
    @Ensures({ artifacts.every{ new File( directory, it.file.name ).file } })
    void copyArtifacts ( File directory, List<Artifact> artifacts )
    {
        artifacts*.file.each {
            GCommons.file().copy( it, directory )
        }

        log.info( "${ artifacts.size() } artifact${ GCommons.general().s( artifacts.size())} copied \"${ directory.canonicalPath }\": " +
                  artifacts )
    }
}
