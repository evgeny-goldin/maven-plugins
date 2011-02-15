package com.goldin.plugins.common

import com.goldin.gcommons.GCommons
import com.goldin.gcommons.util.GroovyConfig
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.apache.maven.Maven
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.factory.ArtifactFactory
import org.apache.maven.artifact.metadata.ArtifactMetadataSource
import org.apache.maven.artifact.resolver.ArtifactResolutionResult
import org.apache.maven.artifact.resolver.ArtifactResolver
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter
import org.apache.maven.execution.MavenSession
import org.apache.maven.monitor.logging.DefaultLog
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.logging.console.ConsoleLogger
import org.xml.sax.ext.DefaultHandler2
import org.apache.commons.exec.*

class GMojoUtils
{
    private GMojoUtils ()
    {
    }


    /**
     * Retrieves plugin's {@link Log} instance
     * @return plugin's {@link Log} instance
     */
    static Log getLog () { ThreadLocals.get( Log.class ) }


    /**
     * Updates Groovy MOP with additional methods
     */
     static mopInit ()
     {
         GCommons.file() // Triggers GCommons MOP replacements

         /**
          * Trims multi-lines String: each line in the String specified is trim()-ed
          */
         String.metaClass.trimMultiline ={->
             delegate.splitWith( 'eachLine' )*.trim().join( GCommons.constants().CRLF )
         }


         /**
          * Deletes empty lines from the String
          */
         String.metaClass.deleteEmptyLines ={->
             delegate.splitWith( 'eachLine' ).findAll{ it.trim() }.join( GCommons.constants().CRLF )
         }


         /**
          * Replaces {..} expressions, not preceded by $, by ${..} to work around
          * "Disabling/escaping POM interpolation for specific variable" - http://goo.gl/NyEq
          *
          * We're putting {..} in POMs where we would like to have un-interpolated ${..}
          */
         String.metaClass.addDollar = {->
             // Adding a '$' before {..} where there was no '$' previously
             delegate.replaceAll( /(?<!\$)(?=\{.+?\})/, '\\$' )
         }
     }


   /**
    * {@link java.io.File#getCanonicalPath()} wrapper.
    *
    * @param f file to retrieve canonical path from
    * @return  file's canonical pathname
    */
   static String path( File f ) { GCommons.net().isNet( f.path ) ? f.path : f.canonicalPath }


    /**
     * Retrieves first not-<code>null</code> object.
     *
     * @param objects objects to check
     * @param <T> objects type
     * @return first not-<code>null</code> object (for chaining)
     *
     * @throws RuntimeException if all objects specified are <code>null</code>
     */
    static <T> T choose ( T ... objects )
    {
        for ( t in objects )
        {
            if ( t != null ) { return t }
        }

        throw new RuntimeException( 'All objects specified are *null*' )
    }


    /**
     * Retrieves an instance of {@code OutputStream} ignoring everything that is written to it.
     * @return instance of {@code OutputStream} ignoring everything that is written to it
     */
    static OutputStream devNullOutputStream ()
    {
        new OutputStream() { void write( int b ) {}}
    }


    /**
    * Retrieves {@link SimpleTemplateEngine} for the resource specified
    */
    static Template getTemplate ( String templatePath, ClassLoader loader = GMojoUtils.class.classLoader )
    {
        URL    templateURL = GMojoUtils.class.getResource( templatePath )
        assert templateURL, "[${ templatePath }] could not be loaded from the classpath"

        Template template = new SimpleTemplateEngine( loader ).createTemplate( templateURL )
        assert   template
        return   template
    }



   /**
    * {@code GMojoUtils.getTemplate().make().toString()} wrapper
    */
    static String makeTemplate( String  templatePath,
                                Map     binding,
                                String  endOfLine        = null,
                                boolean deleteEmptyLines = false )
    {
        def content = getTemplate( templatePath ).make( binding ).toString()

        if ( endOfLine        ) { content = content.replaceAll( /\r?\n/, (( 'windows' == endOfLine ) ? '\r\n' : '\n' )) }
        if ( deleteEmptyLines ) { content = content.deleteEmptyLines() }

        GCommons.verify().notNullOrEmpty( content )
    }


    /**
     * Reads lines from the file specified.
     *
     * @param file file to read
     * @return lines read from the file specified
     */
    static Collection<String> readLines( File file )
    {
        GCommons.verify().file( file ).readLines( "UTF-8" )
    }


