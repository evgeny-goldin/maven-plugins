package com.github.goldin.plugins.common

import com.github.goldin.gcommons.GCommons
import com.github.goldin.gcommons.beans.*
import com.github.goldin.gcommons.util.GroovyConfig
import com.google.common.io.ByteStreams
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovyx.gpars.GParsPool
import org.apache.maven.Maven
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.artifact.versioning.VersionRange
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Dependency
import org.apache.maven.monitor.logging.DefaultLog
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.filtering.MavenFileFilter
import org.apache.maven.shared.filtering.MavenResourcesExecution
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.logging.console.ConsoleLogger
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires


/**
 * Various Mojo helper methods
 */
@SuppressWarnings([ 'MethodCount', 'AbcMetric' ])
class GMojoUtils
{
    /**
     * <groupId> prefix for Ivy <dependencies>.
     */
    static final String IVY_PREFIX = 'ivy.'


    private GMojoUtils ()
    {
    }


    /**
     * Retrieves plugin's {@link Log} instance
     * @return plugin's {@link Log} instance
     */
    static Log getLog () { ThreadLocals.get( Log ) }


    /**
     * Updates Groovy MOP with additional methods
     */
     static mopInit ()
     {
         fileBean() // Triggers GCommons MOP replacements

         /**
          * Trims multi-lines String: each line in the String specified is trim()-ed
          */
         String.metaClass.trimMultiline = { ->
             (( String ) delegate ).readLines()*.trim().join( constantsBean().CRLF )
         }


         /**
          * Deletes empty lines from the String
          */
         String.metaClass.deleteEmptyLines = { ->
             (( String ) delegate ).readLines().findAll{ it.trim() }.join( constantsBean().CRLF )
         }


         /**
          * Replaces {..} expressions, not preceded by $, by ${..} to work around
          * "Disabling/escaping POM interpolation for specific variable" - http://goo.gl/NyEq
          *
          * We're putting {..} in POMs where we would like to have un-interpolated ${..}
          */
         String.metaClass.addDollar = { ->
             // Adding a '$' before {..} where there was no '$' previously
             delegate.replaceAll( /(?<!\$)(?=\{.+?\})/, '\\$' )
         }
     }


    static OutputStream nullOutputStream () { ByteStreams.nullOutputStream() }
    static PrintStream  nullPrintStream  () { new PrintStream( nullOutputStream()) }


    /**
    * Retrieves {@link SimpleTemplateEngine} for the resource specified
    */
    static Template getTemplate ( String templatePath, ClassLoader loader = GMojoUtils.classLoader )
    {
        URL    templateURL = GMojoUtils.getResource( templatePath )
        assert templateURL, "[${ templatePath }] could not be loaded from the classpath"

        verifyBean().notNull( new SimpleTemplateEngine( loader ).createTemplate( templateURL ))
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

        verifyBean().notNullOrEmpty( content )
    }


    static Properties properties( String path, ClassLoader cl = GMojoUtils.classLoader )
    {
        assert path && cl

        InputStream is = cl.getResourceAsStream( path )
        assert      is, "Failed to load resource [$path] using ClassLoader [$cl]"

        Properties props = new Properties()
        props.load( is )
        is.close()

        props
    }


