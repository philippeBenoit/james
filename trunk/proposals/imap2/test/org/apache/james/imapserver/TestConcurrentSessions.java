////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2003, Wotif.com. All rights reserved.
//
// This is unpublished proprietary source code of Wotif.com.
// The copyright notice above does not evidence any actual or intended
// publication of such source code.
//
////////////////////////////////////////////////////////////////////////////////
package org.apache.james.imapserver;

import org.apache.james.imapserver.TestCommandsInAuthenticatedState;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author <a href="mailto:ddeboer@thoughtworks.com">Darrell DeBoer</a>
 * @version $Id: TestConcurrentSessions.java,v 1.2 2003/11/30 07:03:48 darrell Exp $
 */
public class TestConcurrentSessions extends TestCommandsInAuthenticatedState {
    public TestConcurrentSessions(String fileName) {
        super(fileName);
    }

    /**
     * Runs all tests which verify the behaviour of IMAP under multiple concurrent sessions.
     */
    public static Test suite() throws Exception
    {
        TestSuite suite = new TestSuite();
        // Not valid in this state
        suite.addTest( new TestConcurrentSessions( "concurrent/FetchResponse" ) );
        suite.addTest( new TestConcurrentSessions( "concurrent/ExistsResponse" ) );
        suite.addTest( new TestConcurrentSessions( "concurrent/ExpungeResponse" ) );

        return suite;
    }
}
