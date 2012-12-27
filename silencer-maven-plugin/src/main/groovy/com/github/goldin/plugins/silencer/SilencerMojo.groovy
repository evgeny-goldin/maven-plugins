package com.github.goldin.plugins.silencer

import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.lifecycle.internal.MojoExecutor
import org.apache.maven.plugin.DefaultBuildPluginManager
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import java.lang.reflect.Field
import java.lang.reflect.Modifier


/**
 * Mojo for silencing Maven logging.
 */
@Mojo( name = 'silence', defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true )
class SilencerMojo extends BaseGroovyMojo
{

    @Override
    void doExecute ( )
    {
        updateMavenPluginManager()
        updateRepositoryLogger()
    }

    private void updateMavenPluginManager ( )
    {
        try
        {
            final executor = session.container.lookup( MojoExecutor )
            (( DefaultBuildPluginManager ) executor.pluginManager ).mavenPluginManager =
                new SilencerMavenPluginManager((( DefaultBuildPluginManager ) executor.pluginManager ).mavenPluginManager )
        }
        catch ( Throwable ignored ){}
    }


    private void updateRepositoryLogger ( )
    {
        try
        {
            final listener            = session.repositorySession.repositoryListener
            final loggerField         = listener.class.getDeclaredField( 'logger' )
            final modifiersField      = Field.class.getDeclaredField( 'modifiers' )
            loggerField.accessible    = true
            modifiersField.accessible = true

            modifiersField.setInt( loggerField , loggerField.modifiers & ~Modifier.FINAL )
            loggerField.set( listener , new SilencerLogger() )
        }
        catch ( Throwable ignored ){}
    }
}
