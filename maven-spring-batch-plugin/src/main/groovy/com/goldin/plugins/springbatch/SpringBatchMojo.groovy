package com.goldin.plugins.springbatch

import static com.goldin.plugins.common.GMojoUtils.*
import org.apache.maven.plugin.MojoFailureException
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import org.springframework.core.io.ClassPathResource
import com.goldin.plugins.common.*


/**
 * Spring Batch invoker
 */
@MojoGoal  ( 'run' )
@MojoPhase ( 'install' )
@SuppressWarnings( 'StatelessClass' )
class SpringBatchMojo extends BaseGroovyMojo
{
    SpringBatchMojo ()
    {
    }


   /**
    * Comma-separated Spring context files
    */
    @MojoParameter ( required = true )
    public String configLocations
    public String configLocations(){ verify().notNullOrEmpty( this.configLocations ) }


    /**
     * SpringBatch job name
     */
    @MojoParameter ( required = true )
    public String jobId
    public String jobId(){ verify().notNullOrEmpty( this.jobId )}


    /**
     * SpringBatch additional command line parameters:
     * http://static.springsource.org/spring-batch/apidocs/org/springframework/batch/core/launch/support/CommandLineJobRunner.html
     *
     * @see org.springframework.batch.core.launch.support.CommandLineJobRunner#main(String[])
     */
    @MojoParameter
    public String params
    public String params(){ this.params }

    /**
     * SpringBatch additional command line options (arguments starting with '-'):
     * http://static.springsource.org/spring-batch/apidocs/org/springframework/batch/core/launch/support/CommandLineJobRunner.html
     *
     * @see org.springframework.batch.core.launch.support.CommandLineJobRunner#main(String[])
     */
    @MojoParameter
    public String opts
    public String opts(){ this.opts }


    /**
     * Optional key = value multiline properties:
     *
     * <properties>
     *     od.input.dir             = file:${data-files}
     *     od.index.location        = ${index-files}
     *     odWriter.commit.interval = 10000
     * </properties>
     */
    @MojoParameter
    public String props
    public String props(){ this.props }


    /**
     * Comma-separated list of integer values for "Ok" exit codes.
     * If job's exit code is not on the list - the build will fail.
     */
    @MojoParameter
    public String failIfExitCodeOtherThan
    public String failIfExitCodeOtherThan(){ this.failIfExitCodeOtherThan }




    void doExecute ()
    {
        long        l                    = System.currentTimeMillis()
        def         command              = new GoldinCommandLineJobRunner()
        String[]    configLocationsSplit = configLocations().split( /\s*,\s*/ )
        def         isSet                = { String s -> ( s != null ) && ( ! s.equalsIgnoreCase( "none" )) }
        String[]    paramsSplit          = isSet( params()) ? params().trim().split() : new String[ 0 ]
        Set<String> optsSplit            = ( isSet( opts()) ? opts().trim().split()   : [] ) as Set

        if ( isSet( props()))
        {
            configLocationsSplit = [ *configLocationsSplit, propertiesConfigLocation( props()) ] as String[]
        }

        def builder = new StringBuilder()
        configLocationsSplit.eachWithIndex {

            String configLocation, int index ->

            def n        = (( index < ( configLocationsSplit.size() - 1 )) ? constants().CRLF : "" )
            def location = ( configLocation.startsWith( 'classpath:' ) ?
                                new ClassPathResource( configLocation.substring( 'classpath:'.length())).getURL() :
                                null );
            builder += ( " * [$configLocation]${ location ? ' - [' + location + ']' : '' }$n" )
        }

        log.info ( """
Starting job [${ jobId() }]:
configLocations :
$builder
parameters      : $paramsSplit
options         : $optsSplit
""" )

        int exitCode = command.start( configLocationsSplit, jobId(), paramsSplit, optsSplit )

        if ( failIfExitCodeOtherThan())
        {
            List okExitCodes = failIfExitCodeOtherThan().split( /\s*,\s*/ ).toList()

            if ( ! okExitCodes.contains( String.valueOf( exitCode )))
            {
                throw new MojoFailureException(
                    "Job [${ jobId() }] exit code is [$exitCode], " +
                    (( okExitCodes.size() == 1 ) ? "not [${ okExitCodes.first() }]" :
                                                   "none of $okExitCodes" ));
            }
        }

        log.info( "Job [${ jobId() }] finished in [${ ( System.currentTimeMillis() - l ) / 1000 }] sec, exit code [$exitCode]" )
    }


    /**
     * Generates Spring config file with properties specified
     * @return file location
     */
    private String propertiesConfigLocation( String propertiesValue )
    {
        verify().notNullOrEmpty( propertiesValue )

        def file       = new File( outputDirectory(), 'PropertyPlaceholderConfigurer.xml' )
        def lines      = propertiesValue.splitWith( 'eachLine', String ).collect { it.trim().replace( '\\', '/' ) }
        def properties = [ '', *lines ].join( "${ constants().CRLF }${ ' ' * 16 }" )
        def text       = makeTemplate( '/PropertyPlaceholderConfigurer.xml', [ properties : properties ] )

        file.write( text )

        def filePath   = file.canonicalPath
        log.info( "Properties bean written to [$filePath]:${ constants().CRLF }$text" )
        "file:$filePath"
    }
}
