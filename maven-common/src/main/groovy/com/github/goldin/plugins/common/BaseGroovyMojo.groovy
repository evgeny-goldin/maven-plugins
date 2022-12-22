package com.github.goldin.plugins.common

import static com.github.goldin.plugins.common.ConversionUtils.*
import static com.github.goldin.plugins.common.GMojoUtils.*
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import org.apache.ivy.util.Message
import org.apache.maven.artifact.Artifact
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.tools.ant.DefaultLogger
import org.codehaus.gmaven.mojo.GroovyMojo
import org.codehaus.plexus.DefaultPlexusContainer
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.slf4j.LoggerFactory
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Field
import java.lang.reflect.Modifier


/**
 * Base GroovyMojo class
 */
@SuppressWarnings([ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ])
abstract class BaseGroovyMojo extends GroovyMojo
{
    static final String SILENCE = 'SILENCE'

    @Component
    DefaultPlexusContainer container

    @Parameter ( required = true, defaultValue = '${project}' )
    MavenProject project

    @Parameter ( required = true, defaultValue = '${session}' )
    MavenSession session

    @Parameter ( required = true, defaultValue = '${project.basedir}' )
    File basedir

    @Parameter ( required = true, defaultValue = '${project.build.outputDirectory}' )
    private   File outputDirectory
    File outputDirectory() { fileBean().mkdirs( this.outputDirectory ) }

    @Parameter
    private String runIf

    /**
     * Aether components:
     * http://www.sonatype.org/aether/
     * http://eclipse.org/aether/
     * https://docs.sonatype.org/display/AETHER/Home
     * http://aether.sonatype.org/using-aether-in-maven-plugins.html
     */

    @Component
    RepositorySystem repoSystem

    @Parameter ( defaultValue = '${repositorySystemSession}', readonly = true )
    RepositorySystemSession repoSession

    @Parameter ( defaultValue = '${project.remoteProjectRepositories}', readonly = true )
    List<RemoteRepository> remoteRepos


    /**
     * Resolves local {@link File} of Maven {@link Artifact} and updates it.
     *
     * @param artifact    Maven artifact to resolve
     * @param verbose     whether resolving process should be logged
     * @param failOnError whether execution should fail if failed to resolve an artifact
     * @return            same artifact with its local file set
     *
     * @throws RuntimeException if 'failOnError' is true and resolution fails
     */
    @Requires({ artifact })
    @Ensures({ result.is( artifact ) })
    final Artifact downloadArtifact ( Artifact artifact, boolean verbose, boolean failOnError )
    {
        final errorMessage = "Failed to resolve ${ artifact.optional ? 'optional artifact ' : '' }[$artifact]"

        if ( ! artifact.file )
        {
            final request = new ArtifactRequest( toAetherArtifact( artifact ), remoteRepos, null )
            try
            {
                if ( verbose ) { log.info( "Resolving [$artifact]: optional [$artifact.optional], failOnError [$failOnError]" ) }
                artifact.file = repoSystem.resolveArtifact( repoSession, request ).artifact?.file
                if ( verbose ) { log.info( "Resolving [$artifact]: done - [$artifact.file]" ) }
            }
            catch ( e )
            {
                failOrWarn((( ! artifact.optional ) && failOnError ), errorMessage, e )
                return artifact
            }
        }

        if ( ! artifact.file?.file )
        {
            failOrWarn((( ! artifact.optional ) && failOnError ), errorMessage )
        }

        artifact
    }


    /**
     * Retrieves object's field value.
     *
     * @param o          object owning the field
     * @param c          object's expected class
     * @param fieldName  name of the field
     * @return object's field value.
     */
    @Requires({ ( o != null ) && c && fieldName })
    @SuppressWarnings([ 'JavaStylePropertiesInvocation', 'GroovyGetterCallCanBePropertyAccess' ])
    final Object getFieldValue( Object o, Class c = Object, String fieldName )
    {
        assert c.isInstance( o ), "Object [$o][${ o.getClass().name }] is not an instance of [$c.name]"

        if ( o instanceof Map ){ return o[ fieldName ] }

        final  field = ReflectionUtils.findField( o.getClass(), fieldName )
        assert field, "Unable to find field [$fieldName] on object [$o][${ o.getClass().name }]"
        field.accessible = true
        field.get( o )
    }


