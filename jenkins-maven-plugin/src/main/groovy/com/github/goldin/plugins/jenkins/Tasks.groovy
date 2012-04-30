package com.github.goldin.plugins.jenkins


abstract class Task
{
    final String space = ' ' * 4

    @SuppressWarnings( 'GetterMethodCouldBeProperty' )
    String   getClassName  (){ "hudson.tasks.${ this.class.simpleName }" }

    @SuppressWarnings( 'GetterMethodCouldBeProperty' )
    String   getExtraMarkup(){ '' }

    abstract List<String> getPropertyNames()

    final String getMarkup()
    {
        List<String> lines = [ extraMarkup ]

        for ( property in propertyNames )
        {
            String value = this[ property ] as String
            if ( value ) { lines << "<$property>${ value.trim() }</$property>" }
        }

        "<$className>\n" + space + lines.grep().join( "\n$space" ) + "\n</$className>"
    }
}


@SuppressWarnings( 'StatelessClass' )
class Shell extends Task
{
    String command

    @Override
    List<String> getPropertyNames(){[ 'command' ]}
}


@SuppressWarnings( 'StatelessClass' )
class BatchFile extends Task
{
    String command

    @Override
    List<String> getPropertyNames(){[ 'command' ]}
}


@SuppressWarnings( 'StatelessClass' )
class Ant extends Task
{
    String antName
    String targets
    String antOpts    = ''
    String buildFile  = 'build.xml'
    String properties = ''

    @Override
    List<String> getPropertyNames(){[ ( antName ? 'antName' : '' ),
                                      'targets', 'antOpts', 'buildFile', 'properties' ].grep() }
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

    @Override
    List<String> getPropertyNames(){[ 'targets', 'mavenName', 'jvmOptions',
                                      ( pom == 'false' ? '' : 'pom' ),
                                      'properties', 'usePrivateRepository' ].grep() }
}


@SuppressWarnings( 'StatelessClass' )
class Groovy extends Task
{
    boolean pre       // Whether groovy task is executed as pre or post-step
    String  command
    String  file
    String  groovyName
    String  parameters
    String  scriptParameters
    String  properties
    String  javaOpts
    String  classPath

    @Override
    @SuppressWarnings( 'GetterMethodCouldBeProperty' )
    String getClassName ( ) { 'hudson.plugins.groovy.Groovy' }

    @Override
    String getExtraMarkup ( )
    {
        assert ( command || file ), 'Either <command> or <file> needs to be specified for <groovy>'
        final className  = command ? 'hudson.plugins.groovy.StringScriptSource' :
                                     'hudson.plugins.groovy.FileScriptSource'
        final commandTag = command ? 'command' :
                                     'scriptFile'

        """|<scriptSource class=\"$className\">
           |$space$space<$commandTag>${ command ?: file }</$commandTag>
           |$space</scriptSource>""".stripMargin()
    }

    @Override
    List<String> getPropertyNames (){ [ 'groovyName', 'parameters', 'scriptParameters',
                                        'properties', 'javaOpts', 'classPath' ] }
}