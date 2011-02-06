package com.goldin.plugins.common;

import com.goldin.gcommons.GCommons;
import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenResourcesExecution;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.goldin.plugins.common.GMojoUtils.getLog;
import static com.goldin.plugins.common.GVerifier.isFalse;
import static com.goldin.plugins.common.GVerifier.isTrue;
import static org.twdata.maven.mojoexecutor.MojoExecutor.*;


/**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Mojo utilities
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */
public final class MojoUtils
{

    static { GMojoUtils.init(); }


    private MojoUtils ()
    {
    }


    /**
     * Pattern to {@code <include>} or {@code <exclude>} all files in directory specified
     */
    public static final List<String> INCLUDE_ALL = Collections.unmodifiableList( Arrays.asList( "**" ));


    /**
     * Temporal files that are cleaned up by shutdown hook (below)
     */
    private static final List<File> TEMP_FILES = Collections.synchronizedList( new ArrayList<File>( 8 ));

    static
    {
        Runtime.getRuntime().addShutdownHook( new Thread( new Runnable()
        {
            @Override
            public void run ()
            {
                for ( File tempFile : TEMP_FILES )
                {
                    if ( ! delete( tempFile, false, false ))
                    {
                        getLog().warn( String.format( "Unable to delete temporal file [%s]", path( tempFile )));
                    }
                }
            }
        }));
    }


    /**
     * {@link java.io.File#getCanonicalPath()} wrapper.
     *
     * @param f file to retrieve canonical path from
     * @return  file's canonical pathname
     *
     * @throws RuntimeException if fails to call {@link java.io.File#getCanonicalPath()}
     */
    public static String path( File f )
    {
        return GMojoUtils.path( f );
    }


    /**
     * Creates a {@link java.io.File} using a <code>path</code> specified, passes it to {@link #path(java.io.File)}
     * (that calls {@link java.io.File#getCanonicalPath()}) and verifies directory existence.
     *
     * @param path path to use for {@link java.io.File} creation,
     *
     * @return {@link java.io.File} using a <code>path</code> specified
     */
    public static File directory ( String path )
    {
        return GCommons.verify().directory( file( null, path ));
    }


    /**
     * Creates a {@link java.io.File} using a <code>path</code> specified, passes it to {@link #path(java.io.File)}
     * (that calls {@link java.io.File#getCanonicalPath()}) and verifies it's existence.
     *
     * @param path path to use for {@link java.io.File} creation,
     *
     * @return {@link java.io.File} using a <code>path</code> specified
     */
    public static File file ( String path )
    {
        return file( null, path );
    }


    /**
     * Creates a {@link java.io.File} using a <code>path</code> specified, passing it to {@link #path(java.io.File)}
     * (that calls {@link java.io.File#getCanonicalPath()}) and verifies it's existence.
     *
     * @param parentDir parent directory, allowed to be <code>null</code>
     * @param path      path to use for {@link java.io.File} creation
     *
     * @return {@link java.io.File} using a <code>path</code> specified
     */
    public static File file ( File parentDir, String path )
    {
        GCommons.verify().notNullOrEmpty( path );

        return GCommons.verify().exists( new File( path( new File( parentDir, path.trim()))));
    }


    /**
     * Retrieves relative path of file inside directory specified.
     * For example: for directory <code>"C:\some"</code> and child file <code>"C:\some\folder\opa\1.txt"</code>
     * this function returns <code>"\folder\opa\1.txt"</code>.
     *
     * @param directory file's parent directory
     * @param file      directory's child file
     * @return          relative path of file inside directory specified, starts with "\" or "/"
     */
    public static String relativePath( File directory, File file )
    {
        GCommons.verify().notNull( directory, file );

        String directoryPath = path( directory );
        String filePath      = path( file );

        if ( ! filePath.startsWith( directoryPath ))
        {
            throw new RuntimeException( String.format( "File [%s] is not a child of [%s]", filePath, directoryPath ));
        }

        String relativePath = GCommons.verify().notNullOrEmpty( filePath.substring( directoryPath.length()));
        isTrue( relativePath.startsWith( "/" ) || relativePath.startsWith( "\\" ));

        return relativePath;
    }


