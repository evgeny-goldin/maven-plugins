package com.goldin.plugins.hudson

import com.goldin.plugins.common.GMojoUtils
import com.goldin.plugins.common.ThreadLocals
import org.apache.maven.plugin.logging.Log
import org.junit.Test

class RepositoryTest
{
    private static Log getLog () { ThreadLocals.get( Log.class ) }

    static { GMojoUtils.mopInit() }

    RepositoryTest ()
    {
        GMojoUtils.initTestThreadLocals()
    }


    /**
     * Reads a "/file" specified from the current class classpath and returns a list
     * of its rows, trim()-ed, cleaned up from empty lines and #-comments
     *
     * @param fileName name of the file to read
     */
    private static List<String> read( String fileName )
    {
        def    stream = RepositoryTest.class.getResourceAsStream( "/${ fileName }" )
        assert stream, "Failed to load [/${ fileName }] from [${ RepositoryTest.class }] classpath"

        stream.text.splitWith( 'eachLine' ).findAll{ it }*.trim().findAll{ ! it.startsWith( '#' ) }
    }


    private static checkEquals( Object expected, Object result, String logMessage )
    {
        assert expected == result
        log.info( logMessage )
    }


    @Test
    void getRemoteLink()
    {
        log.info( "getRemoteLink()" )

        read( 'getRemoteLink.txt' ).each {
            def ( remoteUrl, httpUrl ) = it.split()
            checkEquals( httpUrl, new Repository( remote : remoteUrl ).getRemoteLink(), "[$remoteUrl] => [$httpUrl]" )
        }
    }


    @Test
    void getGitRemoteBranchLink()
    {
        log.info( "getGitRemoteBranchLink()" )

        read( 'getGitRemoteBranchLink.txt' ).each {
            def ( remoteUrl, branchName, httpUrl ) = it.split()
            def repository                         = new Repository( remote: remoteUrl, gitBranch: branchName )

            assert repository.isGit()
            checkEquals( httpUrl, repository.getGitRemoteBranchLink(), "[$remoteUrl] + [$branchName] => [$httpUrl]" )
        }
    }


    @Test
    void getRemotePathLink()
    {
        log.info( "getRemotePathLink()" )

        read( 'getRemotePathLink.txt' ).each {
            def split      = it.split()
            def path       = split[ -2 ]
            def httpUrl    = split[ -1 ]
            def repository = ( split.size() == 3 ) ? new Repository( remote: split[ 0 ] ) :                      // SVN, no branch name
                                                     new Repository( remote: split[ 0 ], gitBranch: split[ 1 ] ) // Git

            checkEquals( httpUrl, repository.getRemotePathLink( path ), "[${ repository.remote }]${ repository.isGit() ? ' + [' + repository.gitBranch + ']' : '' } + [$path] => [$httpUrl]" )
        }
    }
}