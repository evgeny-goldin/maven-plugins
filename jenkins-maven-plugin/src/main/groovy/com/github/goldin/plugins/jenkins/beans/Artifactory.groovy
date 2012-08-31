package com.github.goldin.plugins.jenkins.beans


/**
 * Artifactory deploy data container
 */
class Artifactory
{
    String  name                = ''
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
