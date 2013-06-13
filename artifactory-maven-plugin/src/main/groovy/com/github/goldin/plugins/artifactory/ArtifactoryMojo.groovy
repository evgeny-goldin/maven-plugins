package com.github.goldin.plugins.artifactory

import static com.github.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.execution.ExecutionEvent
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.AbstractMavenLifecycleParticipant
import org.apache.maven.lifecycle.internal.DefaultExecutionEventCatapult
import org.apache.maven.lifecycle.internal.ExecutionEventCatapult
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Component
import org.jfrog.build.api.BuildInfoConfigProperties
import org.jfrog.build.extractor.maven.BuildInfoRecorderLifecycleParticipant


/**
 * Artifactory plugin creating JSON build data.
 */
@Mojo ( name = 'create-build-info', defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true )
class ArtifactoryMojo extends BaseGroovyMojo
{

    @Component( role = ExecutionEventCatapult.class )
    DefaultExecutionEventCatapult eventCatapult

    @Override
    void doExecute ()
    {
        createProperties()
        final recorder = verifyBean().isInstance( container.lookup( AbstractMavenLifecycleParticipant ),
                                                  BuildInfoRecorderLifecycleParticipant )
        recorder.afterSessionStart( session )
        recorder.afterProjectsRead( session )

        eventCatapult.fire( ExecutionEvent.Type.SessionStarted, session, null )
        eventCatapult.fire( ExecutionEvent.Type.ProjectStarted, session, null )
    }


    private void createProperties()
    {
        final mapToString = { Map m -> m.collect { key, value -> "[$key] = [$value]" }.join( '\n' ) }
        log.info( "System properties:\n${ mapToString( System.properties ) }" )
        log.info( "Env variables:\n${ mapToString( System.getenv() ) }" )

        final propertiesFile    = fileBean().tempFile( '.properties' )
        final propertiesContent = this.class.classLoader.getResourceAsStream( 'buildInfo.properties' ).getText( 'UTF-8' )
        propertiesFile.setText( propertiesContent, 'UTF-8' )
        System.setProperty( BuildInfoConfigProperties.PROP_PROPS_FILE, propertiesFile.canonicalPath )
    }
}
