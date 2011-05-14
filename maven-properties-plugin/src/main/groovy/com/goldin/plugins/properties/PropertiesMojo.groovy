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
    public String addDollar

    @MojoParameter ( required = false )
    public boolean normalizePath = true

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
        Map<String, String> props = [:]
        def normalizePath         = { String value -> def file = new File( value )
                                                      file.isAbsolute() ? file.canonicalPath.replace( '\\', '/' ) : value }

        if ( rawProperties ) { props += rawProperties()   }
        if ( properties())   { props += namedProperties() }

        def padName = maxKeyLength( props )

        props.each {
            String name, String value ->
            assert name && value
            setProperty( name, ( normalizePath ? normalizePath( value ) : value ), '', verbose, padName )
        }
    }

    
    /**
     * Sets raw properties specified.
     */
    private Map<String, String> rawProperties ()
    {
        assert rawProperties

        Map<String, String> map = rawProperties.readLines().inject( [:] ) {
            Map m, String line ->
            def ( String name, String value ) = line.split( /=/ )[ 0, 1 ]*.trim()
            m[ name ] = addDollar( value, addDollar )
            m
        }

        /**
         * Interpolates the value specified: "aa${expression}" => "aabb"
         */
        def interpolate = {
            String name, String value ->
            while ( value.contains( '${' ))
            {
                value = value.replaceAll( /\$\{(.+?)\}/ ){
                    String all, String expression ->
                    assert expression
                    assert expression != name, "Property [$name] has a circular definition with itself"
                    String newValue    = map[ expression ] ?: System.getProperty( expression )
                    if (( ! newValue ) && ( expression.startsWith( 'env.' )))
                    {
                        newValue = System.getenv( expression.substring( 'env.'.size()))
                    }
                    assert newValue, "Unable to interpolate \${$expression} - unknown value"
                    newValue
                }
            }
            value
        }

        ( Map<String, String> ) map.collectEntries{ String name, String value -> [ name, interpolate( name, value ) ] }
    }


    /**
     * Set named properties specified.
     */
    private Map<String, String> namedProperties()
    {
        assert properties()
        def map = [:]

        for ( property in properties())
        {
            def name      = property.name?.trim()
            def value     = addDollar( property.value?.trim(), addDollar )
            def isVerbose = generalBean().choose( property.verbose, verbose )

            if ( value.startsWith( '{{' ) && value.endsWith( '}}' ))
            {
                groovyConfig.verbose = isVerbose
                value = eval( value, String, groovyConfig )
            }

            map[ name ] = value
        }

        map
    }
}
