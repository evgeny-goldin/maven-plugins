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
    private final Mojo delegate
    private final Logger logger = new ConsoleLogger( Logger.LEVEL_INFO, this.class.name )


    @Requires({ delegate })
    ExecutionTimingMojo ( Mojo delegate )
    {
        this.delegate = delegate
    }


    @Override
    void execute () throws MojoExecutionException, MojoFailureException
    {
        final t = System.currentTimeMillis()
        try { delegate.execute() }
        finally{ logger.info( "[${ System.currentTimeMillis() - t }] ms" ) }
    }
}
