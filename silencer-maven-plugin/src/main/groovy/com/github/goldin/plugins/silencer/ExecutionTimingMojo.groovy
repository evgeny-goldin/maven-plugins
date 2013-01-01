package com.github.goldin.plugins.silencer

import org.apache.maven.plugin.Mojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.logging.console.ConsoleLogger
import org.gcontracts.annotations.Requires


/**
 * Intercepts and times {@link Mojo#execute()} call.
 */
class ExecutionTimingMojo
{
    @Delegate
    private final Mojo    delegate
    private final boolean logMojoClass
    private final Logger  logger


    @Requires({ delegate })
    ExecutionTimingMojo ( Mojo delegate, boolean logMojoClass )
    {
        this.delegate     = delegate
        this.logMojoClass = logMojoClass
        this.logger       = new ConsoleLogger( Logger.LEVEL_INFO, this.class.name )
    }


    @Override
    @SuppressWarnings([ 'JavaStylePropertiesInvocation', 'GroovyGetterCallCanBePropertyAccess' ])
    void execute () throws MojoExecutionException, MojoFailureException
    {
        final t = System.currentTimeMillis()
        try     { delegate.execute() }
        finally { logger.info( "[${ System.currentTimeMillis() - t }] ms" +
                               ( logMojoClass ? " (${ delegate.getClass().name })" : '' )) }
    }
}
