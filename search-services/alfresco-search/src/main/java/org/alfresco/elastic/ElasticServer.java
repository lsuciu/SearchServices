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

package org.alfresco.elastic;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.alfresco.solr.config.ConfigUtil;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.solr.common.SolrInputDocument;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an ElasticServer to be used for indexing or searching operations.
 * 
 * The implementation is based in the Elastic Rest Client.
 * 
 * @author aborroy
 *
 */
public class ElasticServer 
{
	
    protected final static Logger LOGGER = LoggerFactory.getLogger(ElasticServer.class);
	
	public static final String ELASTIC_PROTOCOL = "elastic.protocol";
	public static final String ELASTIC_HOST = "elastic.host";
	public static final String ELASTIC_PORT = "elastic.port";
	
	String protocol;
	String host;
	int port;
	
	/**
	 * Elastic Server has been configured to be used when "enabled" is true.
	 */
	public static boolean IS_ENABLED = false; 
	
	public ElasticServer()
	{
		this.protocol = ConfigUtil.locateProperty(ELASTIC_PROTOCOL, null);
		this.host = ConfigUtil.locateProperty(ELASTIC_HOST, null);
		String portString = ConfigUtil.locateProperty(ELASTIC_PORT, null);
		if (portString != null)
		{
			this.port = Integer.valueOf(portString);
		}
		IS_ENABLED = Objects.nonNull(protocol) && Objects.nonNull(host) && Objects.nonNull(port);
		
	}
	
	/**
	 * Check if an Index exists in Elastic Server by name
	 * @param indexName Name of the index
	 * @return true when the index exists, false otherwise
	 */
	public boolean existsIndex(String indexName)
	{
		boolean exists = false;
		
		RestClient restClient = RestClient.builder(new HttpHost(host, port, protocol)).build();
		Request request = new Request("GET", "/" + indexName);
		try 
		{
			Response response = restClient.performRequest(request);
			exists = response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
			restClient.close();
		}
		catch (ResponseException re)
		{
			if (re.getResponse().getStatusLine().getStatusCode() != HttpStatus.SC_NOT_FOUND)
			{
				LOGGER.warn("Found non expected HTTP Status " + re.getResponse().getStatusLine().getStatusCode()
						+ " while checking if index " + indexName + " exists");
			}
		}
		catch (IOException ioe) 
		{
			LOGGER.error("Elastic Server " + protocol + "//" + host + ":" + port + " returned an unexpected error", ioe);
		}
		return exists;
	}
	
	/**
	 * Create a new Elastic Index with name and mappings (JSON Schema)
	 * @param indexName Name of the index to be created
	 * @param jsonMapping JSON mapping properties and types for Lucene Schema
	 * @return true when the index is created, false otherwise
	 */
	public boolean createIndex(String indexName, String jsonMapping)
	{
		boolean success = false;

		RestClient restClient = RestClient.builder(new HttpHost(host, port, protocol)).build();
		Request request = new Request("PUT", "/" + indexName);
		request.setJsonEntity(jsonMapping);
		try 
		{
			Response response = restClient.performRequest(request);
			success = response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
			restClient.close();
		} 
		catch (IOException ioe) 
		{
			LOGGER.error("Elastic Server " + protocol + "//" + host + ":" + port + " invoked with error: ", ioe);
		}
		return success;
	}
	
	/**
	 * Format Date values to Elastic String date format
	 * @param name Name of the field
	 * @param date Value of the field
	 * @return Elastic String date format
	 */
	private static SimpleDateFormat DATE_TIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	private static SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd");
	private String getDateString(String name, Date date)
	{
		if (name.startsWith("datetime"))
		{
			return DATE_TIME_FORMATTER.format(date);
		}
		else
		{
			return DATE_FORMATTER.format(date);
		}
	}
	
	/**
	 * Build JSON String from SolrDocument object
	 * @param solrDocument The SolrInputDocument object
	 * @return String JSON Compliant
	 */
	private String getJsonMapping(SolrInputDocument solrDocument)
	{
		JSONObject jsonMapping = new JSONObject();
		for (String fieldName : solrDocument.getFieldNames())
		{
            Object fieldValue = solrDocument.getField(fieldName).getValue();
            if (fieldValue instanceof ArrayList<?>)
            {
            	JSONArray jsonArray = new JSONArray();
            	ArrayList<?> fieldValues = (ArrayList<?>) fieldValue;
            	if (!fieldValues.isEmpty())
            	{
            		for (Object fv : fieldValues)
            		{
            			if (fv instanceof Date)
            			{
            				jsonArray.put(getDateString(fieldName, (Date) fv));
            			}
            			else
            			{
            			    jsonArray.put(fv);
            			}
            		}
            	}
            	jsonMapping.put(fieldName, jsonArray);
            }
            else if (fieldValue instanceof Date)
            {
            	jsonMapping.put(fieldName, getDateString(fieldName, (Date) fieldValue));
            }
            else
            {
		        jsonMapping.put(fieldName, fieldValue);
            }
		}
		return jsonMapping.toString();
		
	}
	
