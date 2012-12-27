package com.github.goldin.plugins.duplicates

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


/**
 * A plugin that finds duplicate classes in the scope(s) specified
 */
@Mojo( name = 'find-duplicates', defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
@SuppressWarnings([ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ])
class DuplicatesFinderMojo extends BaseGroovyMojo
{
    /**
     * Cache of file on the disk to classes it contains
     */
    private static final Map<File, List<String>> CLASSES_CACHE = [:]


    @Parameter
    private String scopes = 'compile'

    @Parameter
    private boolean verbose = false

    @Parameter
    private boolean fail = true


    @Override
    void doExecute ()
    {
        /**
         * Mapping of File to Maven Artifact
         */
        Map<File, Artifact> f2A = [:]
        def time                = System.currentTimeMillis()

       /**
        * Makes sure map specified has a new list entry and adds a new value to it
        */
        def updateMap = { Map m, Object key, Object value ->
                          m[ key ] = m[ key ]?: []
                          m[ key ] << value }

        /**
         * Mapping of class names to files they were found it
         */
        Set<String>             scopes  = split( this.scopes ) as Set
        Map<String, List<File>> classes =
            project.artifacts.findAll { Artifact a -> scopes.contains( a.scope ) && ( a.type != 'pom' ) }.
                              // Artifact => File
                              collect { Artifact a ->
                                        File f   = resolveArtifact( a, false, true ).file
                                        f2A[ f ] = a
                                        if ( verbose ) { log.info( "Checking [$a]" ) }
                                        f }.
                              // Files => Mapping of class names to files
                              inject( [:] ){ Map map, File f ->
                                             classNames( f ).each{ String className -> updateMap( map, className, f ) }
                                             map }

        /**
         * Mapping of violating artifacts (Stringified list) to duplicate class names
         */
        Map<String, List<String>> violations =
            classes.findAll{ String className, List<File> files -> files.size() > 1 }.
                    // Class names with more then 1 files they were found in => Mapping of violating artifacts
                    inject( [:] ){ Map map, Map.Entry<String, List<File>> entry ->
                                   String className          = entry.key
                                   String violatingArtifacts = entry.value.collect{ File f -> f2A[ f ] }.toString()
                                   updateMap( map, violatingArtifacts, className )
                                   map }

        log.info( "[${ f2A.size() }] artifact${ generalBean().s( f2A.size())} analyzed in [${ System.currentTimeMillis() - time }] ms" )
        if ( violations ) { reportViolations( violations ) }
        else              { log.info( 'No duplicate libraries found' ) }
    }


    /**
     * Reads Zip archive and returns a list of class names stored in it.
     *
     * @param file Zip archive to read
     * @return list of class names stored in it
     */
    private List<String> classNames ( File file )
    {
        if ( CLASSES_CACHE.containsKey( file ))
        {
            return CLASSES_CACHE[ file ]
        }

        ZipFile zip = new ZipFile( file )

        try
        {
            CLASSES_CACHE[ file ] = verifyBean().notNull( zip.entries().findAll{ ZipEntry entry -> entry.name.endsWith( '.class' ) }.
                                                                    collect{ ZipEntry entry -> entry.name.replace( '/', '.' ).
                                                                                               replaceAll( /\.class$/, '' ) } )
        }
        finally { zip.close() }
    }


    /**
     * Reports violations found by throwing an exception or logging an error message.
     *
     * @param violations violations found
     */
    @SuppressWarnings( 'UseCollectMany' )
    private void reportViolations( Map<String, List<String>> violations )
    {
        def message =
            '\nDuplicates found in:\n' +
            violations.collect{ String artifacts, List<String> classes ->
                                [ "-=-= $artifacts =-=-" ] + ( verbose ? classes.sort().collect { " --- [$it]" } : [] ) }.
                       flatten().
                       grep().
                       join( constantsBean().CRLF )

        if ( fail ) { throw new MojoFailureException( message )}
        else        { log.error( message )}
    }
}
