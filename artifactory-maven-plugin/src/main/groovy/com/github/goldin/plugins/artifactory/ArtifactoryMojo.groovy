package com.github.goldin.plugins.artifactory

import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo


/**
 *
 */
@Mojo ( name = 'artifactory', defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true )
class ArtifactoryMojo extends BaseGroovyMojo
{
    @Override
    void doExecute ()
    {

    }
}