    /**
     * Copies file to the destination file specified.
     *
     * @param sourceFile      source file to copy
     * @param destinationFile destination file to copy the source to,
     *                        <code><b>scp://user:password@host:location</b></code> URLs are supported
     * @param verbose         whether information is written to log with "INFO" level
     *
     * @throws RuntimeException if copying fails
     */
    public static void copy ( File sourceFile, File destinationFile, boolean verbose )
    {
        GCommons.verify().file( sourceFile );
        GCommons.verify().notNull( destinationFile );

        String sourceFilePath      = path( sourceFile      );
        String destinationFilePath = path( destinationFile );

        if ( sourceFilePath.equals( destinationFilePath ))
        {
            throw new RuntimeException(
                String.format( "Source [%s] and destination [%s] are the same", sourceFilePath, destinationFilePath ));
        }


        if ( GCommons.net().isNet( destinationFilePath ))
        {
            NetworkUtils.upload( sourceFile, destinationFilePath, verbose );
        }
        else
        {
            delete( destinationFile, false, true );

            try
            {
                FileUtils.copyFile ( sourceFile, destinationFile );
                GCommons.verify().file( destinationFile );

                if ( verbose )
                {
                    getLog().info( String.format( "[%s] copied to [%s]", sourceFilePath, destinationFilePath ));
                }
            }
            catch ( Exception e )
            {
                throw new RuntimeException( String.format( "Failed to copy [%s] to [%s]: %s",
                                                           sourceFilePath, destinationFilePath, e ),
                                            e );
            }
        }
    }


    /**
     *
     * Copies source file to destination applying replacements and filtering.
     *
     * @param sourceFile      source file to copy
     * @param destinationFile destination file to copy the source to,
     *                        <code><b>scp://user:password@host:location</b></code> URLs are supported
     * @param skipIdentical   whether identical files should be skipped (not copied)
     * @param replaces        replacements to make
     * @param filtering       whether Maven
     *                        <a href="http://www.sonatype.com/books/maven-book/reference/resource-filtering-sect-description.html">filtering</a>
     *                        should be performed
     * @param encoding        Filtering/replacement encoding
     * @param fileFilter      {@link org.apache.maven.shared.filtering.MavenFileFilter} instance,
     *                        allowed to be <code>null</code> if <code>filter</code> is <code>false</code>
     * @param mavenProject    {@link org.apache.maven.project.MavenProject} instance,
     *                        allowed to be <code>null</code> if <code>filter</code> is <code>false</code>
     * @param mavenSession    {@link org.apache.maven.execution.MavenSession} instance,
     *                        allowed to be <code>null</code> if <code>filter</code> is <code>false</code>
     * @param verbose         whether information is written to log with "INFO" level
     *
     * @return <code>true</code>  if file was copied,
     *         <code>false</code> if file was skipped (identical)
     * @throws RuntimeException if fails to make replacements or filtering while copying the file
     */
    public static boolean copy ( File            sourceFile,
                                 File            destinationFile,
                                 boolean         skipIdentical,
                                 Replace[]       replaces,
                                 boolean         filtering,
                                 String          encoding,
                                 MavenFileFilter fileFilter,
                                 MavenProject    mavenProject,
                                 MavenSession    mavenSession,
                                 boolean         verbose )
    {
        GCommons.verify().file( sourceFile );
        GCommons.verify().notNull( destinationFile, replaces );
        GCommons.verify().notNullOrEmpty( encoding );

        String sourceFilePath      = path( sourceFile );
        String destinationFilePath = path( destinationFile );

        try
        {
            File fromFile = sourceFile;

            if ( filtering )
            {
                GCommons.verify().notNull( fileFilter, mavenProject, mavenSession );

                /**
                 * http://maven.apache.org/shared/maven-filtering/apidocs/index.html
                 */

                File                  tempFile = tempFile();
                List<MavenFileFilter> wrappers = fileFilter.getDefaultFilterWrappers( mavenProject,
                                                                                      null,
                                                                                      false,
                                                                                      mavenSession,
                                                                                      new MavenResourcesExecution());

                fileFilter.copyFile( fromFile, tempFile, true, wrappers, encoding, true );

                if ( verbose )
                {
                    getLog().info( String.format( "[%s] copied to [%s] (with <filtering>)", path( fromFile ), path( tempFile )));
                }

                fromFile = tempFile;
            }

            if ( replaces.length > 0 )
            {
                String data = FileUtils.readFileToString( fromFile, encoding );

                for ( Replace replace : replaces )
                {
                    data = replace.replace( data, fromFile.getAbsolutePath());
                }

                File tempFile = tempFile();
                FileUtils.writeStringToFile( tempFile, data, encoding );

                if ( verbose )
                {
                    getLog().info( String.format( "[%s] copied to [%s] (with <replaces>)", sourceFilePath, path( tempFile )));
                }

                fromFile = tempFile;
            }

            if ( skipIdentical )
            {
                boolean identicalFiles = (( destinationFile.isFile())                            &&
                                          ( destinationFile.length()       == fromFile.length()) &&
                                          ( destinationFile.lastModified() == fromFile.lastModified()));
                if ( identicalFiles )
                {
                    getLog().info( String.format( "[%s] skipped - identical to [%s]",
                                                  path( fromFile ), path( destinationFile )));
                    return false;
                }
            }

            copy( fromFile, destinationFile, verbose );
            return true;
        }
        catch ( Exception e )
        {
            throw new RuntimeException( String.format( "Failed to copy [%s] to [%s]: %s",
                                                       sourceFilePath, destinationFilePath, e ),
                                        e );
        }
    }