	/**
	 * Index SolrDocument properties in Elastic Index
	 * @param indexName Name of the index to be updated
	 * @param id Id of the document to be modified, this parameter should be NULL for new documents
	 * @param solrDocument SoldDocument containing properties to be indexed
	 */
	private void indexOrUpdateDocument(String indexName, Long id, SolrInputDocument solrDocument)
	{
		String jsonMapping = getJsonMapping(solrDocument);
		
		RestClient restClient = RestClient.builder(new HttpHost(host, port, protocol)).build();
		Request request = (id == null ? 
				new Request("POST", "/" + indexName + "/_doc/")
				: new Request("PUT", "/" + indexName + "/_doc/" + id));
		request.setJsonEntity(jsonMapping);
		
		try {
			restClient.performRequest(request);
			restClient.close();
		}
		catch (IOException ioe)
		{
			LOGGER.error("Elastic Server " + protocol + "//" + host + ":" + port
					+ " returned an unexpected error indexing node " + id + " when parsing following properties: "
					+ jsonMapping, ioe);
		}
	}
	
	/**
	 * Index SolrDocument properties in Elastic Index
	 * @param indexName Name of the index to be updated
	 * @param solrDocument SoldDocument containing properties to be indexed
	 */
	public void indexDocument(String indexName, SolrInputDocument solrDocument)
	{
		indexOrUpdateDocument(indexName, null, solrDocument);
	}
	
	/**
	 * Index SolrDocument properties in Elastic Index
	 * @param indexName Name of the index to be updated
	 * @param id Id of the document to be modified
	 * @param solrDocument SoldDocument containing properties to be indexed
	 */
	public void updateDocument(String indexName, Long id, SolrInputDocument solrDocument)
	{
		indexOrUpdateDocument(indexName, id, solrDocument);
	}
	
	/**
	 * Build an Elastic JSON Search Query to get a document by DBID
	 * @param dbId Node id from database
	 * @return JSON Query to find a document by DBID
	 */
	private String buildJsonQueryById(Long dbId)
	{
		
		JSONObject filter = new JSONObject();
		filter.put("long@s_@{http://www.alfresco.org/model/system/1.0}node-dbid", String.valueOf(dbId));
		
		JSONObject query = new JSONObject();
		query.put("match", filter);
		
		JSONObject jsonQuery = new JSONObject();
		jsonQuery.put("_source", "_id");
		jsonQuery.put("query", query);
		
		return jsonQuery.toString();
	}
	
	/**
	 * Get Elastic Document Id (_id) from Elastic Search Response
	 * @param response Elastic Search Response
	 * @return Elastic Document Id
	 * @throws UnsupportedOperationException
	 * @throws IOException
	 */
	private Long getIdFromResponse(Response response) throws UnsupportedOperationException, IOException 
	{
		
		String content = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
		
		JSONObject json = new JSONObject(content);
		JSONObject hits = json.getJSONObject("hits");
		JSONArray hitsArray = hits.getJSONArray("hits");
		
		if (hitsArray.length() > 1)
		{
			throw new RuntimeException("Found " + hitsArray.length() + " elements!");
		}
		else if (hitsArray.length() == 0)
		{
			LOGGER.warn("The response '" + content + "' does not include a hit!");
			return null;
		}
		else
		{
			JSONObject h = hitsArray.getJSONObject(0);
			return h.getLong("_id");
		}
	}
	
	/**
	 * Get Elastic Document Id (_id) for an Index by Alfresco DBID
	 * @param indexName Name of the index
	 * @param dbId Alfresco DBID 
	 * @return Elastic Document Id (_id)
	 */
	public Long getDocumentId(String indexName, Long dbId)
	{
		
		Long documentId = null;
		
		String jsonQuery = buildJsonQueryById(dbId);
		
		RestClient restClient = RestClient.builder(new HttpHost(host, port, protocol)).build();
		Request request = new Request("GET", "/" + indexName + "/_search");
		request.setJsonEntity(jsonQuery);
		
		try {
			Response response = restClient.performRequest(request);
			documentId = getIdFromResponse(response);
			restClient.close();
		}
		catch (IOException ioe)
		{
			LOGGER.error("Elastic Server " + protocol + "//" + host + ":" + port
					+ " returned an unexpected error while performing following search: "
					+ jsonQuery, ioe);
		}
		
		return documentId;
		
	}
	
	/**
	 * Remove a list of document Ids from Elastic Index
	 * @param indexName Name of the index
	 * @param idsLists List of Ids to be removed
	 */
	@SuppressWarnings({ "unchecked" })
	public void deleteDocument(String indexName, List<Long>... idsLists)
	{
		RestClient restClient = RestClient.builder(new HttpHost(host, port, protocol)).build();
        for (Collection<Long> ids : idsLists)
        {
            for (Long id : ids)
            {
            	
            	Long documentId = getDocumentId(indexName, id);
            	
        		try
        		{
	            	Request request = new Request("DELETE", "/" + indexName + "/_doc/" + documentId);
	        		restClient.performRequest(request);
	        		restClient.close();
        		}
        		catch (ResponseException re)
        		{
        			if (re.getResponse().getStatusLine().getStatusCode() != HttpStatus.SC_NOT_FOUND)
        			{
        				LOGGER.warn("Found non expected HTTP Status " + re.getResponse().getStatusLine().getStatusCode()
        						+ " while removing document " + id + " from index " + indexName);
        			}
        		}
        		catch (IOException ioe)
        		{
        			LOGGER.error("Elastic Server " + protocol + "//" + host + ":" + port + " returned an unexpected error", ioe);
        		}
            }
        }
	}
	
}