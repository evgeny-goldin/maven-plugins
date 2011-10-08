package com.goldin.plugins.properties

import com.goldin.gcommons.util.GroovyConfig
import com.goldin.plugins.common.BaseGroovyMojo
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import static com.goldin.plugins.common.GMojoUtils.*

@MojoGoal( 'set-properties' )
@MojoPhase( 'validate' )
@SuppressWarnings( 'StatelessClass' )
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


    private String normalizePath( String s ){
        new File( s ).with { absolute ? canonicalPath.replace( '\\', '/' ) : s }
    }


    @Override
    void doExecute()
    {
        Map<String, String> props = ( rawProperties ? rawProperties()   : [:] ) +
                                    ( properties()  ? namedProperties() : [:] )

        assert props, 'No properties defined. Use <rawProperties> or <properties> to define them.'

        def padName = maxKeyLength( props )

        props.each {
            String name, String value ->
            assert name && ( value != null )
            setProperty( name, ( normalizePath ? normalizePath( value ) : value ), '', verbose, padName )
        }
    }


    /**
     * Interpolates the value specified: "aa${expression}${anotherExpression}" => "aabbcc"
     */
    @Requires({ name && ( value != null ) && ( map != null ) })
    @Ensures({ result != null })
    private String interpolate( String name, String value, Map map )
    {
        String newValue = value
        while ( newValue.contains( '${' ))
        {
            newValue = newValue.replaceAll( /\$\{(.+?)\}/ ) {
                String all, String expression ->
                assert expression
                assert expression != name, "Property [$name] has a circular definition dependency on itself"
                String s = map[ expression ] ?: System.getProperty( expression )
                if (( s == null ) && ( expression.startsWith( 'env.' )))
                {
                    s = System.getenv( expression.substring( 'env.'.size()))
                }
                assert ( s != null ), "Unable to interpolate \${$expression} - unknown value"
                s
            }
        }
        newValue
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

        map.each { String name, String value -> map[ name ] = interpolate( name, value, map )}
        map
    }


    /**
     * Set named properties specified.
     */
    private Map<String, String> namedProperties()
    {
        assert properties()

        properties().collectEntries {
            Property p ->

            def name      = p.name?.trim()
            def value     = addDollar( p.value?.trim(), addDollar )
            def isVerbose = generalBean().choose( p.verbose, verbose )

            if ( value.startsWith( '{{' ) && value.endsWith( '}}' ))
            {
                groovyConfig.verbose = isVerbose
                value = eval( value, String, groovyConfig )
            }

            [ name, value ]
        }
    }
}
