package com.github.goldin.plugins.silencer

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.LoggingRepositoryListener
import org.apache.maven.cli.AbstractMavenTransferListener
import org.apache.maven.lifecycle.internal.MojoExecutor
import org.apache.maven.plugin.DefaultBuildPluginManager
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.context.Context
import org.codehaus.plexus.context.ContextException
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable
import org.gcontracts.annotations.Requires


/**
 * Mojo for silencing Maven logging.
 */
@SuppressWarnings([ 'GroovyAccessibility' ])
@Mojo( name = 'silence', defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true )
class SilencerMojo extends BaseGroovyMojo implements Contextualizable
{
    @Parameter ( required = false )
    private String enabled

    @Parameter ( required = false )
    private boolean timeExecution = false

    @Parameter ( required = false )
    private String loggerFields


    private final String defaultLoggerFields = '''
    org.apache.maven.plugin.compiler.CompilerMojo:compilerManager.compilers.javac.logger
    org.codehaus.gmaven.plugin.compile.CompileMojo:log
    org.codehaus.gmaven.plugin.compile.TestCompileMojo:log
    '''.stripIndent()


    private PlexusContainer container


    @Override
    @Requires({ context })
    void contextualize ( Context context ) throws ContextException
    {
        this.container = ( PlexusContainer ) context.get( PlexusConstants.PLEXUS_KEY )
    }


    @Override
    void doExecute()
    {
        if ( session.userProperties[ this.class.name ] != null ) { return }

        if (( enabled == null ) || ( enabled == 'true' ) || eval( enabled, Boolean ))
        {
            log.info( 'Silencer Mojo is on - enjoy the silence.' )

            tryIt { updateMavenPluginManager() }
            tryIt { setFieldValue( repoSession.repositoryListener, LoggingRepositoryListener,     'logger', silentLogger )}
            tryIt { setFieldValue( repoSession.transferListener,   AbstractMavenTransferListener, 'out',    nullPrintStream())}
        }

        session.userProperties[ this.class.name ] = 'true'
    }


    void updateMavenPluginManager ()
    {
        final executor = container.lookup( MojoExecutor )

        (( DefaultBuildPluginManager ) executor.pluginManager ).mavenPluginManager =
            new SilentMavenPluginManager(
                this,
                timeExecution,
                (( DefaultBuildPluginManager ) executor.pluginManager ).mavenPluginManager,
                ( defaultLoggerFields + '\n' + ( loggerFields ?: '' )))
    }
}
