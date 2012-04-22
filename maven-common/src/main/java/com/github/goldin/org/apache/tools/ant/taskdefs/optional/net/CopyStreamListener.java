package com.github.goldin.org.apache.tools.ant.taskdefs.optional.net;

import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.tools.ant.Project;

import java.util.Date;

/**
 * {@link org.apache.commons.net.io.CopyStreamListener} implementation to provide a progress tracking
 * of FTP download process
 */
public class CopyStreamListener implements org.apache.commons.net.io.CopyStreamListener
{
    private final Project project;
    private       long    mbTransferred = 0;

    public CopyStreamListener ( Project project )
    {
        this.project = project;
    }


    @Override
    public void bytesTransferred ( CopyStreamEvent event )
    {
        bytesTransferred( event.getTotalBytesTransferred(),
                          event.getBytesTransferred(),
                          event.getStreamSize());
    }


    @Override
    public void bytesTransferred ( long totalBytesTransferred, int bytesTransferred, long streamSize )
    {
        long mb = ( totalBytesTransferred / ( 1024 * 1024 ));
        if ( mb > mbTransferred )
        {
            mbTransferred = mb;
            this.project.log( "[" + new Date() + "]: [" + mbTransferred + "] Mb transferred" );
        }
    }
}
