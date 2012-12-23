package com.github.goldin.plugins.timestamp


import static com.github.goldin.plugins.common.GMojoUtils.*
import org.jfrog.maven.annomojo.annotations.MojoThreadSafe
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase


/**
 * Timestamp properties creation MOJO
 */
@MojoThreadSafe
@MojoGoal( 'timestamp' )
@MojoPhase( 'validate' )
@SuppressWarnings( [ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ] )
class TimestampMojo extends BaseGroovyMojo
{
    @MojoParameter ( required = false )
    public String time

    @MojoParameter ( required = false )
    public Timestamp[] timestamps

    @MojoParameter ( required = false )
    public  Timestamp timestamp
    private List<Timestamp> timestamps() { generalBean().list( this.timestamps, this.timestamp ) }


    void doExecute()
    {
        Date date = ( time ? (( Date ) eval( time, Date )) : new Date())

        for ( t in timestamps())
        {
            String value = t.format( date )
            setProperty( t.property, value,
                         "Property \${$t.property} is set to \"$value\": " +
                         "date [$date], pattern [$t.pattern], timezone [$t.timezone], locale [$t.locale]" )
        }
    }
}