    /**
     * Reads lines from the resource specified.
     *
     * @param file resource to read from the classpath
     * @return lines read from the resource specified
     */
    static Collection<String> readLines( String classPath )
    {
        InputStream is = GMojoUtils.class.getResourceAsStream( GCommons.verify().notNullOrEmpty( classPath ))
        assert      is, "Resource [$classPath] not found in a classpath"

        is.readLines( "UTF-8" )
    }


    /**
     * Retrieves Maven version as appears in "pom.properties" inside Maven jar.
     *
     * @return Maven version
     */
    static String mavenVersion()
    {
        InputStream is    = GCommons.verify().notNull( Maven.class.getResourceAsStream( '/META-INF/maven/org.apache.maven/maven-core/pom.properties' ))
        Properties  props = new Properties()
        props.load( is )
        is.close()
        GCommons.verify().notNullOrEmpty( props.getProperty( 'version', 'Unknown' ).trim())
    }


    /**
     * Determines if execution continues.
     *
     * @param s {@code <runIf>} string
     * @return true if 'runIf' is not defined or evaluates to true,
     *         false otherwise
     */
    static boolean runIf( String s )
    {
        boolean run = true

        if ( s )
        {
            run = Boolean.valueOf( groovy( s, String ))
            log.info( "<runIf>: [$s] evaluated to [$run] - ${ run ? 'continuing' : 'returning' }" )
        }

        run
    }


    /**
     * Evaluates Groovy expression provided and casts it to the class specified.
     *
     * @param expression   Groovy expression to evaluate, if null or empty - null is returned
     * @param resultType   result's type,
     *                     if <code>null</code> - no verification is made for result's type and <code>null</code>
     *                     value is allowed to be returned from eval()-ing the expression
     * @param groovyConfig {@link com.goldin.gcommons.util.GroovyConfig} object to use, allowed to be <code>null</code>
     * @param verbose      Whether Groovy evaluation should be verbose
     *
     * @param <T>        result's type
     * @return           expression evaluated and casted to the type specified
     *                   (after verifying compliance with {@link Class#isInstance(Object)}
     */
    public static <T> T groovy( String       expression,
                                Class<T>     resultType = null,
                                GroovyConfig config     = new GroovyConfig(),
                                Object ...   bindingObjects )
    {


        MavenProject project    = ThreadLocals.get( MavenProject.class )
        MavenSession session    = ThreadLocals.get( MavenSession.class )
        Map          bindingMap = [ project      : project,
                                    session      : session,
                                    mavenVersion : mavenVersion(),
                                    *:( project.properties + session.userProperties + session.executionProperties )]

        GCommons.groovy().eval( expression,
                                resultType,
                                GCommons.groovy().binding( bindingMap, bindingObjects ),
                                config )
    }


    /**
     * Converts an ['a', 'b', 'c'] collection to:
     *  * [a]
     *  * [b]
     *  * [c]
     *
     * @param c Collection to convert
     * @return String to use for log messages
     */
    static String stars ( Collection c ) { "* [${ c.join( "]${ GCommons.constants().CRLF }* [") }]" }

    /**
     * Strategy for executing the command, see {@link #execute}
     */
    enum EXEC_OPTION
    {
        /**
         * Apache Commons Exec {@link Executor} is used
         */
        CommonsExec,

        /**
         * {@link Runtime#getRuntime()} is used
         */
        Runtime,


        /**
         * {@link ProcessBuilder} is used
         */
        ProcessBuilder
    }


