package com.goldin.plugins.mail

import static com.goldin.plugins.common.GMojoUtils.*
import com.goldin.plugins.common.BaseGroovyMojo
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import org.jfrog.maven.annomojo.annotations.MojoGoal
import org.jfrog.maven.annomojo.annotations.MojoParameter
import org.jfrog.maven.annomojo.annotations.MojoPhase
import javax.mail.*
import javax.mail.Message.RecipientType


/**
 * MOJO that sends mails with attachments
 */
@MojoGoal( 'send' )
@MojoPhase( 'install' )
@SuppressWarnings( 'StatelessClass' )
class MailMojo extends BaseGroovyMojo
{
    @MojoParameter
    public String smtp = 'specify.your.SMTP.server'

    @MojoParameter
    public String from = 'specify@your.mail'

    @MojoParameter ( required = true )
    public Map<String, String> mails

    @MojoParameter
    public String subject = ''

    @MojoParameter
    public String text = ''

    @MojoParameter
    public File textFile

    @MojoParameter
    public File[] files


    MailMojo ()
    {
    }


    @Override
    void doExecute()
    {
        Properties props = new Properties()
        props[ 'mail.smtp.host' ] = verify().notNullOrEmpty( smtp )
        props[ 'mail.from'      ] = verify().notNullOrEmpty( from )

        Message             message    = new MimeMessage( Session.getInstance( props, null ))
        Map<String, String> recipients = setRecipients( message, mails )

        message.subject = subject
        attachFiles( message, text, textFile, files )

        Transport.send( message )

        log.info( "Mail from [$from] sent to [$recipients] (through [$smtp] SMTP server)" )
        log.info( "Subject [$subject], files attached [${ files*.canonicalPath ?: 'none' }]" )
    }


    /**
     * Sets message recipients
     *
     * @param message message to set recipients to
     * @param mails   <code>Map</code> of recipients:
     *                key   - one of either "to", "cc" or "bcc"
     *                value - mail addresses, separated with ""
     * @return recipients mapping, split and converted to {@link InternetAddress} instances
     */
    private Map<String, String> setRecipients( Message message, Map<String, String> mails )
    {
        def recipients = [:]

        for ( to in mails.keySet())
        {
            RecipientType recipientType = (( 'to'  == to ) ? RecipientType.TO  :
                                           ( 'cc'  == to ) ? RecipientType.CC  :
                                           ( 'bcc' == to ) ? RecipientType.BCC :
                                                             null )
            assert recipientType, "Unknown recipient type [$to]. Known types are \"to\", \"cc\", and \"bcc\"."

            if ( ! mails[ to ] )
            {
                // Empty section
                continue
            }

            List<Address> addresses = split( mails[ to ], ';' ).collect{ new InternetAddress( it )}
            message.setRecipients( recipientType, addresses as Address[] )

            assert ! recipients[ recipientType.toString() ], "<$recipientType> is specified more than once"
            recipients[ recipientType.toString() ] = addresses.toString()
            log.info( "[$recipientType] recipients: [$addresses]" )
        }

        recipients
    }


    /**
     * Attaches files to the message specified.
     *
     * @param message  message to attach files to
     * @param text     message text body, if empty - <code>textFile</code> should be used instead
     * @param textFile file to be used as mail body (if <code>text</code> is <code>null</code>), may be <code>null</code>
     * @param files    files to attach, may be <code>null</code>
     */
    private void attachFiles( Message message, String text, File textFile, File[] files )
    {
        if ( files )
        {
            Multipart mp = new MimeMultipart()
            mp.addBodyPart( textBodyPart( text, textFile ))

            for ( file in files )
            {
                mp.addBodyPart( fileBodyPart( file ))
                log.info( "File [$file.canonicalPath] attached" )
            }

            message.content = mp
        }
        else
        {
            message.text = text
        }
    }


    /**
     * Convenience wrapper returning text {@link MimeBodyPart}
     *
     * @param text text to put in {@link MimeBodyPart}
     * @param file file to add to text, may be <code>null</code>
     *
     * @return {@link MimeBodyPart} containing the text specified
     */
    private static MimeBodyPart textBodyPart( String text, File file )
    {
        MimeBodyPart mbp = new MimeBodyPart()
        mbp.text = "$text${ constants().CRLF }${ file ? file.text : '' }"
        mbp
    }


    /**
     * Convenience wrapper returning file {@link MimeBodyPart}
     *
     * @param file file to attach to {@link MimeBodyPart}
     * @return {@link MimeBodyPart} having attached the file specified
     */
    private static MimeBodyPart fileBodyPart( File file )
    {
        MimeBodyPart mbp = new MimeBodyPart()
        mbp.attachFile( file )
        mbp
    }
}
