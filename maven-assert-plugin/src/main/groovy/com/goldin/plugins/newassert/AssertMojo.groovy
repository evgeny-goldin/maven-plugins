package com.goldin.plugins.newassert

import com.goldin.plugins.common.BaseGroovyMojo
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import static com.goldin.plugins.common.GMojoUtils.*


/**
 * Asserts invoker
 */
@MojoGoal  ( "assert" )
@MojoPhase ( "install" )
class AssertMojo extends BaseGroovyMojo
{
    AssertMojo ()
    {
    }


   /**
    * Multi-line parameter.
    * Each line - name of the Maven property to verify for existence.
    * Verifies each property is defined and logs its value.
    */
    @MojoParameter
    public String assertProperties


    /**
     * Multi-line parameter.
     * Each line - file pattern to match: "path/*.something"
     * Verifies each pattern represents existing files.
     */
    @MojoParameter
    public String assertFiles

    @MojoParameter
    public String shouldFailAssertFiles


   /**
    * Multi-line parameter.
    * Each line - two entries, ":"-separated "entry1:entry1"
    * Verifies entries are "equal", comparing files with files, directories with directories
    */
    @MojoParameter
    public String assertEqual

    @MojoParameter
    public String shouldFailAssertEqual


   /**
    * Multi-line parameter.
    * Each line - two entries, ":"-separated "entry1:entry1"
    * Verifies entries are "equal", comparing files with files, directories with directories (no checksum check)
    */
    @MojoParameter
    public String assertEqualNoChecksum

    @MojoParameter
    public String shouldFailAssertEqualNoChecksum


   /**
    * Multi-line parameter.
    * Each line - Groovy expression verified to evaluate to true.
    */
    @MojoParameter
    public String assertGroovy


   /**
    * Value to "normalize" CrLf when comparing files
    */
    @MojoParameter
    public String endOfLine



    void doExecute ()
    {
        verifyProperties ( assertProperties )
        verifyFiles      ( assertFiles )
        verifyFilesFails ( shouldFailAssertFiles )
        verifyEqual      ( assertEqual,                     true  )
        verifyEqualFails ( shouldFailAssertEqual,           true  )
        verifyEqual      ( assertEqualNoChecksum,           false )
        verifyEqualFails ( shouldFailAssertEqualNoChecksum, false )
        verifyGroovy     ( assertGroovy )
    }


    String shouldFailAssert ( Closure c ) { new GroovyTestCase().shouldFail( AssertionError, c ) }


    /**
    * Invokes a closure specified with each non-empty and trim()-ed line of data passed
    *
    * @param data data to split to lines
    * @param callback closure to invoke on each line
    */
    private verifyEachLine( String data, Closure callback )
    {
        if ( data )
        {
            verifyBean().notNullOrEmpty( data ).splitWith( 'eachLine', String )*.trim().findAll{ it }.each { callback( it ) }
        }
    }


    private verifyProperties ( String properties )
    {
        verifyEachLine( properties, {

            String propertyName ->

            Map    superMap      = mavenProject.properties + mavenSession.userProperties + mavenSession.executionProperties
            Object propertyValue = superMap[ propertyName ]

            assert ( propertyValue != null ), 'Property ${' + propertyName + '} is undefined!'

            log.info( '${' + propertyName + '} = [' + propertyValue + ']' )
        })
    }


    private verifyFiles( String files )
    {
        verifyEachLine( files, {

            String line ->

            if ( line.contains( '*' ))
            {
                int j = line.lastIndexOf( '/' )
                assert ( j > -1 ),                      "File pattern [$line] contains no slashes?"
                assert ( line.indexOf( '*' )     > j ), "File pattern [$line] contains '*' before last slash?"
                assert ( line.lastIndexOf( '*' ) > j ), "File pattern [$line] doesn't contain '*' after last slash?"

                File       basedir        = new File( line.substring( 0, j ))
                String     includePattern = line.substring( j + 1 )
                Collection c              = fileBean().files( basedir, [ includePattern ], [], true, false, false )
                assert     c, "File pattern [$line] - no files matched"

                log.info( "File pattern [$line] - [${ c.size()}] file${ generalBean().s( c.size()) } matched" )
            }
            else
            {
                File   file = new File( line )
                String path = file.canonicalPath
                assert file.exists(), "[$path] is missing"

                log.info ( "${file.isFile() ? 'File' : 'Directory' } [$path] exists${ file.isFile() ?  ', [' + file.length() + '] bytes' : '' }" )
            }
        })
    }


    private verifyFilesFails ( String files )
    {
        verifyEachLine( files, { String line -> shouldFailAssert { verifyFiles( line ) }} )
    }


    private verifyEqual( String lines, boolean verifyChecksum )
    {
        verifyEachLine( lines, {

            String line ->

            def ( String file1Path, String file2Path, String pattern ) = [ *line.split( /\s*\|\s*/ ), null ]

            assert ( file1Path && file2Path ), \
                   "Each non-empty <assertEqual> line should be composed of two file entries witn an optional pattern, " +
                   "separated with a '|': \"path1 | path2 ( | pattern)\""

            def file1 = new File( file1Path )
            def file2 = new File( file2Path )

            /**
             * If pattern is applied - verifying all *matching* files from the first dir identical to their corresponding files in the second dir
             * If pattern is not applied - verifying two directories are absolutely identical
             */
            log.info( pattern ?
                "Verifying [${ file1.canonicalPath }]/[$pattern] file(s) have corresponding and identical file(s) in [${ file2.canonicalPath }]" :
                "Verifying [${ file1.canonicalPath }] is identical to [${ file2.canonicalPath }]" )

            int filesChecked = verifyBean().equal( file1, file2, verifyChecksum, pattern, endOfLine )

            log.info( "[${ file1.canonicalPath }] is identical to [${ file2.canonicalPath }] " +
                           "($filesChecked file${ ( filesChecked == 1 ) ? '' : 's' } checked)" )
        })
    }


    private verifyEqualFails ( String lines, boolean verifyChecksum )
    {
        verifyEachLine( lines, { String line -> shouldFailAssert { verifyEqual( line, verifyChecksum ) }} )
    }


    private verifyGroovy( String groovy )
    {
        verifyEachLine( groovy, { String line -> eval( "assert $line" ) })
    }
}
