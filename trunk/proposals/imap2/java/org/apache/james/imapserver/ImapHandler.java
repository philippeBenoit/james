/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache", "Jakarta", "JAMES" and "Apache Software Foundation"
 *    must not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * Portions of this software are based upon public domain software
 * originally written at the National Center for Supercomputing Applications,
 * University of Illinois, Urbana-Champaign.
 */

package org.apache.james.imapserver;

import org.apache.avalon.cornerstone.services.connection.ConnectionHandler;
import org.apache.avalon.excalibur.pool.Poolable;
import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.logger.Logger;
import org.apache.avalon.framework.logger.LogEnabled;
import org.apache.james.Constants;
import org.apache.james.imapserver.commands.ImapCommand;
import org.apache.james.imapserver.commands.ImapCommandFactory;
import org.apache.james.imapserver.commands.CommandParser;
import org.apache.james.imapserver.store.ImapMailbox;
import org.apache.mailet.MailRepository;
import org.apache.mailet.User;
import org.apache.mailet.UsersRepository;
import org.apache.james.util.InternetPrintWriter;
import org.apache.james.util.watchdog.Watchdog;
import org.apache.james.util.watchdog.WatchdogTarget;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.Reader;
import java.io.InputStream;
import java.net.Socket;

/**
 * The handler class for IMAP connections.
 * TODO: This is a quick cut-and-paste hack from POP3Handler. This, and the ImapServer
 * should probably be rewritten from scratch.
 *
 * @author Federico Barbieri <scoobie@systemy.it>
 * @author Peter M. Goldstein <farsight@alum.mit.edu>
 */
