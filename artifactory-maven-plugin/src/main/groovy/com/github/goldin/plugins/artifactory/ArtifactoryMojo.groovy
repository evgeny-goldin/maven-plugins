package com.github.goldin.plugins.artifactory

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.AbstractMavenLifecycleParticipant
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.jfrog.build.extractor.maven.BuildInfoRecorderLifecycleParticipant


/**
 * Artifactory plugin creating JSON build data.
 */
@Mojo ( name = 'create-build-info', defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true )
class ArtifactoryMojo extends BaseGroovyMojo
{
    @Override
    void doExecute ()
    {
        final recorder = verifyBean().isInstance( container.lookup( AbstractMavenLifecycleParticipant ),
                                                  BuildInfoRecorderLifecycleParticipant )
        recorder.afterSessionStart( session )
        recorder.afterProjectsRead( session )
    }
}
