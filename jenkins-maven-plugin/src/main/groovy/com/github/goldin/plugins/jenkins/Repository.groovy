package com.github.goldin.plugins.jenkins

import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires

/**
 * Source repository location
 */
@SuppressWarnings( 'StatelessClass' )
class Repository
{
    String  remote
    String  remoteLink
    String  local
    String  repoBrowser    = ''
    String  repoBrowserUrl = ''

    /**
     * CVS-specific properties
     */

    String  cvsModule               = ''
    String  cvsBranch               = ''
    String  cvsRsh                  = ''     // "CVS_RSH" advanced option
    String  cvsExcludedRegions      = ''     // "Excluded Regions" advanced option
    boolean cvsTag                  = false  // "This is a tag, not a branch" option
    boolean cvsUpdate               = true   // "Use update" advanced option
    boolean cvsLegacy               = false  // "Legacy mode" advanced option

    String getCvsRepoBrowserClass ()
    {
        ( repoBrowser == 'ViewCVS' ? 'hudson.scm.browsers.ViewCVS'    :
          repoBrowser == 'FishEye' ? 'hudson.scm.browsers.FishEyeCVS' :
                                     '' )
    }

    /**
     * Git-specific properties
     */

    String  gitName    = 'origin'
    String  gitRefspec = '+refs/heads/*:refs/remotes/origin/*'
    String  gitBranch  = 'master'

    /**
     * Git branch "Advanced" options
     */

    String  gitExcludedRegions    = ''
    String  gitExcludedUsers      = ''
    String  gitLocalBranch        = ''
    String  gitLocalSubdirectory  = ''
    String  gitScmName            = ''
    String  gitConfigName         = ''
    String  gitConfigEmail        = ''
    String  gitMergeRepo          = ''
    String  gitMergeBranch        = ''
    String  getGitRepoBrowserClass()
    {
        ( repoBrowser == 'githubweb'    ? 'hudson.plugins.git.browser.GithubWeb'    :
          repoBrowser == 'gitoriousweb' ? 'hudson.plugins.git.browser.GitoriousWeb' :
          repoBrowser == 'gitweb'       ? 'hudson.plugins.git.browser.GitWeb'       :
          repoBrowser == 'redmineweb'   ? 'hudson.plugins.git.browser.RedmineWeb'   :
                                          '' )
    }

    boolean gitPruneBranches      = false
    boolean gitSkipTag            = false
    boolean gitCleanAfterCheckout = false
    boolean gitRemotePolling      = false
    boolean gitUpdateSubmodules   = false
    boolean gitCommitAuthor       = false
    boolean gitWipeOutWorkspace   = false
    boolean gitorious             = false

    /**
    * Whether this repository is a Git/Gitorious/SVN/CVS repository
    */
    boolean isGit()        { remote.endsWith( '.git' ) }
    boolean isGitorious () { assert isGit(); ( remote.contains( 'gitorious.org/' ) || this.gitorious ) }
    boolean isSvn()        { ( remote.startsWith( 'http://' )   || remote.startsWith( 'https://' ) || remote.startsWith( 'svn://' )) }
    boolean isCVS()        { ( remote.startsWith( ':pserver:' ) || ( ! ( isGit() || isSvn()))) }


    /**
     * Hg-specific properties
     */

    String  hgBranch  = 'default'
    boolean hgClean   = false
    String  hgModules = ''
    String  hgSubdir  = ''
    String  getHgRepoBrowserClass()
    {
        ( repoBrowser == 'bitbucket'       ? 'hudson.plugins.mercurial.browser.BitBucket'       :
          repoBrowser == 'fisheye'         ? 'hudson.plugins.mercurial.browser.FishEye'         :
          repoBrowser == 'googlecode'      ? 'hudson.plugins.mercurial.browser.GoogleCode'      :
          repoBrowser == 'hgweb'           ? 'hudson.plugins.mercurial.browser.HgWeb'           :
          repoBrowser == 'kiln'            ? 'hudson.plugins.mercurial.browser.KilnHG'          :
          repoBrowser == 'rhodecode'       ? 'hudson.plugins.mercurial.browser.RhodeCode'       :
          repoBrowser == 'rhodecodelegacy' ? 'hudson.plugins.mercurial.browser.RhodeCodeLegacy' :
                                             '' )
    }

