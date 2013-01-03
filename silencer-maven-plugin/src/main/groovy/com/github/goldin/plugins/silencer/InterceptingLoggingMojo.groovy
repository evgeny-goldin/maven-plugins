package com.github.goldin.plugins.silencer

import org.apache.maven.plugin.Mojo
import org.apache.maven.plugin.MojoExecution
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
    private SilencerMojo        parentMojo
    @Delegate
    private final Mojo          delegate
    private final MojoExecution mojoExecution
    private final Logger        logger

    class ExecutionTime { long time; Mojo mojo; MojoExecution execution }
    private static final List<ExecutionTime> executions = []


    @Requires({ parentMojo && delegate && mojoExecution })
    InterceptingLoggingMojo ( SilencerMojo parentMojo, Mojo delegate, MojoExecution mojoExecution )
    {
        this.parentMojo    = parentMojo
        this.delegate      = delegate
        this.mojoExecution = mojoExecution
        this.logger        = new ConsoleLogger( Logger.LEVEL_INFO, this.class.name )
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
            logger.info( "$timeMillis ms" + ( parentMojo.logMojo ? " - ${ delegate.getClass().name }" : '' ))
        }

        if ( ! parentMojo.logSummary ) { return }

        executions << new ExecutionTime( time: timeMillis, mojo: this.delegate, execution: this.mojoExecution )

        if ( parentMojo.mojoExecutor.mojoExecutions )
        {
            final firstExecution = ( parentMojo.mojoExecutor.mojoExecutions[ 0  ] == this.mojoExecution )
            final lastExecution  = ( parentMojo.mojoExecutor.mojoExecutions[ -1 ] == this.mojoExecution )

            if ( firstExecution ) { executions.clear() }
            if ( lastExecution  )
            {
                logger.info( '' )
                logger.info( 'Summary:' )

                final timePadding  = executions*.time*.toString()*.length().max()

                executions.sort { ExecutionTime e1, ExecutionTime e2 -> e2.time <=> e1.time }. // Sort in decreasing order
                take ( parentMojo.topN < 1 ? Integer.MAX_VALUE : parentMojo.topN ).
                each { ExecutionTime e ->
                    logger.info( "${ e.time } ms".padRight( timePadding + 3 )                            +
                                 " - ${ e.execution.mojoDescriptor.roleHint.replaceAll( /^.+?:/, '' )} " + // Plugin's groupId is removed
                                 "(${ e.execution.executionId })"                                        +
                                 ( parentMojo.logMojo ? " (${ e.mojo.class.name })" : '' ))
                }
            }
        }
    }
}

