/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included  with this distribution in *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.james.transport.match;

import java.util.*;
import org.apache.arch.*;
import org.apache.mail.Mail;
import org.apache.james.transport.*;

/**
 * @version 1.0.0, 24/04/1999
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 */
public class HostIsLocal extends AbstractMatch {
    
    private Vector serverNames;
    
    public void setContext(Context context) {
        serverNames = (Vector) context.get(Resources.SERVER_NAMES);
    }

    public Vector match(Mail mail, String condition) {
        Vector matchingRecipients = new Vector();
        for (Enumeration e = mail.getRecipients().elements(); e.hasMoreElements(); ) {
            String rec = (String) e.nextElement();
            if (serverNames.contains(getHost(rec))) {
                matchingRecipients.addElement(rec);
            }
        }
        return matchingRecipients;
    }

    private String getHost(String recipient) {
        return recipient.substring(recipient.indexOf("@") + 1);
    }
}
    
