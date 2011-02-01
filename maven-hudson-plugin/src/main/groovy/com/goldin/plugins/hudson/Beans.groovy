package com.goldin.plugins.hudson

import com.goldin.gcommons.GCommons

/**
 * "Deploy artifacts to Maven repository" Hudson option
 */
class Deploy
{
    String  url                    /* URL of the Maven repository to deploy artifacts to */
    String  id             = ''    /* "~/.m2/settings.xml" repository ID   */
    boolean uniqueVersion  = false /* Assign unique versions to snapshots  */
    boolean evenIfUnstable = false /* Deploy even if the build is unstable */
}


 /**
 * Artifactory deploy data container
 */
class Artifactory
{
    String  name
    String  repository          = 'libs-releases-local'  /* Repository to deploy release artifacts to   */
    String  snapshotsRepository = 'libs-snapshots-local' /* Repository to deploy snapshots artifacts to */

    String  user                = ''     /* User with permissions to deploy to the selected of Artifactory repository */
    String  scrambledPassword   = ''     /* Scrambled password for the user entered above (take from 'config.xml')    */
    String  violationRecipients = ''     /* Space-separated list of recipient addresses that need to be notified of license violations in the build info */

    boolean deployArtifacts     = false  /* False if you wish to deploy the build info only (useful if you use maven to deploy artifacts) */
    boolean includeEnvVars      = false  /* True  if you wish to include all environment variables accessible by the builds process */
    boolean skipBuildInfoDeploy = false  /* False if you wish to deploy build information to Artifactory */
    boolean runChecks           = false  /* True  if you wish that automatic license scanning will occur after the build is complete */
    boolean evenIfUnstable      = false  /* True  if you wish deployment to be performed even if the build is unstable */
}


/**
 * Mailing options
 */
class Mail
{
    String  recipients
    boolean sendForUnstable   = true
    boolean sendToIndividuals = true
}


/**
 * Job trigger
 */
class Trigger
{
    static final Map TYPES = [ scm   : 'hudson.triggers.SCMTrigger',
                               timer : 'hudson.triggers.TimerTrigger' ]
    String type
    String description  // Description to be used (by normal humans) as comment
    String expression   // Crontab expression

    String getTriggerClass()
    {
        def    triggerClass = TYPES[ this.type ]
        assert triggerClass, "Unknown trigger <type>${ this.type }</type>. Known types are ${ TYPES.keySet() }"
               triggerClass
    }
}


/**
 * Specifies invocation of other jobs: "Trigger parameterized build on other projects" checkbox
 */
class Invoke
{
    boolean     always    = false  // Whether other jobs should always be invoked
    boolean     stable    = true   // Whether other jobs should be invoked if build is stable
    boolean     unstable  = false  // Whether other jobs should be invoked if build is unstable (tests failed)
    boolean     failed    = false  // Whether other jobs should be invoked if build has failed
    String      jobs               // Comma-separated job IDs to invoke
    Set<String> jobsSplit          // Job IDs to invoke (split)


   /**
    * Sets the jobs (job IDs) to invoke.
    * Called by Maven when reading plugin <configuration>
    */
    void setJobs( String jobs )
    {
        assert jobs?.trim()?.length()
        this.jobs      = jobs
        this.jobsSplit = new HashSet<String>( this.jobs.split( /\s*,\s*/ ).toList()).asImmutable()
    }


    /**
     * Gets invocation condition according to "Parameterized Trigger Plugin"
     * http://wiki.hudson-ci.org/display/HUDSON/Parameterized+Trigger+Plugin
     */
    List<String> getCondition ()
    {
        def condition =
            (( always ) || ( stable && unstable && failed ))  ? [ 'ALWAYS',             'is <strong>complete</strong> (always trigger)'                ] :
            ( stable && unstable )                            ? [ 'UNSTABLE_OR_BETTER', 'is <strong>stable</strong> or <strong>unstable</strong> but not failed' ] :
            ( failed   )                                      ? [ 'FAILED',             'has <strong>failed</strong>'   ]                          :
            ( unstable )                                      ? [ 'UNSTABLE',           'is <strong>unstable</strong>' ]                          :
            ( stable   )                                      ? [ 'SUCCESS',            'is <strong>stable</strong>'   ]                          :
                                                                null

        assert condition, 'At least one of <stable>, <unstable> or <failed> should be set to "true" to enable invocation of [$jobs]'
        condition
    }
}


class DescriptionRow
{
    String  key
    String  value
    boolean bottom     = true
    boolean escapeHTML = true

    String getKey()           { escape( this.key   ) }
    String getValue()         { escape( this.value ) }
    String escape ( String s ){ this.escapeHTML ?
                                    s.replace( '<', '&lt;' ).replace( '>', '&gt;' ).replace( '"', '&quot;' ) :
                                    s }
}


/**
 * Parametrized build parameter
 */
enum  ParameterType { bool, choice, string, password, run, file, jira }
class Parameter
{
    String        name        = "UNDEFINED"
    ParameterType type        = null
    String        value       = ""
    String        description = ""

    void setType( String type ) { this.type = ParameterType.valueOf( type ) }

    @Override
    int     hashCode ()           { "[$name][$type]".hashCode()  }

    @Override
    boolean equals   ( Object o ) { ( o instanceof Parameter ) &&
                                    ( this.name            == o.name            ) &&
                                    ( this.type.toString() == o.type.toString() ) }
}

