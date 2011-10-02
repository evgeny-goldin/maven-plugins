package com.goldin.plugins.duplicates

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.BaseGroovyMojo
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.apache.maven.artifact.Artifact
import org.apache.maven.plugin.MojoFailureException
import org.sonatype.aether.RepositorySystem
import org.sonatype.aether.RepositorySystemSession
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.resolution.ArtifactRequest
import org.sonatype.aether.util.artifact.DefaultArtifact
import org.jfrog.maven.annomojo.annotations.*


/**
 * A plugin that finds duplicate classes in the scope(s) specified
 */
@MojoGoal ( 'find-duplicates' )
@MojoPhase ( 'process-resources' )
@MojoRequiresDependencyResolution ( 'test' )
@SuppressWarnings( 'StatelessClass' )
class DuplicatesFinderMojo extends BaseGroovyMojo
{
    /**
     * Cache of Maven artifact to file on the disk
     */
    private static final Map<Artifact, File> FILES_CACHE = [:]

    /**
     * Cache of file on the disk to classes it contains
     */
    private static final Map<File, List<String>> CLASSES_CACHE = [:]


    @MojoParameter
    public String scopes = 'compile'

    @MojoParameter
    public boolean verbose = false

    @MojoParameter
    public boolean fail = true

    /**
     * Aether components:
     * http://aether.sonatype.org/using-aether-in-maven-plugins.html
     * https://docs.sonatype.org/display/AETHER/Home
     */

    @MojoComponent
    public RepositorySystem repoSystem

    @MojoParameter ( defaultValue = '${repositorySystemSession}', readonly = true )
    public RepositorySystemSession repoSession

    @MojoParameter ( defaultValue = '${project.remoteProjectRepositories}', readonly = true )
    public List<RemoteRepository> remoteRepos


    @Override
    void doExecute ()
    {
        assert mavenVersion().startsWith( '3' ), "This plugin only runs with Maven 3"

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
        Set<String>             scopes  = this.scopes.split( /,/ )*.trim() as Set
        Map<String, List<File>> classes =
            project.artifacts.findAll { Artifact a -> scopes.contains( a.scope ) && ( a.type != 'pom' ) }.
                              // Artifact => File
                              collect { Artifact a ->
                                        File f   = resolveArtifact( a )
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
        else              { log.info( "No duplicate libraries found" ) }
    }


    /**
     * Resolves Maven {@link Artifact} to local {@link File}.
     *
     * @param artifact Maven artifact to resolve
     * @return local file where it is downloaded and stored
     */
    private File resolveArtifact( Artifact artifact )
    {
        if ( FILES_CACHE.containsKey( artifact ))
        {
            return FILES_CACHE[ artifact ]
        }

        def request = new ArtifactRequest( new DefaultArtifact( artifact.groupId,
                                                                artifact.artifactId,
                                                                artifact.classifier,
                                                                artifact.type,
                                                                artifact.version ),
                                           remoteRepos, null )

        FILES_CACHE[ artifact ] = verifyBean().file( repoSystem.resolveArtifact( repoSession, request ).artifact.file )
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
