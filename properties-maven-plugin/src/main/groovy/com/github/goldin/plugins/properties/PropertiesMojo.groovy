package com.github.goldin.plugins.properties


import static com.github.goldin.plugins.common.GMojoUtils.*
import org.jfrog.maven.annomojo.annotations.MojoThreadSafe
import com.github.goldin.gcommons.util.GroovyConfig
import com.github.goldin.plugins.common.BaseGroovyMojo
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase


/**
 * Creates Maven properties by evaluating a Groovy expression.
 */
@MojoThreadSafe
@MojoGoal( 'set-properties' )
@MojoPhase( 'validate' )
@SuppressWarnings( [ 'StatelessClass', 'PublicInstanceField', 'NonFinalPublicField' ] )
class PropertiesMojo extends BaseGroovyMojo
{
    @MojoParameter ( required = false )
    public Property   property

    @MojoParameter ( required = false )
    public Property[] properties

    private List<Property> properties() { generalBean().list( this.properties, this.property ) }

    @MojoParameter ( required = false )
    public String rawProperties

    @MojoParameter ( required = false )
    public String addDollar

    @MojoParameter ( required = false )
    public boolean normalizePath = true

    @MojoParameter ( required = false )
    public boolean verbose = true

    @MojoParameter ( required = false )
    public GroovyConfig groovyConfig = new GroovyConfig()


    private String normalizePath( String s ){
        new File( s ).with { absolute ? canonicalPath.replace( '\\', '/' ) : s }
    }


    @Override
    void doExecute()
    {
        Map<String, String> props = new LinkedHashMap (
            ( properties()  ? namedProperties() : [:] ) +
            ( rawProperties ? rawProperties()   : [:] ))

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
                String s = generalBean().choose (
                    project.properties      [ expression ],
                    session.systemProperties[ expression ],
                    session.userProperties  [ expression ],
                    map                     [ expression ],
                    System.getProperty( expression ),
                    ( expression.startsWith( 'env.' ) ? System.getenv( expression.substring( 'env.'.size())) : '' ),
                    '' )

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
            def ( String name, String value ) = line.split( /=/ ).with{ size() > 1 ? delegate[ 0, 1 ] : [ delegate[ 0 ], '' ] }*.trim()
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
            def value     = addDollar( p.value?.trim() ?: '', addDollar )
            def isVerbose = generalBean().choose( p.verbose, verbose )

            if ( value.startsWith( '{{' ) && value.endsWith( '}}' ))
            {
                groovyConfig.verbose = isVerbose
                value = eval( value, String, groovyConfig )
            }
            else
            {
                value = interpolate( name, value, [:] )
            }

            [ name, value ]
        }
    }
}
