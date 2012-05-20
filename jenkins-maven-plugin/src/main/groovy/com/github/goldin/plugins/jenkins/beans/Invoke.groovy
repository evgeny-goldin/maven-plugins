package com.github.goldin.plugins.jenkins.beans

import static com.github.goldin.plugins.common.GMojoUtils.*


/**
 * Specifies invocation of other jobs: "Trigger parameterized build on other projects" checkbox
 */
@SuppressWarnings( 'StatelessClass' )
class Invoke
{
    String      jobs               // Comma-separated job IDs to invoke
    Set<String> jobsSplit          // Job IDs to invoke (split)
    boolean     always    = false  // Whether jobs should always be invoked
    boolean     stable    = true   // Whether jobs should be invoked if build is stable
    boolean     unstable  = false  // Whether jobs should be invoked if build is unstable (tests failed)
    boolean     failed    = false  // Whether jobs should be invoked if build has failed

    /**
     * Job parameters
     */

    boolean     triggerWithoutParameters = false  // Trigger build without parameters
    boolean     currentBuildParams       = false  // Current build parameters
    boolean     subversionRevisionParam  = false  // "Subversion revision" parameter
    boolean     gitCommitParam           = false  // Pass-through Git Commit that was built
    String      params                            // Predefined parameters
    String      propertiesFileParams              // Parameters from properties file


   /**
    * Sets the jobs (job IDs) to invoke.
    * Called by Maven when reading plugin <configuration>
    */
    void setJobs( String jobs )
    {
        assert jobs?.trim()?.length()
        this.jobs      = jobs
        this.jobsSplit = ( split( this.jobs ) as Set ).asImmutable()
    }


    /**
     * Gets invocation condition according to "Parameterized Trigger Plugin"
     * http://wiki.jenkins-ci.org/display/JENKINS/Parameterized+Trigger+Plugin
     */
    List<String> getCondition ()
    {
        final condition =
          ( always  || ( stable && unstable && failed )) ? [ 'ALWAYS', 'is <strong>complete</strong> (always trigger)' ] :
          ( stable && unstable )                         ? [ 'UNSTABLE_OR_BETTER', 'is <strong>stable</strong> or <strong>unstable</strong> but not failed' ] :
          ( failed   )                                   ? [ 'FAILED',   'has <strong>failed</strong>'  ] :
          ( unstable )                                   ? [ 'UNSTABLE', 'is <strong>unstable</strong>' ] :
          ( stable   )                                   ? [ 'SUCCESS',  'is <strong>stable</strong>'   ] :
                                                                           null

        assert condition, 'At least one of <stable>, <unstable> or <failed> should be set to "true" to enable invocation of [$jobs]'
        condition
    }
}
