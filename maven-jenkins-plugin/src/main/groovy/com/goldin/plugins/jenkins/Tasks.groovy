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
            if ( value )
            {
                lines << "<$property>${ value.split( '\n' )*.trim().join( ' ' ) }</$property>"
            }
        }

        space + lines.join( "\n$space" )
    }
}

class Shell extends Task
{
    String command
    String getMarkup () { buildMarkup([ 'command' ]) }
}

class Maven extends Task
{
    String  mavenName            = '(Default)'
    String  targets              = '-e clean install'
    String  pom                  = 'pom.xml'
    String  jvmOptions           = ''
    String  properties           = ''
    boolean usePrivateRepository = false

    String getMarkup ()
    {
        pom != 'false' ? buildMarkup([ 'targets', 'mavenName', 'pom', 'jvmOptions', 'properties', 'usePrivateRepository' ]) :
                         buildMarkup([ 'targets', 'mavenName',        'jvmOptions', 'properties', 'usePrivateRepository' ])
    }
}

class BatchFile extends Task
{
    String command
    String getMarkup () { buildMarkup([ 'command' ]) }
}

class Ant extends Task
{
    String targets
    String buildFile  = 'build.xml'
    String antOpts    = ''
    String properties = ''
    String getMarkup () { buildMarkup([ 'targets', 'buildFile',  'antOpts', 'properties' ]) }
}
