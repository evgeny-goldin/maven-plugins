package com.goldin.plugins.jenkins.beans


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
     * http://wiki.jenkins-ci.org/display/JENKINS/Parameterized+Trigger+Plugin
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
