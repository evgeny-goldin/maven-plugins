package com.github.goldin.plugins.jenkins

import groovy.xml.MarkupBuilder
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires

abstract class Scm
{
    Job              job
    List<Repository> repositories

    @Ensures({ result })
    abstract String getScmClass()

    @Requires({ builder && job && ( repositories != null ) })
    abstract void buildMarkup( MarkupBuilder builder, Job job, List<Repository> repositories )


    /**
     * Whether repositories should be verified to be defined
     */
    boolean verifyRepositories(){ true }

    /**
     * Adds tag specified to the builder only if value specified evaluates to true according to the Groovy Truth.
     */
    final void addIf( MarkupBuilder builder, String tagName, Object value )
    {
        if ( value ) { builder."$tagName"( value ) }
    }

    /**
     * Builds SCM section markup.
     *
     * @return SCM section markup
     */
    final String getMarkup()
    {
        final writer  = new StringWriter()
        final builder = new MarkupBuilder( new IndentPrinter( writer, ' ' * 4 ))

        builder.scm( class: scmClass ) {
            assert job && ( repositories || ( ! verifyRepositories()))
            buildMarkup( builder, job, repositories )
        }

        writer.toString()
    }
}


class None extends Scm
{
    @Override
    String getScmClass(){ 'hudson.scm.NullSCM' }

    @Override
    boolean verifyRepositories(){ false }

    @Override
    void buildMarkup( MarkupBuilder builder, Job job, List<Repository> repositories ){}
}


class Cvs extends Scm
{
    @Override
    String getScmClass(){ 'hudson.scm.CVSSCM' }

    @Override
    void buildMarkup( MarkupBuilder builder, Job job, List<Repository> repositories )
    {
        assert repositories.size() == 1, "[${ job }] - multiple CVS repositories are not supported"
        final repository = repositories.first()

        builder.with {

            cvsroot( repository.remote )
            addIf( builder, 'module', repository.cvsModule )
            addIf( builder, 'branch', repository.cvsBranch )
            addIf( builder, 'cvsRsh', repository.cvsRsh )
            canUseUpdate( repository.cvsUpdate )
            flatten( ! repository.cvsLegacy )

            if ( repository.cvsRepoBrowserClass && repository.repoBrowserUrl )
            {
                repositoryBrowser( class: repository.cvsRepoBrowserClass ) {
                    url( repository.repoBrowserUrl )
                }
            }

            isTag( repository.cvsTag )
            excludedRegions( repository.cvsExcludedRegions )
        }
    }
}


class Svn extends Scm
{
    @Override
    String getScmClass(){ 'hudson.scm.SubversionSCM' }

    @Override
    void buildMarkup( MarkupBuilder builder, Job job, List<Repository> repositories )
    {
        builder.with {

            locations {
                for ( repository in repositories ) {
                    "${ scmClass }_-ModuleLocation" {
                        remote( repository.remote )
                        addIf( builder, 'local', repository.local )
                    }
                }
            }
            useUpdate( job.useUpdate )
            doRevert( job.doRevert )
        }
    }
}


class Git extends Scm
{
    @Override
    String getScmClass(){ 'hudson.plugins.git.GitSCM' }

    @Override
    void buildMarkup( MarkupBuilder builder, Job job, List<Repository> repositories )
    {
        final gitRepository = repositories.first()

        builder.with {

            configVersion( 2 )
            userRemoteConfigs {
                for ( repository in repositories ) {
                    'hudson.plugins.git.UserRemoteConfig' {
                        name( repository.gitName )
                        refspec( repository.gitRefspec )
                        url( repository.remote )
                    }
                }
            }
            branches {
                for ( repository in repositories ) {
                    'hudson.plugins.git.BranchSpec' {
                        name( repository.gitBranch )
                    }
                }
            }
            addIf( builder, 'localBranch', gitRepository.gitLocalBranch )

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
            buildChooser( class: 'hudson.plugins.git.util.DefaultBuildChooser' )
            gitTool( 'Default' )

            if ( gitRepository.gitRepoBrowserClass && gitRepository.repoBrowserUrl )
            {
                browser( class: gitRepository.gitRepoBrowserClass ) {
                    url( gitRepository.repoBrowserUrl )
                }
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
    String getScmClass(){ 'hudson.plugins.mercurial.MercurialSCM' }

    @Override
    void buildMarkup( MarkupBuilder builder, Job job, List<Repository> repositories )
    {
        assert repositories.size() == 1, "[${ job }] - multiple Mercurial repositories are not supported"
        final repository = repositories.first()

        builder.with {

            source ( repository.remote )
            modules( repository.hgModules )
            addIf( builder, 'branch', repository.hgBranch )
            addIf( builder, 'subdir', repository.hgSubdir )
            clean( repository.hgClean )

            if ( repository.hgRepoBrowserClass && repository.repoBrowserUrl )
            {
                browser( class: repository.hgRepoBrowserClass ) {
                    url( repository.repoBrowserUrl )
                }
            }
        }
    }
}


