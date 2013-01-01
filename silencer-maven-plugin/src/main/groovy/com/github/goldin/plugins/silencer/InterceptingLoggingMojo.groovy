package com.github.goldin.plugins.silencer

import org.apache.maven.plugin.Mojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.logging.console.ConsoleLogger
import org.gcontracts.annotations.Requires


/**
 * Intercepts and logs {@link Mojo#execute()} call.
 */
class InterceptingLoggingMojo
{
    private SilencerMojo  parentMojo
    @Delegate
    private final Mojo    delegate
    private final Logger  logger


    @Requires({ parentMojo && delegate })
    InterceptingLoggingMojo ( SilencerMojo parentMojo, Mojo delegate )
    {
        this.parentMojo = parentMojo
        this.delegate   = delegate
        this.logger     = new ConsoleLogger( Logger.LEVEL_INFO, this.class.name )
    }


    @Override
    @SuppressWarnings([ 'JavaStylePropertiesInvocation', 'GroovyGetterCallCanBePropertyAccess' ])
    void execute ()
         throws MojoExecutionException, MojoFailureException
    {
        final t = System.currentTimeMillis()

        try     { delegate.execute() }
        finally { logExecution( System.currentTimeMillis() - t ) }
    }


    @SuppressWarnings([ 'JavaStylePropertiesInvocation', 'GroovyGetterCallCanBePropertyAccess' ])
    @Requires({ timeMillis >= 0 })
    private void logExecution( long timeMillis )
    {
        if ( parentMojo.logTime )
        {
            logger.info( "[$timeMillis] ms" + ( parentMojo.logMojo ? " (${ delegate.getClass().name })" : '' ))
        }

        if ( parentMojo.logSummary )
        {
            final executions = parentMojo.mojoExecutor.mojoExecutions
            int j = 5
        }
    }
}
