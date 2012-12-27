package com.github.goldin.plugins.silencer

import org.slf4j.Marker


/**
 * Logger swallowing messages sent to it.
 */
class SilencerLogger extends    org.apache.log4j.Logger
                     implements org.apache.maven.plugin.logging.Log,
                                org.codehaus.plexus.logging.Logger,
                                org.apache.commons.logging.Log,
                                org.slf4j.Logger
{
    SilencerLogger (){ super( 'SilencerLogger' ) }

    @Override
    boolean isDebugEnabled (){ false }

    @Override
    void debug ( CharSequence content ){}

    @Override
    void debug ( CharSequence content, Throwable error ){}

    @Override
    void debug ( Throwable error ){}

    @Override
    boolean isInfoEnabled (){ false }

    @Override
    void info ( CharSequence content ){}

    @Override
    void info ( CharSequence content, Throwable error ){}

    @Override
    void info ( Throwable error ){}

    @Override
    boolean isWarnEnabled (){ false }

    @Override
    void warn ( CharSequence content ){}

    @Override
    void warn ( CharSequence content, Throwable error ){}

    @Override
    void warn ( Throwable error ){}

    @Override
    boolean isErrorEnabled (){ false }

    @Override
    void error ( CharSequence content ){}

    @Override
    void error ( CharSequence content, Throwable error ){}

    @Override
    void error ( Throwable error ){}

    @Override
    void debug ( String message ){}

    @Override
    void debug ( String message, Throwable throwable ){}

    @Override
    void info ( String message ){}

    @Override
    void info ( String message, Throwable throwable ){}

    @Override
    void warn ( String message ){}

    @Override
    void warn ( String message, Throwable throwable ){}

    @Override
    void error ( String message ){}

    @Override
    void error ( String message, Throwable throwable ){}

    @Override
    void fatalError ( String message ){}

    @Override
    void fatalError ( String message, Throwable throwable ){}

    @Override
    boolean isFatalErrorEnabled (){ return false }

    @Override
    int getThreshold (){ return 0 }

    @Override
    void setThreshold ( int threshold ){}

    @Override
    org.codehaus.plexus.logging.Logger getChildLogger ( String name ){ return null }

    @Override
    boolean isFatalEnabled (){ false }

    @Override
    void trace ( String s ){}

    @Override
    void trace ( String s, Object o ){}

    @Override
    void trace ( String s, Object o, Object o1 ){}

    @Override
    void trace ( String s, Object... objects ){}

    @Override
    void trace ( String s, Throwable throwable ){}

    @Override
    boolean isTraceEnabled ( Marker marker ){ false  }

    @Override
    void trace ( Marker marker, String s ){}

    @Override
    void trace ( Marker marker, String s, Object o ){}

    @Override
    void trace ( Marker marker, String s, Object o, Object o1 ){}

    @Override
    void trace ( Marker marker, String s, Object... objects ){}

    @Override
    void trace ( Marker marker, String s, Throwable throwable ){}

    @Override
    void debug ( String s, Object o ){}

    @Override
    void debug ( String s, Object o, Object o1 ){}

    @Override
    void debug ( String s, Object... objects ){}

    @Override
    boolean isDebugEnabled ( Marker marker ){ false }

    @Override
    void debug ( Marker marker, String s ){}

    @Override
    void debug ( Marker marker, String s, Object o ){}

    @Override
    void debug ( Marker marker, String s, Object o, Object o1 ){}

    @Override
    void debug ( Marker marker, String s, Object... objects ){}

    @Override
    void debug ( Marker marker, String s, Throwable throwable ){}

    @Override
    void info ( String s, Object o ){}

    @Override
    void info ( String s, Object o, Object o1 ){}

    @Override
    void info ( String s, Object... objects ){}

    @Override
    boolean isInfoEnabled ( Marker marker ){ false }

    @Override
    void info ( Marker marker, String s ){}

    @Override
    void info ( Marker marker, String s, Object o ){}

    @Override
    void info ( Marker marker, String s, Object o, Object o1 ){}

    @Override
    void info ( Marker marker, String s, Object... objects ){}

    @Override
    void info ( Marker marker, String s, Throwable throwable ){}

    @Override
    void warn ( String s, Object o ){}

    @Override
    void warn ( String s, Object... objects ){}

    @Override
    void warn ( String s, Object o, Object o1 ){}

    @Override
    boolean isWarnEnabled ( Marker marker ){ false }

    @Override
    void warn ( Marker marker, String s ){}

    @Override
    void warn ( Marker marker, String s, Object o ){}

    @Override
    void warn ( Marker marker, String s, Object o, Object o1 ){}

    @Override
    void warn ( Marker marker, String s, Object... objects ){}

    @Override
    void warn ( Marker marker, String s, Throwable throwable ){}

    @Override
    void error ( String s, Object o ){}

    @Override
    void error ( String s, Object o, Object o1 ){}

    @Override
    void error ( String s, Object... objects ){}

    @Override
    boolean isErrorEnabled ( Marker marker ){ false }

    @Override
    void error ( Marker marker, String s ){}

    @Override
    void error ( Marker marker, String s, Object o ){}

    @Override
    void error ( Marker marker, String s, Object o, Object o1 ){}

    @Override
    void error ( Marker marker, String s, Object... objects ){}

    @Override
    void error ( Marker marker, String s, Throwable throwable ){}
}
