package com.saptarshi.rest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.ResourceNotFoundException;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.document.TextDocumentManager;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.io.StringHandle;

public class DocumentHistoryLogger {
	
	private static String uploadresourceUri = "/statistics/uploadstat.txt";
	private static String downloadresourceUri = "/statistics/downloadstat.txt";

	public DocumentHistoryLogger(String docUri, String option) {
		if(option.equals("upload")) {
			new Thread(new updateUploadStat(docUri)).start();
		} else {
			new Thread(new updateDownloadStat(docUri)).start();
		}
	}
	
	public DocumentHistoryLogger() {
		
	}
	
	private void updateLatestUpload(String uri) {
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		TextDocumentManager docMgr = client.newTextDocumentManager();
		StringHandle content = new StringHandle();
		String data = null, tempData = null;
		boolean isUnique = true;
		try {
			docMgr.read(uploadresourceUri, content);
			data = content.get();
			tempData = data;
			if(data.equals(uri)) {
				client.release();
				return;
			}
			if(data.contains(",")) {
				String splitData[] = data.split(",");
				for(int i=0; i<splitData.length; i++) {
					if(uri.equals(splitData[i])) {
						int j=i-1;
						String temp = splitData[i];
						while(j >= 0) {
							splitData[j+1] = splitData[j];
							j--;
						}
						splitData[0] = temp;
						data = Arrays.toString(splitData);
						data = data.replace("[", "");
						data = data.replace("]", "");
						data= data.replaceAll(" ", "");
						isUnique = false;
						break;
					}
				}
			}
			if(isUnique) {
				if(!data.contains(",") || (tempData.length() - tempData.replace(",", "").length()) < 9) {
					data = uri +"," +data;
				} else {
					data = uri +","+ data.substring(0, data.lastIndexOf(","));
				}
			}
		} catch(ResourceNotFoundException e) {
			data = uri;
		}
		StringHandle writeContent = new StringHandle(data);	
		docMgr.write(uploadresourceUri, writeContent);
		client.release();
	}

	public String getLatestUploads() {
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		TextDocumentManager docMgr = client.newTextDocumentManager();
		StringHandle content = new StringHandle();
		try {
			docMgr.read(uploadresourceUri, content);
			return content.get();
		} catch(ResourceNotFoundException e) {
			return null;
		} finally {
			client.release();
		}
	}
	
	private void updateDownloadstat(String uri) {
		if(uri.equals(downloadresourceUri) || uri.equals(uploadresourceUri))
			return;
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		JSONDocumentManager docMgr = client.newJSONDocumentManager();
		StringHandle content = new StringHandle();
		String data = null, contentTobeinserted = null ;
		try {
			docMgr.read(downloadresourceUri, content);
			data = content.get();
			// get the json content and convert it into a list of objects
			List<DownloadStat> downloadstatList;
			try {
				downloadstatList = new ObjectMapper().readValue(data, 
						new ObjectMapper().getTypeFactory().constructCollectionType
						(List.class, DownloadStat.class));				
				// create a map of to sort 
				Map<String, Integer> map = createmapForDownloadList(downloadstatList);
				if(map.containsKey(uri)) {
					map.put(uri, map.get(uri)+1);
				} else {
					map.put(uri, 1);
				}
				List<Entry<String, Integer>> finalList = valueSortedMap(map);
				contentTobeinserted = DocumentHistoryLogger.JsonBuilderForDownload(finalList);

			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (ResourceNotFoundException e) {
			 StringBuilder sb = new StringBuilder();
			 sb.append("[{\"uri\":\"");
			 sb.append(uri);
			 sb.append("\",\"count\":");
			 sb.append(1);
			 sb.append("}]");
		     
			 contentTobeinserted = sb.toString();
		}
		InputStream is = new ByteArrayInputStream( contentTobeinserted.
				getBytes(Charset.defaultCharset()) );
		InputStreamHandle inputHandle =  new InputStreamHandle(is);
		docMgr.write(downloadresourceUri, inputHandle);
		client.release();
	}
	
	public String getMostDownloads() {
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		JSONDocumentManager docMgr = client.newJSONDocumentManager();
		StringHandle content = new StringHandle();
		try {
			docMgr.read(downloadresourceUri, content);
		    String data = content.get();
		    // get the json content and convert it into a list of objects
		    List<DownloadStat> someClassList;
		    someClassList = new ObjectMapper().readValue(data, 
					new ObjectMapper().getTypeFactory().constructCollectionType
					(List.class, DownloadStat.class));
			StringBuilder fileDownloadedList = new StringBuilder();
			for(int i=0; i<someClassList.size(); i++) {
				if(i!=0) {
					fileDownloadedList.append(",");
				}
				fileDownloadedList.append(someClassList.get(i).geturi());
				if(i==9)
					break;
			}
			return fileDownloadedList.toString();
		} catch(Exception e) {
			return null;
		} finally {
			client.release();
		}
	}
		
	private Map<String, Integer> createmapForDownloadList (List<DownloadStat> li) {   
		Map<String, Integer> downloadStatMap = new HashMap<String, Integer>();
	    for(int i=0; i< li.size(); i++) {
	    	downloadStatMap.put(li.get(i).geturi(), li.get(i).getcount());
	    }
	    return downloadStatMap;
	}
	
    private <String, Integer extends Comparable<? super Integer>> 
    List<Entry<String, Integer>> valueSortedMap(Map<String, Integer> map) {
    	List<Entry<String, Integer>> sortedEntries = new ArrayList<Entry<String, Integer>>(map.entrySet());
    	Collections.sort(sortedEntries, 
            new Comparator<Entry<String, Integer>>() {
                public int compare(Entry<String, Integer> e1, Entry<String, Integer> e2) {
                    return e2.getValue().compareTo(e1.getValue());
                }
            }
    );
    return sortedEntries;
}
 
    static String JsonBuilderForDownload(List<Entry<String, Integer>> finalList) {
	 StringBuilder sb = new StringBuilder();
		sb.append("[");
     Iterator<Entry<String, Integer>> iter = finalList.iterator();
     while (iter.hasNext()) {
         Entry<String, Integer> entry = iter.next();
         sb.append("{\"uri\":\"");
         sb.append(entry.getKey());
         sb.append("\",\"count\":");
         sb.append(entry.getValue());
         sb.append("}");
         if (iter.hasNext()) {
             sb.append(',');
         }
     }
     sb.append("]");
     return sb.toString();
 }

    class updateUploadStat implements Runnable {
    	String uri;
    	public updateUploadStat(String s) {
			this.uri = s;
		}
		@Override
		public void run() {
			updateLatestUpload(uri);
		}
    }
    
    class updateDownloadStat implements Runnable {
    	String uri;
    	public updateDownloadStat(String s) {
			this.uri = s;
		}
		@Override
		public void run() {
			updateDownloadstat(uri);
		}
    }
}