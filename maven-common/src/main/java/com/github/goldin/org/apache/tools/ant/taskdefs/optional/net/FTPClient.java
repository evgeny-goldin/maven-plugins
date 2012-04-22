package com.github.goldin.org.apache.tools.ant.taskdefs.optional.net;

import org.apache.commons.net.ftp.FTPCommand;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.FromNetASCIIInputStream;
import org.apache.commons.net.io.Util;
import org.apache.tools.ant.Project;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * {@link org.apache.commons.net.ftp.FTPClient} extension to use
 * {@link org.apache.commons.net.io.CopyStreamListener} in
 * {@link #retrieveFile(String, java.io.OutputStream)}
 */
public class FTPClient extends org.apache.commons.net.ftp.FTPClient
{

    private int     __fileType = org.apache.commons.net.ftp.FTP.ASCII_FILE_TYPE;
    private Project project;

    public FTPClient ( Project project )
    {
        this.project = project;
    }


    @Override
    public boolean setFileType ( int fileType ) throws IOException
    {
        __fileType = fileType;
        return super.setFileType( fileType );
    }


    @Override
    public boolean setFileType ( int fileType, int formatOrByteSize ) throws IOException
    {
        __fileType = fileType;
        return super.setFileType( fileType, formatOrByteSize );
    }


    /**
     * Copied from {@link org.apache.commons.net.ftp.FTPClient#retrieveFile(String, java.io.OutputStream)},
     * "commons-net", v2.1,
     * with minor change to utilize {@link org.apache.commons.net.io.CopyStreamListener}
     * in
     * {@link Util#copyStream(java.io.InputStream, java.io.OutputStream, int, long, org.apache.commons.net.io.CopyStreamListener, boolean)}
     */
    @Override
    public boolean retrieveFile(String remote, OutputStream local)
    throws IOException
    {
        InputStream input;
        Socket socket;

        if ((socket = _openDataConnection_( FTPCommand.RETR, remote)) == null)
            return false;

        input = new BufferedInputStream(socket.getInputStream(),
                getBufferSize());
        if (__fileType == ASCII_FILE_TYPE)
            input = new FromNetASCIIInputStream(input);
        // Treat everything else as binary for now
        try
        {
            Util.copyStream(input, local, getBufferSize(),
                    CopyStreamEvent.UNKNOWN_STREAM_SIZE, new CopyStreamListener( this.project ),
                    false);
        }
        catch (IOException e)
        {
            try
            {
                socket.close();
            }
            catch (IOException f)
            {}
            throw e;
        }
        socket.close();
        return completePendingCommand();
    }
}
