package com.goldin.plugins.ivy

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.BaseGroovyMojo3
import org.apache.ivy.Ivy
import org.apache.maven.artifact.Artifact
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
    static final String IVY_PREFIX = 'ivy.'

    /**
     * Whether Ivy resolver should be added to Maven for subsequent resolutions of Ivy artifacts by other plugins.
     */
    @MojoParameter ( required = false )
    public boolean addIvyResolver = false

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
     * Single Maven-style {@code <dependency>}.
     */
    @MojoParameter ( required = false )
    public ArtifactItem dependency

    /**
     * Multiple Maven-style {@code <dependencies>}.
     */
    @MojoParameter ( required = false )
    public ArtifactItem[] dependencies

    /**
     * Comma-separated Maven scope to add the dependencies resolved to: "compile", "compile, runtime", "test", etc.
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
        final helper = new IvyHelper( url( this.ivyconf ), logVerbosely(), this.failOnError )

        if ( addIvyResolver )
        {
            addIvyResolver( helper )
        }

        ArtifactItem[] dependencies = general().list( this.dependencies, this.dependency ) as ArtifactItem[]

        if ( ivy || dependencies )
        {
            assert ( scope || dir ), "Either <scope> or <dir> (or both) needs to be specified when <ivy> or <dependencies> are used."
        }

        if ( scope || dir )
        {
            assert ( ivy || dependencies ), "Either <ivy> or <dependencies> (or both) needs to be specified when <scope> or <dir> are used."
            final artifacts = resolveArtifacts(( ivy ? url( ivy ) : null ), dependencies, helper ).asImmutable()

            if ( artifacts )
            {
                if ( scope ){

                    addToScopes( artifacts, scope, project )

                    if ( logVerbosely() || logNormally())
                    {
                        log.info( "${ helper.artifactsNumber( artifacts )} added to \"$scope\" scope: " +
                                  ( logVerbosely() ? helper.artifactsToString( artifacts ) : artifacts  ))

                    }
                }

                if ( dir ){

                    copyToDir( artifacts, dir, logVerbosely())

                    if ( logVerbosely() || logNormally() )
                    {
                        log.info( "${ helper.artifactsNumber( artifacts )} copied to \"${ dir.canonicalPath }\"" +
                                  ( logVerbosely() ? '' /* Artifact paths are logged already */ : ': ' + artifacts ))
                    }
                }
            }
        }
    }


    /**
     * Adds Ivy resolver to Aether RepositorySystem based on settings file specified.
     * @return configured {@link Ivy} instance.
     */
    @Requires({ repoSystem && helper && helper.ivyconfUrl })
    void addIvyResolver ( IvyHelper helper )
    {
        assert repoSystem instanceof DefaultRepositorySystem
        (( DefaultRepositorySystem ) repoSystem ).artifactResolver = new IvyArtifactResolver( repoSystem.artifactResolver, helper )
        assert repoSystem.artifactResolver instanceof IvyArtifactResolver

        if ( logNormally())
        {
            log.info( "Added Ivy artifacts resolver based on \"${ helper.ivyconfUrl }\"" )
        }
    }


    /**
     * Resolves dependencies specified and retrieves their local paths.
     *
     * @param ivyFile      "ivy.xml" file
     * @param dependencies Maven-style dependencies
     * @param helper       {@link IvyHelper} instance to use
     * @return             artifacts resolved or empty list if {@link #failOnError} is "false".
     */
    @Requires({ ( ivyFile || dependencies ) && helper })
    @Ensures({ result.every{ it.file.file } })
    List<Artifact> resolveArtifacts ( URL ivyFile, ArtifactItem[] dependencies, IvyHelper helper )
    {
        List<Artifact> ivyArtifacts   = ( ivyFile      ? helper.resolve( ivyFile )                         : [] )
        List<Artifact> mavenArtifacts = ( dependencies ? resolveMavenDependencies( helper, dependencies  ) : [] )
        ( ivyArtifacts + mavenArtifacts ).findAll{ it.file } // Some artifacts may have file undefined if {@link #failOnError} is "false".
    }


    /**
     * Resolve dependencies specified inline as Maven {@code <dependencies>}.
     *
     * @param helper       {@link IvyHelper} to use for resolution of dependencies located in Ivy repo
     * @param dependencies dependencies to resolve
     * @return artifacts resolved
     */
    @Requires({ helper && dependencies })
    @Ensures({ result && ( result.size() == dependencies.size()) })
    List<Artifact> resolveMavenDependencies ( IvyHelper helper, ArtifactItem[] dependencies )
    {
        dependencies.collect {
            ArtifactItem d ->

            d.groupId.startsWith( IVY_PREFIX ) ?
                helper.resolve(            d.groupId, d.artifactId, d.version, d.type, d.classifier ) :
                resolveArtifact( artifact( d.groupId, d.artifactId, d.version, d.type, d.classifier, null ))
        }
    }
}
