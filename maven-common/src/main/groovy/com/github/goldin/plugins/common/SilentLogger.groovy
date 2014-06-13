package com.github.goldin.plugins.common


/**
 * Logger implementation swallowing all messages sent to it.
 */
@SuppressWarnings([ 'MethodCount' ])
class SilentLogger extends    org.apache.log4j.Logger
                   implements org.apache.maven.plugin.logging.Log,
                              org.codehaus.plexus.logging.Logger,
                              org.apache.commons.logging.Log,
                              org.eclipse.aether.spi.log.Logger,
                              org.slf4j.Logger,
                              org.apache.ivy.util.MessageLogger
{
    SilentLogger (){ super( 'SilentLogger' ) }

    @Override
    void log ( String msg, int level ){}

    @Override
    void rawlog ( String msg, int level ){}

    @Override
    void verbose ( String msg ){}

    @Override
    void deprecated ( String msg ){}

    @Override
    void rawinfo ( String msg ){}

    @Override
    List getProblems (){ [] }

    @Override
    List getWarns () { [] }

    @Override
    List getErrors () { [] }

    @Override
    void clearProblems (){}

    @Override
    void sumupProblems (){}

    @Override
    void progress (){}

    @Override
    void endProgress (){}

    @Override
    void endProgress ( String msg ){}

    @Override
    boolean isShowProgress () { false }

    @Override
    void setShowProgress ( boolean progress ){}

    @Override
    boolean isErrorEnabled ( org.slf4j.Marker marker ){ true }

    @Override
    boolean isFatalEnabled (){ true }

    @Override
    boolean isFatalErrorEnabled (){ true }

    @Override
    boolean isErrorEnabled (){ true }

    @Override
    boolean isWarnEnabled ( org.slf4j.Marker marker ){ false }

    @Override
    boolean isWarnEnabled (){ false }

    @Override
    boolean isInfoEnabled (){ false }

    @Override
    boolean isInfoEnabled ( org.slf4j.Marker marker ){ false }

    @Override
    boolean isDebugEnabled (){ false }

    @Override
    boolean isTraceEnabled ( org.slf4j.Marker marker ){ false  }

    @Override
    boolean isDebugEnabled ( org.slf4j.Marker marker ){ false }

    @Override
    void debug ( CharSequence content ){}

    @Override
    void debug ( CharSequence content, Throwable error ){}

    @Override
    void debug ( Throwable error ){}

    @Override
    void info ( CharSequence content ){}

    @Override
    void info ( CharSequence content, Throwable error ){}

    @Override
    void info ( Throwable error ){}

    @Override
    void warn ( CharSequence content ){}

    @Override
    void warn ( CharSequence content, Throwable error ){}

    @Override
    void warn ( Throwable error ){}

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
    int getThreshold (){ org.codehaus.plexus.logging.Logger.LEVEL_DISABLED }

    @Override
    void setThreshold ( int threshold ){}

    @Override
    org.codehaus.plexus.logging.Logger getChildLogger ( String name ){ null }


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
    void trace ( org.slf4j.Marker marker, String s ){}

    @Override
    void trace ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void trace ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void trace ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void trace ( org.slf4j.Marker marker, String s, Throwable throwable ){}

    @Override
    void debug ( String s, Object o ){}

    @Override
    void debug ( String s, Object o, Object o1 ){}

    @Override
    void debug ( String s, Object... objects ){}

    @Override
    void debug ( org.slf4j.Marker marker, String s ){}

    @Override
    void debug ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void debug ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void debug ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void debug ( org.slf4j.Marker marker, String s, Throwable throwable ){}

    @Override
    void info ( String s, Object o ){}

    @Override
    void info ( String s, Object o, Object o1 ){}

    @Override
    void info ( String s, Object... objects ){}

    @Override
    void info ( org.slf4j.Marker marker, String s ){}

    @Override
    void info ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void info ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void info ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void info ( org.slf4j.Marker marker, String s, Throwable throwable ){}

    @Override
    void warn ( String s, Object o ){}

    @Override
    void warn ( String s, Object... objects ){}

    @Override
    void warn ( String s, Object o, Object o1 ){}

    @Override
    void warn ( org.slf4j.Marker marker, String s ){}

    @Override
    void warn ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void warn ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void warn ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void warn ( org.slf4j.Marker marker, String s, Throwable throwable ){}

    @Override
    void error ( String s, Object o ){}

    @Override
    void error ( String s, Object o, Object o1 ){}

    @Override
    void error ( String s, Object... objects ){}

    @Override
    void error ( org.slf4j.Marker marker, String s ){}

    @Override
    void error ( org.slf4j.Marker marker, String s, Object o ){}

    @Override
    void error ( org.slf4j.Marker marker, String s, Object o, Object o1 ){}

    @Override
    void error ( org.slf4j.Marker marker, String s, Object... objects ){}

    @Override
    void error ( org.slf4j.Marker marker, String s, Throwable throwable ){}
}
