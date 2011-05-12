package com.goldin.plugins.properties

import com.goldin.gcommons.util.GroovyConfig
import com.goldin.plugins.common.BaseGroovyMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import static com.goldin.plugins.common.GMojoUtils.*

@MojoGoal( 'set-properties' )
@MojoPhase( 'validate' )
class PropertiesMojo extends BaseGroovyMojo
{
    @MojoParameter ( required = false )
    public String rawProperties

    @MojoParameter ( required = false )
    public Property[] properties

    @MojoParameter ( required = false )
    public Property   property

    @MojoParameter ( required = false )
    public boolean verbose = true

    @MojoParameter ( required = false )
    public GroovyConfig groovyConfig = new GroovyConfig()

    private Property[] properties() { generalBean().array( this.properties, this.property, Property ) }


    @Override
    public void doExecute() throws MojoExecutionException, MojoFailureException
    {
        if ( rawProperties )
        {
            Properties p = new Properties()
            p.load( new StringReader( rawProperties ))
            def map1 = [ *:p ]
            def map2 = [:]

            while ( map1.values().any { String value -> value.contains( '${' ) })
            {
                map1.each {
                    String name, String value ->
                    map2[ name ] = value.contains( '${' ) ? value.replaceAll( /\$\{(.+?)\}/ ){ map1[ it[ 1 ]] } :
                                                            value
                }

                map1 = map2
                map2 = [:]
            }

            map1.each { String name, String value -> setProperty( name, value, '', verbose ) }
        }


        for ( property in properties())
        {
            String name      = property.name?.trim()
            String value     = property.value?.trim()
            def    isVerbose = generalBean().choose( property.verbose, verbose )

            if ( value.startsWith( '{{' ) && value.endsWith( '}}' ))
            {
                groovyConfig.verbose = isVerbose
                value = eval( value, String, groovyConfig )
            }

            setProperty( name, value, '', isVerbose )
        }
    }
}