    /**
     * Executes the command specified.
     *
     * @param command    command to execute
     * @param timeoutMs  command's timeout in ms, 5 min by default
     * @param stdout     OutputStream to send command's stdout to, System.out by default
     * @param stderr     OutputStream to send command's stderr to, System.err by default
     * @param option     strategy for executing the command, EXEC_OPTION.CommonsExec by default
     *
     * @return           command's exit value
     */
    static int execute ( String       command,
                         EXEC_OPTION  option      = EXEC_OPTION.CommonsExec,
                         OutputStream stdout      = System.out,
                         OutputStream stderr      = System.err,
                         long         timeoutMs   = ( 5 * GCommons.constants().MILLIS_IN_MINUTE ) /* 5 min */,
                         File         directory   = new File( GCommons.constants().USER_DIR ),
                         Map          environment = new HashMap( System.getenv()))
    {
        GCommons.verify().notNullOrEmpty( command )

        switch ( option )
        {
            case EXEC_OPTION.CommonsExec:

                Executor                    executor = new DefaultExecutor()
                DefaultExecuteResultHandler handler  = new DefaultExecuteResultHandler()

                executor.setStreamHandler( new PumpStreamHandler( stdout, stderr ))
                executor.setWatchdog( new ExecuteWatchdog( timeoutMs ))
                executor.setWorkingDirectory( directory )

                executor.execute( CommandLine.parse( command ), environment, handler );
                handler.waitFor()

                if ( handler.exception )
                {
                    throw new RuntimeException( "Failed to invoke [$command]: ${ handler.exception }",
                                                handler.exception );
                }

                return handler.exitValue

            case EXEC_OPTION.Runtime:

                Process p = command.execute()

                p.consumeProcessOutputStream( stdout )
                p.consumeProcessErrorStream( stderr )
                p.waitForOrKill( timeoutMs )
                return p.exitValue()

            case EXEC_OPTION.ProcessBuilder:

                ProcessBuilder builder = new ProcessBuilder( command ).directory( directory )
                builder.environment() << environment

                Process p = builder.start();
                p.consumeProcessOutputStream( stdout )
                p.consumeProcessErrorStream( stderr )
                p.waitForOrKill( timeoutMs )
                return p.exitValue()

            default:
                assert false : "Unknown option [$option]. Known options are ${ EXEC_OPTION.values() }"
        }
    }


    /**
     * Retrieves all artifacts from the scopes specified.
     */
    static Set<Artifact> getArtifacts ( String ... scopes )
    {
        def result = new HashSet<Artifact>()

        for ( scope in scopes )
        {
            MavenProject project       = ThreadLocals.get( MavenProject.class )
            Artifact     buildArtifact = ThreadLocals.get( ArtifactFactory.class ).
                                         createBuildArtifact( project.getGroupId(),
                                                              project.getArtifactId(),
                                                              project.getVersion(),
                                                              project.getPackaging())
            ArtifactResolutionResult resolutionResult =
                ThreadLocals.get( ArtifactResolver.class ).
                resolveTransitively( project.getArtifacts(),
                                     buildArtifact,
                                     project.getManagedVersionMap(),
                                     ThreadLocals.get( MavenSession.class ).getLocalRepository(),
                                     project.getRemoteArtifactRepositories(),
                                     ThreadLocals.get( ArtifactMetadataSource.class ),
                                     new ScopeArtifactFilter( GCommons.verify().notNullOrEmpty( scope )))

            result.addAll( resolutionResult.getArtifacts())
        }

        result
    }


    /**
     * Validates content of the file specified to be XML-valid with {@link DefaultHandler2}.
     * @param configFile file to validate
     * @return same file object, for further chain calls
     * @throws RuntimeException if content validation fails
     */
    static File validate ( File configFile )
    {
        for ( parserClass in [ XmlParser.class, XmlSlurper.class ] )
        {
            def parser = parserClass.newInstance( true, true )
            parser.setErrorHandler( new DefaultHandler2())
            try
            {
                assert parser.parse( configFile )
            }
            catch ( Throwable t )
            {
                throw new RuntimeException( "Failed to validate [${ configFile.canonicalPath }]: $t", t )
            }
        }

        configFile
    }


    /**
     * Initializes {@link ThreadLocals} storage for testing environment
     */
    static void initTestThreadLocals()
    {
        ThreadLocals.set( new MavenProject(),
                          new MavenSession( null, null, null, null, null, null, null, new Properties(), new Properties(), new Date()),
                          new DefaultLog( new ConsoleLogger( Logger.LEVEL_DEBUG, "TestLog" )))
    }


    /**
     * Sets property specified to maven project and session provided.
     *
     * @param name    name of the property to set
     * @param value   value of the property to set
     * @param project Maven project
     * @param session Maven session
     */
    static void setProperty( String name, String value, MavenProject project, MavenSession session, String logMessage = '' )
    {
        GCommons.verify().notNullOrEmpty( name, value )
        GCommons.verify().notNull( project, session )

        [ project.properties, session.executionProperties, session.userProperties ]*.setProperty( name, value )
        log.info( logMessage ?: ">> Maven property \${$name} is set to \"$value\"" )
    }
}