public class ImapHandler
        extends AbstractLogEnabled
        implements ConnectionHandler, Poolable, ImapConstants
{

    private String softwaretype = "JAMES IMAP4rev1 Server " + Constants.SOFTWARE_VERSION;
    private ImapRequestHandler requestHandler = new ImapRequestHandler();
    private ImapSession session;

    /**
     * The per-service configuration data that applies to all handlers
     */
    private ImapHandlerConfigurationData theConfigData;

    /**
     * The mail server's copy of the user's inbox
     */
    private MailRepository userInbox;

    /**
     * The thread executing this handler
     */
    private Thread handlerThread;

    /**
     * The TCP/IP socket over which the IMAP interaction
     * is occurring
     */
    private Socket socket;

    /**
     * The reader associated with incoming characters.
     */
    private BufferedReader in;

    /**
     * The socket's input stream.
     */
    private InputStream ins;

    /**
     * The writer to which outgoing messages are written.
     */
    private PrintWriter out;

    /**
     * The socket's output stream
     */
    private OutputStream outs;

    /**
     * The watchdog being used by this handler to deal with idle timeouts.
     */
    private Watchdog theWatchdog;

    /**
     * The watchdog target that idles out this handler.
     */
    private WatchdogTarget theWatchdogTarget = new IMAPWatchdogTarget();

    /**
     * Set the configuration data for the handler.
     *
     * @param theData the configuration data
     */
    void setConfigurationData( ImapHandlerConfigurationData theData )
    {
        theConfigData = theData;
    }

    /**
     * Set the Watchdog for use by this handler.
     *
     * @param theWatchdog the watchdog
     */
    void setWatchdog( Watchdog theWatchdog )
    {
        this.theWatchdog = theWatchdog;
    }

    /**
     * Gets the Watchdog Target that should be used by Watchdogs managing
     * this connection.
     *
     * @return the WatchdogTarget
     */
    WatchdogTarget getWatchdogTarget()
    {
        return theWatchdogTarget;
    }

    /**
     * Idle out this connection
     */
    void idleClose()
    {
        // TODO: Send BYE message before closing.
        if ( getLogger() != null ) {
            getLogger().error( "IMAP Connection has idled out." );
        }
        try {
            if ( socket != null ) {
                socket.close();
            }
        }
        catch ( Exception e ) {
            // ignored
        }
        finally {
            socket = null;
        }

        synchronized ( this ) {
            // Interrupt the thread to recover from internal hangs
            if ( handlerThread != null ) {
                handlerThread.interrupt();
                handlerThread = null;
            }
        }

    }

    /**
     * @see ConnectionHandler#handleConnection(Socket)
     */
    public void handleConnection( Socket connection )
            throws IOException
    {

        String remoteHost = "";
        String remoteIP = "";

        try {
            this.socket = connection;
            synchronized ( this ) {
                handlerThread = Thread.currentThread();
            }
            ins = socket.getInputStream();
            in = new BufferedReader( new InputStreamReader( socket.getInputStream(), "ASCII" ), 512 );
            remoteIP = socket.getInetAddress().getHostAddress();
            remoteHost = socket.getInetAddress().getHostName();
        }
        catch ( IOException e ) {
            if ( getLogger().isErrorEnabled() ) {
                StringBuffer exceptionBuffer =
                        new StringBuffer( 256 )
                        .append( "Cannot open connection from " )
                        .append( remoteHost )
                        .append( " (" )
                        .append( remoteIP )
                        .append( "): " )
                        .append( e.getMessage() );
                getLogger().error( exceptionBuffer.toString(), e );
            }
            throw e;
        }

        if ( getLogger().isInfoEnabled() ) {
            StringBuffer logBuffer =
                    new StringBuffer( 128 )
                    .append( "Connection from " )
                    .append( remoteHost )
                    .append( " (" )
                    .append( remoteIP )
                    .append( ") " );
            getLogger().info( logBuffer.toString() );
        }

        try {
            outs = new BufferedOutputStream( socket.getOutputStream(), 1024 );
            out = new InternetPrintWriter( outs, true );
            ImapResponse response = new ImapResponse( outs );

            // Write welcome message
            StringBuffer responseBuffer =
                    new StringBuffer( 256 )
                    .append( VERSION )
                    .append( " Server " )
                    .append( theConfigData.getHelloName() )
                    .append( " ready" );
            response.okResponse( null, responseBuffer.toString() );

            session = new ImapSessionImpl( theConfigData.getImapHost(),
                                           theConfigData.getUsersRepository(),
                                           this,
                                           socket.getInetAddress().getHostName(),
                                           socket.getInetAddress().getHostAddress());

            theWatchdog.start();
            while ( requestHandler.handleRequest( ins, outs, session ) ) {
                theWatchdog.reset();
            }
            theWatchdog.stop();

            //Write BYE message.
            if ( getLogger().isInfoEnabled() ) {
                StringBuffer logBuffer =
                        new StringBuffer( 128 )
                        .append( "Connection for " )
                        .append( session.getUser().getUserName() )
                        .append( " from " )
                        .append( remoteHost )
                        .append( " (" )
                        .append( remoteIP )
                        .append( ") closed." );
                getLogger().info( logBuffer.toString() );
            }

        }
        catch ( Exception e ) {
            out.println( "Error closing connection." );
            out.flush();
            StringBuffer exceptionBuffer =
                    new StringBuffer( 128 )
                    .append( "Exception on connection from " )
                    .append( remoteHost )
                    .append( " (" )
                    .append( remoteIP )
                    .append( ") : " )
                    .append( e.getMessage() );
            getLogger().error( exceptionBuffer.toString(), e );
        }
        finally {
            resetHandler();
        }
    }

    /**
     * Resets the handler data to a basic state.
     */
    void resetHandler()
    {

        if ( theWatchdog != null ) {
            if ( theWatchdog instanceof Disposable ) {
                ( ( Disposable ) theWatchdog ).dispose();
            }
            theWatchdog = null;
        }

        // Close and clear streams, sockets

        try {
            if ( socket != null ) {
                socket.close();
                socket = null;
            }
        }
        catch ( IOException ioe ) {
            // Ignoring exception on close
        }
        finally {
            socket = null;
        }

        try {
            if ( in != null ) {
                in.close();
            }
        }
        catch ( Exception e ) {
            // Ignored
        }
        finally {
            in = null;
        }

        try {
            if ( out != null ) {
                out.close();
            }
        }
        catch ( Exception e ) {
            // Ignored
        }
        finally {
            out = null;
        }

        try {
            if ( outs != null ) {
                outs.close();
            }
        }
        catch ( Exception e ) {
            // Ignored
        }
        finally {
            outs = null;
        }

        synchronized ( this ) {
            handlerThread = null;
        }

        // Clear user data
        session = null;

        // Clear config data
        theConfigData = null;
    }

    /**
     * Implements a "stat".  If the handler is currently in
     * a transaction state, this amounts to a rollback of the
     * mailbox contents to the beginning of the transaction.
     * This method is also called when first entering the
     * transaction state to initialize the handler copies of the
     * user inbox.
     *
     */
    private void stat()
    {
//        userMailbox = new Vector();
//        userMailbox.addElement(DELETED);
//        for (Iterator it = userInbox.list(); it.hasNext(); ) {
//            String key = (String) it.next();
//            MailImpl mc = userInbox.retrieve(key);
//            // Retrieve can return null if the mail is no longer in the store.
//            // In this case we simply continue to the next key
//            if (mc == null) {
//                continue;
//            }
//            userMailbox.addElement(mc);
//        }
//        backupUserMailbox = (Vector) userMailbox.clone();
    }

    /**
     * This method logs at a "DEBUG" level the response string that
     * was sent to the POP3 client.  The method is provided largely
     * as syntactic sugar to neaten up the code base.  It is declared
     * private and final to encourage compiler inlining.
     *
     * @param responseString the response string sent to the client
     */
    private final void logResponseString( String responseString )
    {
        if ( getLogger().isDebugEnabled() ) {
            getLogger().debug( "Sent: " + responseString );
        }
    }

    /**
     * Write and flush a response string.  The response is also logged.
     * Should be used for the last line of a multi-line response or
     * for a single line response.
     *
     * @param responseString the response string sent to the client
     */
    final void writeLoggedFlushedResponse( String responseString )
    {
        out.println( responseString );
        out.flush();
        logResponseString( responseString );
    }

    /**
     * Write a response string.  The response is also logged.
     * Used for multi-line responses.
     *
     * @param responseString the response string sent to the client
     */
    final void writeLoggedResponse( String responseString )
    {
        out.println( responseString );
        logResponseString( responseString );
    }

    /**
     * A private inner class which serves as an adaptor
     * between the WatchdogTarget interface and this
     * handler class.
     */
    private class IMAPWatchdogTarget
            implements WatchdogTarget
    {

        /**
         * @see WatchdogTarget#execute()
         */
        public void execute()
        {
            ImapHandler.this.idleClose();
        }

    }

}

