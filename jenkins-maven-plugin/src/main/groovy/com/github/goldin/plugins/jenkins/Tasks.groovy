package com.github.goldin.plugins.jenkins

import com.github.goldin.plugins.jenkins.markup.Markup
import org.gcontracts.annotations.Requires


/**
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Free-Style projects tasks
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */


abstract class Task extends Markup
{
    /**
     * Class name wrapping a task.
     */
    String getMarkupClassName (){ "hudson.tasks.${ this.class.simpleName }" }


    /**
     * Adds task's markup.
     */
    abstract void addTaskMarkup ()


    /**
     * Title and command used in description table
     */
    abstract String getDescriptionTableTitle  ()
    abstract String getDescriptionTableCommand()


    /**
     * Validates task configuration correctness.
     */
    abstract void validate()


    /**
     * Used by the template - retrieves a shortened version of tasks's "command".
     */
    @Requires({ descriptionTableCommand && descriptionTableTitle })
    final String getCommandShortened(){ descriptionTableCommand.with { size() > 80 ? substring( 0, 80 ) + ' ..' : delegate }}


    /**
     * Adds property values to the {@link #builder}.
     */
    @Requires({ propertyNames })
    final void addProperties( Collection<String> propertyNames )
    {
        for ( propertyName in propertyNames )
        {
            assert propertyName
            builder."${ propertyName }"( this[ propertyName ] )
        }
    }


    /**
     * Adds task's markup to the {@link #builder}.
     */
    @Override
    void addMarkup()
    {
        assert builder
        validate()
        builder."${ markupClassName }" { addTaskMarkup() }
    }
}


@SuppressWarnings( 'StatelessClass' )
class Ant extends Task
{
    String antName    = ''
    String targets    = ''
    String antOpts    = ''
    String buildFile  = 'build.xml'
    String properties = ''

    @Override
    void addTaskMarkup (){ addProperties([ ( antName ? 'antName' : '' ), 'targets', 'antOpts', 'buildFile', 'properties' ].grep()) }

    @Override
    String getDescriptionTableTitle  (){ 'ant' }

    @Override
    String getDescriptionTableCommand(){ targets ?: 'default targets' }

    @Override
    void validate (){ assert buildFile, "Task <ant>: <buildFile> is not specified" }
}


@SuppressWarnings( 'StatelessClass' )
class BatchFile extends Task
{
    String command = ''

    @Override
    void addTaskMarkup (){ addProperties([ 'command' ]) }

    @Override
    String getDescriptionTableTitle  () { 'batch' }

    @Override
    String getDescriptionTableCommand(){ command }

    @Override
    void validate (){ assert command, "Task <batchFile>: <command> is not specified" }
}


@SuppressWarnings([ 'StatelessClass' ])
class Gradle extends Task
{
    String  gradleName         = ''
    String  description        = ''
    String  switches           = ''
    String  tasks              = 'build'
    String  rootBuildScriptDir = '.'
    String  buildFile          = 'build.gradle'
    boolean useWrapper         = false

    @Override
    String getMarkupClassName (){ 'hudson.plugins.gradle.Gradle' }

    @Override
    void addTaskMarkup ()
    {
        addProperties([ 'description', 'switches', 'tasks', 'rootBuildScriptDir',
                        'buildFile', 'gradleName', 'useWrapper' ])
    }

    @Override
    String getDescriptionTableTitle  (){ 'gradle' }

    @Override
    String getDescriptionTableCommand(){ tasks ?: 'default tasks' }

    @Override
    void validate ()
    {
        assert rootBuildScriptDir,             "Task <gradle>: <rootBuildScriptDir> is not specified"
        assert buildFile,                      "Task <gradle>: <buildFile> is not specified"
        assert ! ( gradleName && useWrapper ), "Task <gradle>: both <gradleName> and <useWrapper> can't be used"
    }
}


@SuppressWarnings([ 'StatelessClass' ])
class Groovy extends Task
{
    String  groovyName       = ''
    boolean pre              = false       // Whether groovy task is executed as pre or post-step
    String  command          = ''
    String  file             = ''
    String  parameters       = ''
    String  scriptParameters = ''
    String  properties       = ''
    String  javaOpts         = ''
    String  classPath        = ''

    @Override
    String getMarkupClassName () { 'hudson.plugins.groovy.Groovy' }

    @Override
    void addTaskMarkup ()
    {
        final className = 'hudson.plugins.groovy.' + ( command ? 'StringScriptSource' : 'FileScriptSource' )

        builder.scriptSource( class: className ) {
            "${ command ? 'command' : 'scriptFile' }"( command ?: file )
        }

        addProperties([ 'groovyName', 'parameters', 'scriptParameters', 'properties', 'javaOpts', 'classPath' ])
    }

    @Override
    String getDescriptionTableTitle  (){ 'groovy' }

    @Override
    String getDescriptionTableCommand(){ command ?: file }

    @Override
    void validate ()
    {
        assert   ( command || file ), "Task <groovy>: <command> or <file> needs to be specified"
        assert ! ( command && file ), "Task <groovy>: both <command> and <file> can't be used"
    }
}

@SuppressWarnings( 'StatelessClass' )
class Maven extends Task
{
    String  mavenName            = '(Default)'
    String  targets              = Job.DEFAULT_MAVEN_GOALS
    String  jvmOptions           = ''
    String  pom                  = 'pom.xml'
    String  properties           = ''
    boolean usePrivateRepository = false

    @Override
    void addTaskMarkup ()
    {
        addProperties([ 'targets', 'mavenName', 'jvmOptions', ( pom == 'false' ? '' : 'pom' ), 'properties', 'usePrivateRepository' ].grep())
    }

    @Override
    String getDescriptionTableTitle  (){ 'maven' }

    @Override
    String getDescriptionTableCommand(){ targets }

    @Override
    void validate ()
    {
        assert targets, "Task <maven>: <targets> not specified"
        assert pom,     "Task <maven>: <pom> is not specified"
    }
}


@SuppressWarnings( 'StatelessClass' )
class Shell extends Task
{
    String command = ''

    @Override
    void addTaskMarkup (){ addProperties([ 'command' ]) }

    @Override
    String getDescriptionTableTitle  (){ 'shell' }

    @Override
    String getDescriptionTableCommand(){ command }

    @Override
    void validate (){ assert command, "Task <shell>: <command> is not specified" }
}



@SuppressWarnings([ 'StatelessClass' ])
class Xml extends Task
{
    /**
     * Raw XML content that can be set for the task.
     */
    String content = ''

    @Override
    void addMarkup (){ add( content ) }

    @Override
    void addTaskMarkup (){ assert false /* Shouldn't be called */ }

    @Override
    String getDescriptionTableTitle  (){ 'xml' }

    @Override
    String getDescriptionTableCommand(){ 'custom task' }

    @Override
    void validate (){ assert content, "Task <xml>: <content> is not specified" }
}
