package com.goldin.plugins.timestamp

import com.goldin.plugins.common.BaseGroovyMojo
import com.goldin.plugins.common.GMojoUtils
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import static com.goldin.plugins.common.GMojoUtils.*


/**
 * Timestamp properties creation MOJO
 */
@MojoGoal( 'timestamp' )
@MojoPhase( 'validate' )
class TimestampMojo extends BaseGroovyMojo
{
    @MojoParameter ( required = false )
    public String time

    @MojoParameter ( required = false )
    public Timestamp[] timestamps

    @MojoParameter ( required = false )
    public  Timestamp timestamp
    private Timestamp[] timestamps() { general().array( this.timestamps, this.timestamp, Timestamp ) }


    TimestampMojo ()
    {
    }


    /**
     *
     * @throws org.apache.maven.plugin.MojoExecutionException
     * @throws org.apache.maven.plugin.MojoFailureException
     */
    void doExecute()
    {
        Date date = ( time ? GMojoUtils.groovy( time, Date ) : new Date())

        for ( t in timestamps())
        {
            String value = t.format( date )
            GMojoUtils.setProperty( t.property, value,
                                    "Property \${$t.property} is set to \"$value\": " +
                                    "date [$date], pattern [$t.pattern], timezone [$t.timezone], locale [$t.locale]" )
        }
    }
}
