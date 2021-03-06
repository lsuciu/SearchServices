/*
 * #%L
 * Alfresco Search Services
 * %%
 * Copyright (C) 2005 - 2020 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

package org.alfresco.solr.query;

import org.alfresco.solr.AbstractAlfrescoDistributedIT;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.util.NamedList;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Joel
 */
@SolrTestCaseJ4.SuppressSSL
public class DistributedAlfrescoSolrSpellcheckerIT extends AbstractAlfrescoDistributedIT
{
    @BeforeClass
    public static void initData() throws Throwable
    {
        initSolrServers(2, DistributedAlfrescoSolrSpellcheckerIT.getSimpleClassName(),null);
        initSolrIndex();
    }

    @AfterClass
    public static void destroyData()
    {
        dismissSolrServers();
    }
    
    @Test
    public void testSpellcheckerOutputFormat() throws Exception
    {
        putHandleDefaults();
        QueryResponse response = query(getDefaultTestClient(), true,
                "{\"query\":\"(YYYYY BBBBB AND (id:(1 2 3 4 5 6)))\",\"locales\":[\"en\"], \"templates\": [{\"name\":\"t1\", \"template\":\"%cm:content\"}], \"authorities\": [\"joel\"], \"tenants\": []}",
                params("spellcheck.q", "YYYYY BBBBB", "qt", "/afts", "shards.qt", "/afts", "start", "0", "rows", "100", "spellcheck", "true"));

        NamedList res = response.getResponse();
        NamedList spellcheck = (NamedList)res.get("spellcheck");
        NamedList suggestions = (NamedList)spellcheck.get("suggestions"); // Solr 4 format
        NamedList collation = (NamedList)suggestions.getVal(2); // The third suggestion should be collation in the Solr format.
        String collationQuery = (String)collation.get("collationQuery");
        String collationQueryString = (String)collation.get("collationQueryString");
        int hits = (int)collation.get("hits");
        assertTrue(hits == 3);
        assertTrue(collationQuery.equals("(yyyyyyy bbbbbbb AND (id:(1 2 3 4 5 6)))"));
        assertTrue(collationQueryString.equals("yyyyyyy bbbbbbb"));
    }

    private static void initSolrIndex() throws Exception
    {
        index(getDefaultTestClient(), true, "id", "1",  "suggest", "YYYYYYY BBBBBBB", "_version_","0", "content@s___t@{http://www.alfresco.org/model/content/1.0}content", "YYYYYYY BBBBBBB");
        index(getDefaultTestClient(), true, "id", "2",  "suggest", "AAAAAAAA", "_version_","0", "content@s___t@{http://www.alfresco.org/model/content/1.0}content", "AAAAAAAA");
        index(getDefaultTestClient(), true, "id", "3",  "suggest", "BBBBBBB", "_version_","0", "content@s___t@{http://www.alfresco.org/model/content/1.0}content", "BBBBBBB");
        index(getDefaultTestClient(), true, "id", "4",  "suggest", "CCCC", "_version_","0", "content@s___t@{http://www.alfresco.org/model/content/1.0}content", "CCCC");
        index(getDefaultTestClient(), true, "id", "5", "suggest", "YYYYYYY", "_version_", "0", "content@s___t@{http://www.alfresco.org/model/content/1.0}content", "YYYYYYY BBBBBBB");
        index(getDefaultTestClient(), true, "id", "6", "suggest", "EEEE", "_version_", "0", "content@s___t@{http://www.alfresco.org/model/content/1.0}content", "EEEE");
        commit(getDefaultTestClient(), true);
    }
}

