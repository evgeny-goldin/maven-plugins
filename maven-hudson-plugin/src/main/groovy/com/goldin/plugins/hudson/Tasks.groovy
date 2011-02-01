package com.goldin.plugins.hudson


abstract class Task
{
    String getHudsonClass(){ "hudson.tasks.${ this.class.simpleName }" }
    abstract String getMarkup()

    String buildMarkup( List<String> properties )
    {
        StringBuilder builder = new StringBuilder()
        
        for ( property in properties )
        {
            String value = this[ property ] as String
            if ( value )
            {
                builder.append( "<$property>$value</$property>\n" )
            }
        }

        builder.toString().readLines()*.trim().join( "\n" )
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
    
    String getMarkup () { buildMarkup([ 'targets', 'mavenName', 'pom', 'jvmOptions', 'properties', 'usePrivateRepository' ])
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