    /**
     * Retrieves Maven version as appears in "pom.properties" inside Maven jar.
     *
     * @return Maven version
     */
    static String mavenVersion()
    {
        verifyBean().notNullOrEmpty(
            properties( 'META-INF/maven/org.apache.maven/maven-core/pom.properties', Maven.classLoader ).
            getProperty( 'version', 'Unknown' ).trim())
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
            run = Boolean.valueOf(( String ) eval( s, String ))
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
     * @param groovyConfig {@link GroovyConfig} object to use, allowed to be <code>null</code>
     * @param verbose      Whether Groovy evaluation should be verbose
     *
     * @param <T>        result's type
     * @return           expression evaluated and casted to the type specified
     *                   (after verifying compliance with {@link Class#isInstance(Object)}
     */
    static <T> T eval ( String       expression,
                        Class<T>     resultType = null,
                        GroovyConfig config     = new GroovyConfig(),
                        Object ...   bindingObjects )
    {
        MavenProject project    = ThreadLocals.get( MavenProject )
        MavenSession session    = ThreadLocals.get( MavenSession )
        Map          bindingMap = [ project      : project,
                                    session      : session,
                                    mavenVersion : mavenVersion(),
                                    startTime    : session.startTime,
                                    ant          : new AntBuilder(),
                                    *:( project.properties + session.userProperties + session.systemProperties )]
        groovyBean().eval( expression,
                           resultType,
                           groovyBean().binding( bindingMap, bindingObjects ),
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
    static String stars ( Collection c ) { "* [${ c.join( "]${ constantsBean().CRLF }* [") }]" }


    /**
     * Initializes {@link ThreadLocals} storage for testing environment
     */
    static void initTestThreadLocals()
    {
        ThreadLocals.set( new MavenProject(),
                          new MavenSession( null, null, null, null, null, null, null, new Properties(), new Properties(), new Date()),
                          new DefaultLog( new ConsoleLogger( Logger.LEVEL_DEBUG, 'TestLog' )))
        mopInit()
    }


    /**
     * Retrieves maximal length of map's key.
     */
    static int maxKeyLength ( Map<?, ?> map ) { map.keySet().max{ Object o -> o.toString().size() }.toString().size() }


    /**
     * Sets property specified to maven project and session provided.
     *
     * @param name       name of the property to set
     * @param value      value of the property to set
     * @param logMessage log message to use when property is set, instead of the default one
     * @param verbose    whether property value set is logged or hidden
     * @param padName    number of characters to pad the property name
     */
    @Requires({ name && ( value != null ) && ( logMessage != null ) && ( padName >= 0 ) })
    static void setProperty( String name, String value, String logMessage = '', boolean verbose = true, int padName = 0 )
    {
        MavenProject project = ThreadLocals.get( MavenProject )
        MavenSession session = ThreadLocals.get( MavenSession )

        [ project.properties, session.systemProperties, session.userProperties ]*.setProperty( name, value )

        log.info( logMessage ?: '>> Maven property ' +
                                "\${$name}".padRight( padName + 3 ) +
                                ' is set' + ( verbose ? " to \"$value\"" : '' ))
    }


    /**
     *
     * Copies source file to destination applying replacements and filtering.
     *
     * @param sourceFile           source file to copy
     * @param destinationFile      destination file to copy the source to
     * @param skipIdentical        whether identical files should be skipped (not copied)
     * @param replaces             replacements to make
     * @param filtering            whether Maven
     *                             <a href="http://www.sonatype.com/books/maven-book/reference/resource-filtering-sect-description.html">filtering</a>
     *                             should be performed
     * @param encoding             Filtering/replacement encoding
     * @param fileFilter           {@link MavenFileFilter} instance, allowed to be <code>null</code> if <code>filter</code> is <code>false</code>
     * @param verbose              whether information is written to log with "INFO" level
     * @param move                 whether file should be moved and not copied
     * @param filterWithDollarOnly whether only ${ .. } expressions should be recognized as delimiters when files are filtered
     *
     * @return destinationFile if file was copied,
     *         null            if file was skipped (identical)
     */
    @SuppressWarnings([ 'MethodSize', 'AbcComplexity', 'CyclomaticComplexity' ])
    static File copyFile ( final File            sourceFile,
                           final File            destinationFile,
                           final boolean         skipIdentical,
                           final Replace[]       replaces,
                           final boolean         filtering,
                           final String          encoding,
                           final MavenFileFilter fileFilter,
                           final boolean         verbose,
                           final boolean         move,
                           final boolean         filterWithDollarOnly )
    {
        assert sourceFile.file && destinationFile && ( ! netBean().isNet( destinationFile.path ))
        assert ( replaces != null ) && encoding

        File             fromFile           = sourceFile
        boolean          operationPerformed = false
        boolean          operationSkipped   = false
        Closure<Boolean> samePath           = { fromFile.canonicalPath == destinationFile.canonicalPath }

        assert ! ( move && samePath()), \
               "It is not possible to <move> [$fromFile] into itself - <move> means source file is deleted"

        /**
         * Deleting destination file if possible
         */
        if ( ! ( skipIdentical || samePath())) { fileBean().mkdirs( fileBean().delete( destinationFile ).parentFile )}

        try
        {
            if ( filtering && (( ! filterWithDollarOnly ) || fromFile.getText( encoding ).contains( '${' )))
            {
                List<MavenFileFilter> wrappers =
                    fileFilter.getDefaultFilterWrappers( ThreadLocals.get( MavenProject ), null, false,
                                                         ThreadLocals.get( MavenSession ), new MavenResourcesExecution())
                if ( filterWithDollarOnly )
                {   // noinspection GroovyUnresolvedAccess
                    wrappers.each { it.delimiters = new LinkedHashSet<String>([ '${*}' ]) }
                }
                else if ( fileBean().extension( fromFile ).toLowerCase() == 'bat' )
                {
                    log.warn( "[$fromFile] - filtering *.bat files without <filterWithDollarOnly> may not work correctly due to '@' character, " +
                              'see http://evgeny-goldin.org/youtrack/issue/pl-233.' )
                }

                File tempFile = null
                if ( samePath())
                {
                    tempFile = fileBean().tempFile()
                    fileBean().copy( fromFile, tempFile.parentFile, tempFile.name )
                    if ( verbose ) { log.info( "[$fromFile] copied to [$tempFile] (to filter it into itself)" ) }

                    fromFile = tempFile
                }

                assert ! samePath()
                fileBean().mkdirs( destinationFile.parentFile )
                fileFilter.copyFile( fromFile, destinationFile, true, wrappers, encoding, true )
                assert destinationFile.setLastModified( System.currentTimeMillis())
                fileBean().delete(( tempFile ? [ tempFile ] : [] ) as File[] )

                if ( verbose ) { log.info( "[$fromFile] filtered to [$destinationFile]" ) }

                /**
                 * - Destination file created becomes input file for the following operations
                 * - If no replacements should be made - we're done
                 */
                fromFile           = destinationFile
                operationPerformed = ( replaces.size() < 1 )
            }

            if ( replaces )
            {
                destinationFile.write(( String ) replaces.inject( fromFile.getText( encoding )){ String s, Replace r -> r.replace( s, fromFile ) },
                                      encoding )
                if ( verbose ) { log.info( "[$fromFile] content written to [$destinationFile], " +
                                           "[${ replaces.size()}] replace${ generalBean().s( replaces.size()) } made" )}
                operationPerformed = true
            }

            final boolean identical = destinationFile.file && [ ( ! operationPerformed ), skipIdentical,
                                                                destinationFile.length()       == fromFile.length(),
                                                                destinationFile.lastModified() == fromFile.lastModified() ].every()
            if ( identical )
            {
                if ( verbose ) { log.info( "[$fromFile] skipped - content is identical to destination [$destinationFile]" ) }
                operationPerformed = true
                operationSkipped   = true
            }

            if ( ! operationPerformed )
            {
                if ( samePath())
                {
                    log.warn( "[$fromFile] skipped - path is identical to destination [$destinationFile]" )
                    operationPerformed = true
                    operationSkipped   = true
                }
                else if ( move )
                {
                    operationPerformed = fromFile.renameTo( destinationFile )
                    if ( verbose && operationPerformed ) { log.info( "[$fromFile] renamed to [$destinationFile]" )}
                }

                if ( ! operationPerformed )
                {
                    fileBean().copy( fromFile, destinationFile.parentFile, destinationFile.name )
                    if ( verbose ) { log.info( "[$fromFile] ${ move ? 'moved' : 'copied' } to [$destinationFile]" )}
                }
            }

            /**
             * If it's a "move" operation and file content was filtered/replaced or renameTo() call
             * doesn't succeed - source file is deleted
             */
            if ( move && sourceFile.file && ( sourceFile.canonicalPath != destinationFile.canonicalPath )) { fileBean().delete( sourceFile ) }
            ( operationSkipped ? null : verifyBean().file( destinationFile ))
        }
        catch ( e )
        {
            throw new MojoExecutionException( "Failed to copy [$sourceFile] to [$destinationFile]", e )
        }
    }


    /**
     * Splits a delimiter-separated String.
     *
     * @param s         String to split
     * @param delimiter delimiter (not regex!) to split the String with
     * @return result of {@code s.split( delim )*.trim().grep()}
     */
    @Requires({ delimiter })
    @Ensures ({ result != null })
    static List<String> split( String s, String delimiter = ',' ) { ( s ?: '' ).tokenize( delimiter )*.trim().grep() }


    /**
     * Add a '$' character to {..} expressions.
     *
     * @param value value containing {..} expressions.
     * @param addDollar if "false" or Groovy Truth false - no changes are made to the value,
     *                  if "true" - all {..} expressions are converted to ${..}
     *                  if list of comma-separated tokens - only {token} expressions are updated
     * @return value modified according to 'addDollar'
     */
    static String addDollar( String value, String addDollar )
    {
        String result = value

        if ( value && addDollar && ( 'false' != addDollar ))
        {
            String pattern = ( 'true' == addDollar ) ? '.+?' : split( addDollar ).collect{ String token -> "\\Q$token\\E" }.join( '|' )
            result         = value.replaceAll( ~/(?<!\$)(?=\{($pattern)\})/, '\\$' )
        }

        result
    }


    /**
     * Converts artifact coordinates to Maven {@link Artifact}.
     *
     * @param groupId    artifact {@code <groupId>}
     * @param artifactId artifact {@code <artifactId>}
     * @param version    artifact {@code <version>}
     * @param scope      artifact {@code <scope>}
     * @param type       artifact {@code <type>}
     * @param classifier artifact {@code <classifier>}
     * @param optional   whether artifact is optional
     * @param file       artifact local file, may be {@code null}
     *
     * @return new Maven {@link Artifact}
     */
    @SuppressWarnings([ 'GroovyMethodParameterCount' ])
    @Requires({ groupId && artifactId && version })
    @Ensures ({ result })
    static Artifact toMavenArtifact ( String groupId, String artifactId, String version, String scope, String type, String classifier,
                                      boolean optional, File file = null )
    {
        final a = new DefaultArtifact( groupId, artifactId, VersionRange.createFromVersion( version ),
                                       scope ?: 'compile', type, classifier, new DefaultArtifactHandler(), optional )
        if ( file ) { a.file = verifyBean().file( file ) }
        a
    }


    /**
     * Converts Aether {@link org.sonatype.aether.graph.Dependency} to Maven {@link Artifact}.
     * @param aetherDependency Aether dependency
     * @return new Maven {@link Artifact}
     */
    @Requires({ aetherDependency })
    @Ensures ({ result })
    static Artifact toMavenArtifact ( org.sonatype.aether.graph.Dependency aetherDependency )
    {
        aetherDependency.artifact.with {
            toMavenArtifact( groupId, artifactId, version, aetherDependency.scope, extension, classifier, false, file )
        }
    }


    /**
     * Converts Maven {@link org.apache.maven.model.Dependency} to {@link Artifact}.
     * @param mavenDependency Maven dependency
     * @return new Maven {@link Artifact}
     */
    @Requires({ mavenDependency })
    @Ensures ({ result })
    static Artifact toMavenArtifact( org.apache.maven.model.Dependency mavenDependency )
    {
        mavenDependency.with { toMavenArtifact( groupId, artifactId, version, scope, type, classifier, optional ) }
    }


    /**
     * Converts Maven {@link Artifact} to Aether artifact.
     *
     * @param artifact Maven artifact
     * @return new Aether {@link org.sonatype.aether.artifact.Artifact}
     */
    @Requires({ artifact })
    @Ensures ({ result })
    static org.sonatype.aether.artifact.Artifact toAetherArtifact ( Artifact artifact )
    {
        artifact.with { new org.sonatype.aether.util.artifact.DefaultArtifact( groupId, artifactId, classifier, type, version, null, file )}
    }


    /**
     * Converts Maven {@link Dependency} to Aether artifact.
     *
     * @param dependency Maven dependency
     * @return new Aether {@link org.sonatype.aether.artifact.Artifact}
     */
    @Requires({ dependency })
    @Ensures ({ result })
    static org.sonatype.aether.artifact.Artifact toAetherArtifact ( Dependency dependency )
    {
        dependency.with { new org.sonatype.aether.util.artifact.DefaultArtifact( groupId, artifactId, classifier, type, version )}
    }


    /**
     * Converts path specified to URL.
     *
     * @param s path of disk file or jar-located resource.
     * @return path's URL
     */
    @Requires({ s })
    @Ensures ({ result })
    static URL url( String s )
    {
        s.trim().with { ( startsWith( 'jar:' ) || startsWith( 'file:' )) ? new URL( s ) : new File( s ).toURL() }
    }


    /**
     * Convert path to its canonical form.
     *
     * @param s path to convert
     * @return path in canonical form
     */
    static String canonicalPath ( String s )
    {
        ( s && ( ! netBean().isNet( s ))) ? new File( s ).canonicalPath.replace( '\\', '/' ) : s
    }


    /**
     * Throws a {@link MojoExecutionException} or logs a warning message according to {@code fail}.
     *
     * @param fail     whether execution should throw an exception
     * @param message  error message to throw or log
     * @param error    execution error, optional
     */
    @Requires({ message })
    static void failOrWarn( boolean fail, String message, Throwable error = null )
    {
        if ( fail )
        {
            if ( error ) { throw new MojoExecutionException( message, error )}
            else         { throw new MojoExecutionException( message )}
        }
        else
        {
            if ( error ) { log.warn( message, error )}
            else         { log.warn( message )}
        }
    }


    @Requires({ ( c != null ) && action })
    static void each ( boolean parallel, Collection<?> c, Closure action )
    {
        if ( parallel ) { GParsPool.withPool { c.eachParallel( action ) }}
        else            { c.each( action )}
    }


    @SuppressWarnings( 'UnnecessaryObjectReferences' )
    static ConstantsBean constantsBean (){ GCommons.constants ()}
    @SuppressWarnings( 'UnnecessaryObjectReferences' )
    static GeneralBean  generalBean (){ GCommons.general ()}
    @SuppressWarnings( 'UnnecessaryObjectReferences' )
    static FileBean    fileBean (){ GCommons.file ()}
    @SuppressWarnings( 'UnnecessaryObjectReferences' )
    static NetBean     netBean (){ GCommons.net ()}
    @SuppressWarnings( 'UnnecessaryObjectReferences' )
    static VerifyBean  verifyBean (){ GCommons.verify ()}
    @SuppressWarnings( 'UnnecessaryObjectReferences' )
    static GroovyBean  groovyBean (){ GCommons.groovy ()}
}