    /**
     * Sets object's field to the value specified.
     *
     * @param o           object owning the field
     * @param objectClass object's expected class
     * @param fieldName   name of the field
     * @param fieldValue value to set to the field
     */
    @Requires({ ( o != null ) && objectClass && fieldName })
    final void setFieldValue ( Object o, Class objectClass = Object, String fieldName, Object fieldValue )
    {
        assert objectClass.isInstance( o ), "Object [$o][${ o.class.name }] is not an instance of [$objectClass.name]"
        assert ( ! ( o instanceof Map )) // With Map o.class = o['class']

        Field  field = ReflectionUtils.findField( o.class, fieldName )
        assert field, "Unable to find field [$fieldName] on object [$o][${ o.class.name }]"
        field.accessible   = true
        final currentValue = field.get( o )

        if ( currentValue == fieldValue ){ return }

        if ( o.class.name.startsWith( 'org.codehaus.gmaven.plugin.compile.' ) && ( fieldName == 'log' ))
        {   /**
             * 'o'                 - org.codehaus.gmaven.plugin.compile.CompileMojo or
             *                       org.codehaus.gmaven.plugin.compile.TestCompileMojo
             * 'o.log'             - org.sonatype.gossip.Gossip.LoggerImpl
             * 'o.log.cachedLevel' - org.sonatype.gossip.Level
             */
            final errorLevel = Enum.valueOf(( Class<Enum> ) o.class.classLoader.loadClass( 'org.sonatype.gossip.Level' ), 'ERROR' )
            setFieldValue( currentValue, 'cachedLevel', errorLevel )
        }
        else
        {
            final boolean isAssignable = ( fieldValue == null )   ||
                                         ( field.type.primitive ) ||
                                         field.type.isInstance( fieldValue )
            assert isAssignable,
                   "Field [$field.name][${ field.type.name }] of [${ o.class.name }] " +
                   "is not assignment-compatible with [$fieldValue][${ fieldValue.class.name }]"

            if ( Modifier.isFinal( field.modifiers ))
            {
                final modifiersField      = Field.class.getDeclaredField( 'modifiers' )
                modifiersField.accessible = true
                modifiersField.setInt( field, field.modifiers & ~Modifier.FINAL )
            }

            field.set( o, fieldValue )
        }
    }


    /**
     * Attempts to perform an action specified, prints a stack trace if fails.
     * @param action action to perform
     */
    void tryIt ( Closure action )
    {
        try { action.call() }
        catch ( Throwable e ){ e.printStackTrace() }
    }


    /**
     * {@link #execute()} replacement to be overridden by subclasses
     */
    abstract void doExecute()


    /**
     * Executes the Mojo.
     */
    @Override
    @Requires({ pluginContext && log && project && session })
    final void execute()
    {
        if ( pluginContext[ SILENCE ] ){ updateSilence()}

        final  mavenVersion = mavenVersion()
        assert mavenVersion.startsWith( '3' ), "Only Maven 3 is supported, current Maven version is [$mavenVersion]"

        ThreadLocals.set( log, project, session )
        mopInit()

        if ( runIf( runIf )) { doExecute() }
    }


    private void updateSilence ()
    {
        tryIt { updateGCommons() }
        tryIt { updateAntBuilders() }
        tryIt { updateIvy() }
    }


    void updateGCommons ()
    {
        final context         = (( LoggerContext ) LoggerFactory.ILoggerFactory )
        final gcommonsLoggers = context.loggerList.findAll { it.name.startsWith( 'com.github.goldin.gcommons' ) }
        gcommonsLoggers.each { setFieldValue( it, Logger, 'effectiveLevelInt', Level.OFF_INT )}
    }


    @SuppressWarnings([ 'GroovyGetterCallCanBePropertyAccess' ])
    void updateAntBuilders ()
    {
        final updateLoggers = {
            AntBuilder antBuilder ->
            for ( logger in antBuilder.project.buildListeners.findAll{ it instanceof DefaultLogger })
            {
                setFieldValue( logger, DefaultLogger, 'out', nullPrintStream())
                setFieldValue( logger, DefaultLogger, 'err', nullPrintStream())
            }
            antBuilder
        }

        AntBuilder.metaClass.constructor       = { updateLoggers( AntBuilder.getConstructor().newInstance())}
        CustomAntBuilder.metaClass.constructor = { updateLoggers( CustomAntBuilder.getConstructor().newInstance())}
    }


    void updateIvy ()
    {
        Message.defaultLogger = new SilentLogger()
    }
}
