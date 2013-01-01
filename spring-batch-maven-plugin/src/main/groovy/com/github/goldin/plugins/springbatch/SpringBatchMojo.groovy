package com.github.goldin.plugins.springbatch

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.springframework.core.io.ClassPathResource


/**
 * Spring Batch invoker
 */
@Mojo( name = 'run', defaultPhase = LifecyclePhase.INSTALL, threadSafe = true )
@SuppressWarnings([ 'StatelessClass', 'UnnecessaryPublicModifier', 'PublicInstanceField', 'NonFinalPublicField' ])
class SpringBatchMojo extends BaseGroovyMojo
{
   /**
    * Comma-separated Spring context files
    */
    @Parameter ( required = true )
    private String configLocations
    private String configLocations(){ verifyBean().notNullOrEmpty( this.configLocations ) }


    /**
     * SpringBatch job name
     */
    @Parameter ( required = true )
    private String jobId
    private String jobId(){ verifyBean().notNullOrEmpty( this.jobId )}


    /**
     * SpringBatch additional command line parameters:
     * http://static.springsource.org/spring-batch/apidocs/org/springframework/batch/core/launch/support/CommandLineJobRunner.html
     *
     * @see org.springframework.batch.core.launch.support.CommandLineJobRunner#main(String[])
     */
    @Parameter
    private String params
    private String params(){ this.params }

    /**
     * SpringBatch additional command line options (arguments starting with '-'):
     * http://static.springsource.org/spring-batch/apidocs/org/springframework/batch/core/launch/support/CommandLineJobRunner.html
     *
     * @see org.springframework.batch.core.launch.support.CommandLineJobRunner#main(String[])
     */
    @Parameter
    private String opts
    private String opts(){ this.opts }


    /**
     * Optional key = value multiline properties:
     *
     * <properties>
     *     od.input.dir             = file:${data-files}
     *     od.index.location        = ${index-files}
     *     odWriter.commit.interval = 10000
     * </properties>
     */
    @Parameter
    private String props
    private String props(){ this.props }


    /**
     * Comma-separated list of integer values for "Ok" exit codes.
     * If job's exit code is not on the list - the build will fail.
     */
    @Parameter
    private String failIfExitCodeOtherThan
    private String failIfExitCodeOtherThan(){ this.failIfExitCodeOtherThan }


    void doExecute ()
    {
        def          isSet                = { String s -> (( s ) && ( ! s.equalsIgnoreCase( 'none' ))) }
        long         l                    = System.currentTimeMillis()
        def          command              = new GoldinCommandLineJobRunner()
        List<String> configLocationsSplit = split( configLocations())
        List<String> paramsSplit          = ( isSet( params()) ? params().trim().split() : [] ) as List
        Set<String>  optsSplit            = ( isSet( opts())   ?   opts().trim().split() : [] ) as Set

        if ( isSet( props()))
        {
            configLocationsSplit += propertiesConfigLocation( props())
        }

        def builder = new StringBuilder()
        configLocationsSplit.eachWithIndex {

            String configLocation, int index ->

            def n        = (( index < ( configLocationsSplit.size() - 1 )) ? constantsBean().CRLF : '' )
            def location = ( configLocation.startsWith( 'classpath:' ) ?
                                new ClassPathResource( configLocation.substring( 'classpath:'.length())).URL :
                                null )
            builder += ( " * [$configLocation]${ location ? ' - [' + location + ']' : '' }$n" )
        }

        log.info ( """
Starting job [${ jobId() }]:
configLocations :
$builder
parameters      : $paramsSplit
options         : $optsSplit
""" )

        int exitCode = command.start( configLocationsSplit as String[], jobId(), paramsSplit as String[], optsSplit )

        if ( failIfExitCodeOtherThan())
        {
            List okExitCodes = split( failIfExitCodeOtherThan())

            if ( ! okExitCodes.contains( String.valueOf( exitCode )))
            {
                throw new MojoFailureException(
                    "Job [${ jobId() }] exit code is [$exitCode], " +
                    (( okExitCodes.size() == 1 ) ? "not [${ okExitCodes.first() }]" :
                                                   "none of $okExitCodes" ))
            }
        }

        log.info( "Job [${ jobId() }] finished in [${ ( System.currentTimeMillis() - l ) / 1000 }] sec, exit code [$exitCode]" )
    }


    /**
     * Generates Spring config file with properties specified
     * @return file location
     */
    @Requires({ propertiesValue })
    @Ensures({ result })
    private String propertiesConfigLocation( String propertiesValue )
    {
        def file       = new File( outputDirectory(), 'PropertyPlaceholderConfigurer.xml' )
        def lines      = readLines( propertiesValue ).collect { it.replace( '\\', '/' ) }
        def properties = [ '', *lines ].join( "${ constantsBean().CRLF }${ ' ' * 16 }" )
        def text       = makeTemplate( '/PropertyPlaceholderConfigurer.xml', [ properties : properties ] )

        file.write( text )

        def filePath   = file.canonicalPath
        log.info( "Properties bean written to [$filePath]:${ constantsBean().CRLF }$text" )
        "file:$filePath"
    }
}
