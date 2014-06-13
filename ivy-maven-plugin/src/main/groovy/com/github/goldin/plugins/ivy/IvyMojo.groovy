package com.github.goldin.plugins.ivy

import static com.github.goldin.plugins.common.ConversionUtils.*
import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.ivy.Ivy
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.eclipse.aether.internal.impl.DefaultRepositorySystem


/**
 * Plugin that delegates artifacts resolving to Ivy, adds dependencies resolved to the Maven scope or
 * copies them to local directory.
 */
@Mojo ( name = 'ivy', defaultPhase = LifecyclePhase.INITIALIZE, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
class IvyMojo extends BaseGroovyMojo
{
    /**
     * Whether Ivy resolver should be added to Maven for subsequent resolutions of Ivy artifacts by other plugins.
     */
    @Parameter ( required = false )
    private boolean addIvyResolver = false

   /**
    * Ivy settings file: http://ant.apache.org/ivy/history/latest-milestone/settings.html.
    */
    @Parameter ( required = true )
    private String ivyconf

    /**
     * Ivy file: http://ant.apache.org/ivy/history/latest-milestone/ivyfile.html.
     */
    @Parameter ( required = false )
    private String ivy

    /**
     * Single Maven-style {@code <dependency>}.
     */
    @Parameter ( required = false )
    private ArtifactItem dependency

    /**
     * Multiple Maven-style {@code <dependencies>}.
     */
    @Parameter ( required = false )
    private ArtifactItem[] dependencies

    /**
     * Comma-separated Maven scope to add the dependencies resolved to: "compile", "compile, runtime", "test", etc.
     * Similar to Ivy's <cachepath>: http://ant.apache.org/ivy/history/latest-milestone/use/cachepath.html.
     */
    @Parameter ( required = false )
    private String scope

    /**
     * Directory to copy resolved dependencies to.
     */
    @Parameter ( required = false )
    private File dir

    /**
     * Whether plugin should log verbosely (verbose = true), regularly (verbose is null) or not at all (verbose = false).
     */
    @Parameter ( required = false )
    private Boolean verbose
    private boolean logVerbosely(){ ( verbose ) }
    private boolean logNormally (){ ( verbose ) || ( verbose == null ) }

    /**
     * Whether execution should fail if resolving artifacts doesn't succeed.
     */
    @Parameter ( required = false )
    private boolean failOnError = true


    @Override
    @Requires({ this.ivyconf })
    void doExecute ()
    {
        final helper = new IvyHelper( this, url( this.ivyconf ), logVerbosely(), this.failOnError, this.session.offline )

        if ( addIvyResolver )
        {
            addIvyResolver( helper )
        }

        ArtifactItem[] dependencies = generalBean().list( this.dependencies, this.dependency ) as ArtifactItem[]

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

                    helper.addToScopes( artifacts, scope )

                    if ( logVerbosely() || logNormally())
                    {
                        log.info( "${ helper.artifactsNumber( artifacts )} added to \"$scope\" scope: " +
                                  ( logVerbosely() ? helper.artifactsToString( artifacts ) : artifacts  ))

                    }
                }

                if ( dir ){

                    helper.copyToDir( artifacts, dir, logVerbosely())

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
    @SuppressWarnings([ 'GroovyAccessibility' ])
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
    @Ensures({ result != null })
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
                helper.resolve(                    d.groupId, d.artifactId, d.version,     d.type, d.classifier ) :
                downloadArtifact( toMavenArtifact( d.groupId, d.artifactId, d.version, '', d.type, d.classifier, false ),
                                  logVerbosely(),
                                  failOnError )
        }
    }
}
