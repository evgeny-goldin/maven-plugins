package com.goldin.plugins.jenkins


abstract class Task
{
    String getHudsonClass(){ "hudson.tasks.${ this.class.simpleName }" }
    abstract String getMarkup()

    private final String space = ' ' * 8

    String buildMarkup( List<String> properties )
    {
        List<String> lines = []

        for ( property in properties )
        {
            String value = this[ property ] as String
            if ( value ) { lines << "<$property>${ value.trim() }</$property>" }
        }

        space + lines.join( "\n$space" )
    }
}

@SuppressWarnings( 'StatelessClass' )
class Shell extends Task
{
    String command
    String getMarkup () { buildMarkup([ 'command' ]) }
}

@SuppressWarnings( 'StatelessClass' )
class Maven extends Task
{
    String  targets              = '-B -e clean install'
    String  mavenName            = '(Default)'
    String  jvmOptions           = ''
    String  pom                  = 'pom.xml'
    String  properties           = ''
    boolean usePrivateRepository = false

    String getMarkup ()
    {
        buildMarkup( ( List<String> ) [ 'targets', 'mavenName', 'jvmOptions' ] +
                                      ( pom == 'false' ? [] : [ 'pom' ] ) +
                                      [ 'properties', 'usePrivateRepository' ] )
    }
}

@SuppressWarnings( 'StatelessClass' )
class BatchFile extends Task
{
    String command
    String getMarkup () { buildMarkup([ 'command' ]) }
}

@SuppressWarnings( 'StatelessClass' )
class Ant extends Task
{
    String targets
    String antOpts    = ''
    String buildFile  = 'build.xml'
    String properties = ''
    String getMarkup () { buildMarkup([ 'targets', 'antOpts', 'buildFile', 'properties' ]) }
}