   /**
    * Sets repository remote and HTTP URL.
    * @param remote repository remote URL
    */
    void setRemote( String remote )
    {
        this.remote     = remote.replaceAll( '/$', '' ) // Trimming trailing '/'
        this.remoteLink = this.remote

        if ( isGit())
        {
            this.remoteLink =

            /**
             * http://repo.or.cz/
             */

            // "git://repo.or.cz/jetpack.git" => "http://repo.or.cz/w/jetpack.git"
            remote.startsWith( 'git://repo.or.cz/' )    ? remote.replace( 'git://repo.or.cz/',
                                                                          'http://repo.or.cz/w/' ) :

            // "http://repo.or.cz/r/jetpack.git" => "http://repo.or.cz/w/jetpack.git"
            remote.startsWith( 'http://repo.or.cz/r/' ) ? remote.replace( 'http://repo.or.cz/r/',
                                                                          'http://repo.or.cz/w/' ) :
            /**
             * http://github.com/
             * http://gitorious.org/
             */

            // "git://github.com/evgeny-goldin/WideFinder.git" => "http://github.com/evgeny-goldin/WideFinder"
            // "git://gitorious.org/qt/qt.git"                 => "http://gitorious.org/qt/qt"
            ( remote.startsWith( 'git://' ) ? remote.replace( 'git://', 'http://' ) :

            // "git@github.com:evgeny-goldin/WideFinder.git" => "http://github.com/evgeny-goldin/WideFinder"
              remote.startsWith( 'git@' )   ? remote.replace( ':', '/' ).replace( 'git@', 'http://' ) :

            // "https://evgeny-goldin@github.com/evgeny-goldin/WideFinder.git" => "http://github.com/evgeny-goldin/WideFinder"
            // "http://git.gitorious.org/qt/qt.git"                            => "http://git.gitorious.org/qt/qt"
              remote.endsWith( '.git' )     ? remote.replaceFirst( 'https?://.+@', 'http://' ) :
                                              remote ).
            replaceFirst( /\.git$/, '' )
        }
        else if ( isCVS())
        {
            /**
             * http://durak.org/cvswebsites/howto-cvs/node9.html
             * http://www.idevelopment.info/data/Programming/change_management/unix_cvs/PROGRAMMING_Logging_into_CVS.shtml
             * ":pserver:<username>@<computername>:<repository>"
             * ":pserver:username@interactivate.com:/usr/local/cvs-repository"
             * ":pserver:anonymous@simple-wicket.cvs.sourceforge.net:/cvsroot/simple-wicket"
             */

            def matcher     = ( remote =~ /^:pserver:[^@]+@([^:]+):.+$/ )
            this.remoteLink = ( matcher ? "http://${ matcher[ 0 ][ 1 ] }" + ( this.cvsModule ? "/viewvc/${ this.cvsModule }/${ this.cvsModule }" : '' ) :
                                          remote )
        }

        assert ( ! this.remote.endsWith( '/' )) && ( ! this.remoteLink.endsWith( '/' ))
    }


    /**
     * Retrieves a remote HTTP link for Git branch
     *
     * @return remote HTTP link for Git branch
     */
     String getGitRemoteBranchLink()
     {
         assert isGit()
         "${ remoteLink }/${ isGitorious() ? 'trees' : 'tree' }/${ gitBranch }"
     }


   /**
    * Retrieves a remote HTTP link for the path specified.
    *
    * @param path project path
    * @return     remote HTTP link for the path specified
    */
    @Requires({ path })
    @Ensures({ result })
    String getRemotePathLink( String path )
    {
        ( isGit() ? "${ remoteLink }/${ isGitorious() ? 'blobs' : 'blob' }/${ gitBranch }" :
                    remoteLink ) +
        "/$path"
    }
}
