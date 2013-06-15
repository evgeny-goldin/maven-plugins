package com.github.goldin.plugins.artifactory

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.AbstractMavenLifecycleParticipant
import org.apache.maven.execution.ExecutionEvent
import org.apache.maven.lifecycle.internal.DefaultExecutionEventCatapult
import org.apache.maven.lifecycle.internal.ExecutionEventCatapult
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.build.api.BuildInfoConfigProperties
import org.jfrog.build.extractor.maven.BuildInfoRecorder
import org.jfrog.build.extractor.maven.BuildInfoRecorderLifecycleParticipant


/**
 * Artifactory plugin creating JSON build data.
 */
@Mojo ( name = 'build-info', defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true )
class ArtifactoryMojo extends BaseGroovyMojo
{
    @Component( role = AbstractMavenLifecycleParticipant )
    BuildInfoRecorderLifecycleParticipant listener

    @Component( role = ExecutionEventCatapult )
    DefaultExecutionEventCatapult eventCatapult

    @Parameter
    private File properties

    @Parameter
    private Map<String,String> propertiesMap


    @Requires({ listener && eventCatapult })
    @Override
    void doExecute ()
    {
        session.executionProperties[ 'maven.deploy.skip' ] = 'true'

        final skip = ( ! session.goals.grep( 'deploy' )) ||
                     ( session.request.executionListener instanceof BuildInfoRecorder )

        if ( skip ) { return }

        mergeProperties()

        listener.afterSessionStart( session )
        listener.afterProjectsRead( session )

        eventCatapult.fire( ExecutionEvent.Type.SessionStarted, session, null )
        eventCatapult.fire( ExecutionEvent.Type.ProjectStarted, session, null )
    }


    /**
     * Merges *.properties files with <configuration> values and writes a new *.properties file to be picked up later
     * by the original Maven listener.
     */
    private void mergeProperties ()
    {
        Map<String,String> map = [:]
        final systemProperties = System.getProperty( BuildInfoConfigProperties.PROP_PROPS_FILE )?.with {
            String fileName -> fileName ? new File( fileName ) : null
        }

        if ( systemProperties ){ map += loadProperties( systemProperties )}
        if ( properties       ){ map += loadProperties( properties )}
        if ( propertiesMap    ){ map += propertiesMap }

        map = ( Map<String,String> ) map.collectEntries { String key, String value -> [ key, updateValue( value )] }

        final propertiesFile = fileBean().tempFile( '-buildInfo.properties' )
        propertiesFile.withWriter { Writer w -> new Properties( map ).store( w, 'Build Info Properties' )}
        propertiesFile.deleteOnExit()

        System.setProperty( BuildInfoConfigProperties.PROP_PROPS_FILE, propertiesFile.canonicalPath )
    }


    /**
     * Loads {@code Properties} instance from the file specified.
     */
    @Requires({ f?.file })
    @Ensures ({ result != null })
    private Map<String,String> loadProperties( File f )
    {
        f.withReader {
            Reader r ->
            final p = new Properties()
            p.load( r )
            ( Map<String,String> ) p
        }
    }


    /**
     * Updates all "${VAR}" entries in the value specified to their corresponding environment variables or system properties.
     */
    private String updateValue( String value )
    {
        value?.replaceAll( /\$\{([^}]+)\}/ ){
            final String var = it[ 1 ]
            System.getenv( var ) ?: System.getProperty( var ) ?: '${' + var + '}'
        }
    }
}