    /**
     * Adds a temporal file to the list of files to be deleted by shutdown hook.
     *
     * @param file file to delete
     * @return file specified (for chaining)
     */
    private static File addTempFile ( File file )
    {
        file.deleteOnExit();
        TEMP_FILES.add( file );

        return file;
    }


    /**
     * Deletes all temporal files aggregated till this moment
     */
    public static void deleteTempFiles()
    {
        synchronized ( TEMP_FILES )
        {
            for ( Iterator<File> iter = TEMP_FILES.iterator(); iter.hasNext(); )
            {
                delete( iter.next(), false, true );
                iter.remove();
            }
        }
    }


    /**
     * {@link File#createTempFile(String, String)} wrapper.
     *
     * @return result of invoking {@link File#createTempFile(String, String)}
     */
    public static File tempFile ()
    {
        try
        {
            File tempFile = addTempFile( File.createTempFile( MojoUtils.class.getName(),
                                                              String.valueOf( System.currentTimeMillis())));

            return GCommons.verify().file( tempFile );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( String.format( "Failed to create a temp file: %s", e ),
                                        e );
        }
    }


    /**
     * Deletes file or directory specified
     *
     * @param file file or directory to delete.
     *        If directory - deletes all it's content recursively
     * @param addShutdownHookIfFailed whether a shutdown hook should be added if deleting file fails
     * @param failIfFailed            whether an exception should be thrown if operation fails to delete a file
     *
     * @return <code>true</code> if file was deleted successfully,
     *         <code>false</code> otherwise
     */
    public static boolean delete ( final File file, boolean addShutdownHookIfFailed, boolean failIfFailed )
    {
        GCommons.verify().notNull( file );

        boolean result = true;

        if ( file.isDirectory())
        {
            for ( File f : file.listFiles())
            {
                result &= delete( f, addShutdownHookIfFailed, failIfFailed );
            }
        }

        if (( file.exists()) && ( ! file.delete()))
        {
            if ( addShutdownHookIfFailed )
            {
                addTempFile( file );
                System.err.println( String.format( "Failed to delete [%s] - JVM shutdown hook is added", path( file )));
            }

            if ( failIfFailed )
            {
                throw new RuntimeException( String.format( "Failed to delete [%s]", path( file )));
            }

            result = false;
        }

        if ( result )
        {
            isFalse( file.exists());
        }

        return result;
    }


    /**
     * Determines if value specified is set.
     *
     * @param s value to test
     * @return true if value is different from either <code>null</code> or <code>"none"</code> value,
     *         false otherwise
     */
    public static boolean isSet ( String s ) { return ( s != null ) && ( ! s.equalsIgnoreCase( "none" )); }


    /**
     * Invokes "maven-deploy-plugin" to deploy the file specified.
     *
     * @param file       file to deploy
     * @param url        Maven repository URL
     * @param groupId    groupId
     * @param artifactId artifactId
     * @param version    version
     * @param classifier classifier, can be <code>null</code>
     * @param project    Maven project
     * @param session    Maven session
     * @param manager    Maven plugin manager
     */
    public static void deploy ( File file, String url, String groupId, String artifactId, String version, String classifier,
                                MavenProject project, MavenSession session, PluginManager manager )
    {
        GCommons.verify().file( file );
        GCommons.verify().notNullOrEmpty( url, groupId, artifactId, version );
        assert GMojoUtils.mavenVersion().startsWith( "2" ):
               "<deploy> is only supported by Maven 2 for now, see http://evgeny-goldin.org/youtrack/issue/pl-258";

        List<Element> configuration = Arrays.asList( element( "file",       path( file )),
                                                     element( "url",        url         ),
                                                     element( "groupId",    groupId     ),
                                                     element( "artifactId", artifactId  ),
                                                     element( "version",    version     ),
                                                     element( "packaging",  GCommons.file().extension( file )));
        if ( classifier != null )
        {
            configuration.add( element( "classifier", classifier ));
        }

        String description = String.format( "[%s] to [%s] as [<%s>:<%s>:<%s>%s]",
                                            path( file ), url,
                                            groupId, artifactId, version,
                                            (( classifier != null ) ? ( ":<" + classifier + ">" ) : "" ));
        try
        {
            executeMojo( plugin( "org.apache.maven.plugins",
                                 "maven-deploy-plugin",
                                 "2.5" ),
                         goal( "deploy-file" ),
                         configuration( configuration.toArray( new Element[ configuration.size() ] )),
                         executionEnvironment( project, session, manager ));

            getLog().info( String.format( "Deployed %s", description ));
        }
        catch ( Exception e )
        {
            throw new RuntimeException( String.format( "Failed to deploy %s: %s", description, e ),
                                        e );
        }
    }
}
