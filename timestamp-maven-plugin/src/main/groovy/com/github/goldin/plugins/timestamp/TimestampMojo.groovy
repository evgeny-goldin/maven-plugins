package com.github.goldin.plugins.timestamp

import static com.github.goldin.plugins.common.GMojoUtils.*
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter


/**
 * Timestamp properties creation MOJO
 */
@Mojo( name = 'timestamp', defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true )
@SuppressWarnings([ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ])

class TimestampMojo extends BaseGroovyMojo
{
    @Parameter ( required = false )
    private String time

    @Parameter ( required = false )
    private Timestamp[] timestamps

    @Parameter ( required = false )
    private Timestamp timestamp
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
