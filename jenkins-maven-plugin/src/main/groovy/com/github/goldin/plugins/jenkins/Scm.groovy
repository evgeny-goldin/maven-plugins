package com.github.goldin.plugins.jenkins

import com.github.goldin.plugins.jenkins.markup.Markup


@SuppressWarnings([ 'AbstractClassWithoutAbstractMethod' ])
abstract class Scm extends Markup
{
    /**
     * Both fields are set by {@link com.github.goldin.plugins.jenkins.markup.ConfigMarkup#addScm}
     */

    Job              job
    List<Repository> repositories
    boolean          gerritTrigger
}


class None extends Scm
{
    @Override
    void addMarkup (){ builder.scm( class: 'hudson.scm.NullSCM' ){}}
}


class Cvs extends Scm
{
    @Override
    void addMarkup ()
    {
        assert repositories.size() == 1, "[${ job }] - multiple CVS repositories are not supported"
        final repository = repositories.first()

        builder.scm( class: 'hudson.scm.CVSSCM' ) {
            cvsroot( repository.remote )
            add( 'module', repository.cvsModule )
            add( 'branch', repository.cvsBranch )
            add( 'cvsRsh', repository.cvsRsh )
            canUseUpdate( repository.cvsUpdate )
            flatten( ! repository.cvsLegacy )

            if ( repository.cvsRepoBrowserClass && repository.repoBrowserUrl )
            {
                repositoryBrowser( class: repository.cvsRepoBrowserClass ) { url( repository.repoBrowserUrl ) }
            }

            isTag( repository.cvsTag )
            excludedRegions( repository.cvsExcludedRegions )
        }
    }
}


class Svn extends Scm
{
    @Override
    void addMarkup ()
    {
        builder.scm( class: 'hudson.scm.SubversionSCM' ) {
            locations {
                for ( repository in repositories ) {
                    'hudson.scm.SubversionSCM_-ModuleLocation' {
                        remote( repository.remote )
                        add( 'local', repository.local )
                    }
                }
            }
            useUpdate( job.useUpdate )
            doRevert( job.doRevert )
        }
    }
}


@SuppressWarnings([ 'AbcMetric' ])
class Git extends Scm
{
    @Override
    void addMarkup ()
    {
        final gitRepository = repositories.first()

        builder.scm( class: 'hudson.plugins.git.GitSCM' ) {

            configVersion( 2 )
            userRemoteConfigs {
                for ( repository in repositories ) {
                    'hudson.plugins.git.UserRemoteConfig' {
                        name   ( repository.gitName )
                        refspec( gerritTrigger ? '$GERRIT_REFSPEC' : repository.gitRefspec )
                        url    ( repository.remote )
                    }
                }
            }

            branches {
                for ( repository in repositories ) {
                    'hudson.plugins.git.BranchSpec' {
                        name( gerritTrigger ? '$GERRIT_BRANCH' : repository.gitBranch )
                    }
                }
            }
            add( 'localBranch', gitRepository.gitLocalBranch )

            if ( gitRepository.gitMergeRepo || gitRepository.gitMergeBranch )
            {
                userMergeOptions {
                    mergeRemote( gitRepository.gitMergeRepo )
                    mergeTarget( gitRepository.gitMergeBranch )
                }
            }

            recursiveSubmodules( gitRepository.gitUpdateSubmodules )
            doGenerateSubmoduleConfigurations( false )
            authorOrCommitter( gitRepository.gitCommitAuthor )
            clean( gitRepository.gitCleanAfterCheckout )
            wipeOutWorkspace( gitRepository.gitWipeOutWorkspace )
            pruneBranches( gitRepository.gitPruneBranches )
            remotePoll( gitRepository.gitRemotePolling )

            if ( gerritTrigger )
            {
                buildChooser( class: 'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerBuildChooser' ) {
                    separator( '#' )
                }
            }
            else
            {
                buildChooser( class: 'hudson.plugins.git.util.DefaultBuildChooser' )
            }

            gitTool( 'Default' )

            if ( gitRepository.gitRepoBrowserClass && gitRepository.repoBrowserUrl )
            {
                browser( class: gitRepository.gitRepoBrowserClass ) { url( gitRepository.repoBrowserUrl ) }
            }

            submoduleCfg( class: 'list' )
            relativeTargetDir( gitRepository.gitLocalSubdirectory )
            excludedRegions( gitRepository.gitExcludedRegions )
            excludedUsers( gitRepository.gitExcludedUsers )
            gitConfigName( gitRepository.gitConfigName )
            gitConfigEmail( gitRepository.gitConfigEmail )
            skipTag( gitRepository.gitSkipTag )
            scmName( gitRepository.gitScmName )
        }
    }
}


@SuppressWarnings([ 'GroovyClassNamingConvention' ])
class Hg extends Scm
{
    @Override
    void addMarkup ()
    {
        assert repositories.size() == 1, "[${ job }] - multiple Mercurial repositories are not supported"
        final repository = repositories.first()

        builder.scm( class: 'hudson.plugins.mercurial.MercurialSCM' ) {
            source ( repository.remote )
            modules( repository.hgModules )
            add( 'branch', repository.hgBranch )
            add( 'subdir', repository.hgSubdir )
            clean( repository.hgClean )

            if ( repository.hgRepoBrowserClass && repository.repoBrowserUrl )
            {
                browser( class: repository.hgRepoBrowserClass ) { url( repository.repoBrowserUrl ) }
            }
        }
    }
}
