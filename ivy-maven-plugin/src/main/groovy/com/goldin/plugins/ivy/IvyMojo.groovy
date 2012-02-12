package com.goldin.plugins.ivy

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.gcommons.GCommons
import com.goldin.plugins.common.BaseGroovyMojo3
import org.apache.ivy.Ivy
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import org.jfrog.maven.annomojo.annotations.MojoRequiresDependencyResolution
import org.sonatype.aether.impl.internal.DefaultRepositorySystem


/**
 * Plugin that delegates artifacts resolving to Ivy, adds dependencies resolved to the Maven scope or
 * copies them to local directory.
 */
@MojoGoal ( 'ivy' )
@MojoPhase ( 'initialize' )
@MojoRequiresDependencyResolution( 'test' )
class IvyMojo extends BaseGroovyMojo3
{
    public static final String IVY_PREFIX = 'ivy.'


    private IvyHelper ivyHelper


   /**
    * Ivy settings file: http://ant.apache.org/ivy/history/latest-milestone/settings.html.
    */
    @MojoParameter ( required = true )
    public String ivyconf

    /**
     * Ivy file: http://ant.apache.org/ivy/history/latest-milestone/ivyfile.html.
     */
    @MojoParameter ( required = false )
    public String ivy

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
     * Whether plugin should log verbosely (verbose = true), regularly (verbose is null) or not at all (verbose = false).
     */
    @MojoParameter ( required = false )
    public Boolean verbose
    private boolean logVerbosely(){ ( verbose ) }
    private boolean logNormally (){ ( verbose ) || ( verbose == null ) }


    /**
     * Whether execution should fail if resolving artifacts doesn't succeed.
     */
    @MojoParameter ( required = false )
    public boolean failOnError = true



    @Override
    @Requires({ this.ivyconf })
    void doExecute ()
    {
        ivyHelper = new IvyHelper( url( this.ivyconf ), logVerbosely(), failOnError )
        addIvyResolver()

        if ( ivy || dependencies )
        {
            assert ( scope || dir ), "Either <scope> or <dir> (or both) needs to be specified when <ivy> or <dependencies> are used."
        }

        if ( scope || dir )
        {
            assert ( ivy || dependencies ), "Either <ivy> or <dependencies> (or both) needs to be specified when <scope> or <dir> are used."
            final artifacts = resolveArtifacts(( ivy ? url( ivy ) : null ), dependencies )

            if ( artifacts )
            {
                if ( scope ){ addArtifactsToScope  ( scope, artifacts ) }
                if ( dir   ){ copyArtifactsToDir   ( dir,   artifacts ) }
            }
        }
    }


    /**
     * Adds Ivy resolver to Aether RepositorySystem based on settings file specified.
     * @return configured {@link Ivy} instance.
     */
    @Requires({ repoSystem && ivyHelper && ivyHelper.ivyconfUrl })
    void addIvyResolver ()
    {
        assert repoSystem instanceof DefaultRepositorySystem
        (( DefaultRepositorySystem ) repoSystem ).artifactResolver = new IvyArtifactResolver( repoSystem.artifactResolver, ivyHelper )
        assert repoSystem.artifactResolver instanceof IvyArtifactResolver

        if ( logNormally())
        {
            log.info( "Added Ivy artifacts resolver based on \"${ ivyHelper.ivyconfUrl }\"" )
        }
    }


    /**
     * Converts path specified to URL.
     *
     * @param s path of disk file or jar-located resource.
     * @return path's URL
     */
    @Requires({ s })
    @Ensures({ result })
    URL url( String s )
    {
        s.trim().with { ( startsWith( 'jar:' ) || startsWith( 'file:' )) ? new URL( s ) : new File( s ).toURL() }
    }


    /**
     * Resolves dependencies specified and retrieves their local paths.
     *
     * @param ivyFile      "ivy.xml" file
     * @param dependencies Maven-style dependencies
     * @return             artifacts resolved or empty list if {@link #failOnError} is "false".
     */
    @Requires({ ivyFile || dependencies })
    @Ensures({ result.every{ it.file.file } })
    List<Artifact> resolveArtifacts ( URL ivyFile, ArtifactItem[] dependencies )
    {
        List<Artifact> ivyArtifacts   = ( ivyFile      ? ivyHelper.resolve( ivyFile ) : [] )
        List<Artifact> mavenArtifacts = ( dependencies ? resolveMavenDependencies( dependencies  ) : [] )
        ( ivyArtifacts + mavenArtifacts ).findAll{ it.file } // Some artifacts may have file undefined if {@link #failOnError} is "false".
    }


    /**
     * Resolve dependencies specified inline as Maven {@code <dependencies>}.
     *
     * @param dependencies dependencies to resolve
     * @return artifacts resolved
     */
    @Requires({ dependencies })
    @Ensures({ result && ( result.size() == dependencies.size()) })
    List<Artifact> resolveMavenDependencies ( ArtifactItem[] dependencies )
    {
        dependencies.collect {
            ArtifactItem d ->
            resolveArtifact( artifact( d.groupId, d.artifactId, d.version, d.type, d.classifier, null ))
        }
    }


    /**
     * Adds artifacts to the scope specified.
     *
     * @param scope     Maven scope to add artifacts to: "compile", "runtime", "test", etc.
     * @param artifacts dependencies to add to the scope
     */
    @Requires({ scope && artifacts && artifacts.every{ it.file.file } })
    void addArtifactsToScope ( String scope, List<Artifact> artifacts )
    {
        /**
         * Maven hacks are coming thanks to Groovy being able to read/write private fields.
         */
        if ( scope == 'plugin-runtime' )
        {   /**
             * Adding jars to plugin's classloader.
             */
            assert this.class.classLoader instanceof URLClassLoader
            artifacts*.file.each {
                (( URLClassLoader ) this.class.classLoader ).addURL( it.toURL())
            }
        }
        else
        {   /**
             * Adding jars to Maven's scope and compilation classpath.
             */
            artifacts.each {
                Artifact a ->
                a.scope = scope
                assert a.artifactHandler instanceof DefaultArtifactHandler
                (( DefaultArtifactHandler ) a.artifactHandler ).addedToClasspath = true
            }
            project.setResolvedArtifacts( new HashSet<Artifact>( project.resolvedArtifacts + artifacts ))
        }

        final message = "${ artifacts.size() } artifact${ GCommons.general().s( artifacts.size())} added to \"$scope\" scope: "

        if ( logVerbosely())
        {
            log.info( message + artifacts.collect { "\"$it\" (${ it.file })"  })
        }
        else if ( logNormally())
        {
            log.info( message + artifacts )
        }
    }


    /**
     * Copies artifacts to directory specified.
     *
     * @param directory directory to copy the artifacts to
     * @param artifacts artifacts to copy
     */
    @Requires({ directory && artifacts && artifacts.every{ it.file.file } })
    @Ensures({ artifacts.every{ new File( directory, it.file.name ).file } })
    void copyArtifactsToDir ( File directory, List<Artifact> artifacts )
    {
        Map<Artifact, File> filesCopied = artifacts.inject([:]){
            Map m, Artifact a -> m[ a ] = file().copy( a.file, directory ).canonicalPath
                                 m
        }

        final message = "${ artifacts.size() } artifact${ GCommons.general().s(artifacts.size())} copied to \"${ directory.canonicalPath }\": "

        if ( logVerbosely())
        {
            log.info( message + artifacts.collect { "\"$it\" => \"${ filesCopied[ it ] }\""  })
        }
        else if ( logNormally())
        {
            log.info( message + artifacts )
        }
    }
}
