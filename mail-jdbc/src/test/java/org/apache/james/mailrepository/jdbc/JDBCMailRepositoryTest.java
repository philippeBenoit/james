/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/


package org.apache.james.mailrepository.jdbc;

import javax.sql.DataSource;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.impl.SimpleLog;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.apache.james.mailrepository.AbstractMailRepositoryTest;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.jdbc.JDBCMailRepository;
import org.apache.james.mailstore.mock.MockMailStore;
import org.apache.james.repository.file.FilePersistentStreamRepository;
import org.apache.james.resolver.api.mock.MockFileSystem;

public class JDBCMailRepositoryTest extends AbstractMailRepositoryTest {

    /**
     * @return
     * @throws ServiceException
     * @throws ConfigurationException
     * @throws Exception
     */
    protected MailRepository getMailRepository() throws Exception {
        MockFileSystem fs =  new MockFileSystem();
        DataSource datasource = getDataSource();
        JDBCMailRepository mr = new JDBCMailRepository();
        
        // only used for dbfile
        MockMailStore mockStore = new MockMailStore();
        FilePersistentStreamRepository file_Persistent_Stream_Repository = new FilePersistentStreamRepository();
        file_Persistent_Stream_Repository.setFileSystem(fs);
        file_Persistent_Stream_Repository.setLog(new SimpleLog("MockLog"));
        DefaultConfigurationBuilder defaultConfiguration2 = new DefaultConfigurationBuilder();
        defaultConfiguration2.addProperty("[@destinationURL]", "file://target/var/mr/testrepo");
        file_Persistent_Stream_Repository.configure(defaultConfiguration2);
        file_Persistent_Stream_Repository.init();
        mockStore.add("STREAM.mr", file_Persistent_Stream_Repository);
        
        DefaultConfigurationBuilder defaultConfiguration = new DefaultConfigurationBuilder();
        defaultConfiguration.addProperty("[@destinationURL]","db://maildb/mr/testrepo");
        defaultConfiguration.addProperty("sqlFile","file://conf/sqlResources.xml");
        defaultConfiguration.addProperty("[@type]","MAIL");
        mr.setFileSystem(fs);
        mr.setStore(mockStore);
        mr.setDatasource(datasource);
        mr.setLog(new SimpleLog("MockLog"));
        mr.configure(defaultConfiguration);
        mr.init();
        return mr;
    }
    
    protected String getType() {
        return "db";
    }
    
    
    private BasicDataSource getDataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(EmbeddedDriver.class.getName());
        ds.setUrl("jdbc:derby:target/testdb;create=true");
        ds.setUsername("james");
        ds.setPassword("james");
        return ds;
    }

}

