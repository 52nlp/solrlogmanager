/*
 * Licensed to LucidWorks under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. LucidWorks licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * LWSolrLogCollectionManager
 * @author  MH
 * @version 1.0 
 * @since 2013 
*/

public class LWSolrLogCollectionManager extends CollectionManager{
	private String fieldsPath = "";
	private String addNewDocPath = "";
	
	private enum SOLR_RSP {
		NOCONTENT(204), NOTFOUND(404), DOCUMENTADDED(200), FIELDCREATED(200), COLLECTIONCREATED(201);

		private final int id;
		SOLR_RSP(int id) { this.id = id; }
		public int getValue() { return id; }
	}

	public void init(String host, int pt, String collection) {
		String port = String.valueOf(pt);
		
		// Note the Solr documentation examples do not include the collection.  But if
		// if collection is left out then new records are not added to the index because 
		// the fields are not found even though they do somehow appear in the managed-schema file.
		fieldsPath = "http://" + host + ":" + port + "/solr/" + collection + "/schema/fields";
		addNewDocPath = "http://" + host + ":" + port + "/solr/" + collection + "/update/?commit=true";
	}

	/**
	 * Add new field to Solr schema if field does not already exist.
	 * @param key the field name
	 * @param val the field creation string.
	 * @throws Exception
	 */
	private void createSchemaField(String key, String val) throws Exception {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(fieldsPath + "/" + key);
			
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			// Create required field if it does not already exist
			int respCode = conn.getResponseCode();
			if (respCode == SOLR_RSP.NOTFOUND.getValue()) {
				//LOG.info("Attempting to create schema field - " + url.getPath() + ",  CreateString = " + val);
				
				conn.disconnect();
				url = new URL(fieldsPath);
				conn = (HttpURLConnection) url.openConnection();
				conn.setDoOutput(true);
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/json");
				OutputStream os = conn.getOutputStream();
				
				os.write(val.getBytes());
				os.flush();
				respCode = conn.getResponseCode();
				if (respCode != SOLR_RSP.FIELDCREATED.getValue()) {
					throw new RuntimeException("Failed to create field <" + key + "> - \n\n" + conn.getResponseMessage());
				}
			}
		} catch (Exception e) {
			throw e;
		} finally {
			if (null != conn)
				conn.disconnect();
		}
	}

	/**
	 * Perform rest calls that add the new document. <p>
	 * 
	 */
	private void addDoc(String doc) throws Exception {
		HttpURLConnection conn = null;

		try {
			URL url = new URL(addNewDocPath);
			conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "text/xml");
			OutputStream os = conn.getOutputStream();
					
			os.write(doc.getBytes());
			os.flush();
			int respCode = conn.getResponseCode();
			if (respCode != SOLR_RSP.DOCUMENTADDED.getValue()) {
				throw new RuntimeException("Failed to add docs - " + doc + " - \n\n" + conn.getResponseMessage());
			}
		} catch (Exception e) {
			throw e;
		} finally {
			if (null != conn)
				conn.disconnect();
		}
	}

	/**
	 * Adds single document to the index. <p>
	 * The 'id' field is mandatory.  If it does not appear in the eventlist then a GUIID will be created and used 
	 * as the document id. <p>
	 * 
	 * Fields added to the schema with the following settings: <p>
	 * <b>message</b> - type=text_en, stored=true <br>
	 * <b>tags</b> - type=text_en, stored=true, indexed=true, multivalued=true <br>
	 * <b>allOtherFields></b> - type=text_en, stored=true, indexed=true
	 * 
	 * @param event hashmap<String,String> of fieldname=value pairs.
	 * @param  fieldCreationParams hashmap
	 * @throws Exception
	 */
	public void addSolrDocument(HashMap <String, String> event, HashMap <String, String> fieldCreationParams) throws Exception {
		String id = "";
		StringBuffer s = new StringBuffer();
		s.append("<add><doc>");

		// Prepare event fields
		for (Map.Entry<String, String> entry : event.entrySet()) {
			String key = entry.getKey().toLowerCase().trim();
			
			String param = (fieldCreationParams.get(key) == null) ? 
					"[{\"type\":\"text_en\",\"name\":\"" + key + "\",\"stored\":true,\"indexed\":true}]" : fieldCreationParams.get(key);
			
			createSchemaField(key, param);
			
			if ("id".equals(key)) {
				id = checkURLEncode((String)entry.getValue());
			} else if ("tags".equals(key)) {
				String[] tags = ((String)entry.getValue()).split(",");
				for (int i = 0; i < tags.length; i++)
					s.append("<field name=\"tags\">" + checkURLEncode(tags[i]) + "</field>");
			} else {
				s.append("<field name=\"" + checkURLEncode(key) + "\">" + checkURLEncode((String)entry.getValue()) + "</field>");
			}
		}
		id = id.isEmpty() ? genId() : id;
		s.append("<field name=\"id\">" + id + "</field></doc></add>");
		addDoc(s.toString());
	}
}

