package com.saptarshi.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.document.TextDocumentManager;
import com.marklogic.client.io.SearchHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.query.MatchDocumentSummary;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.StringQueryDefinition;

@Path("dashboard")
public class Dashboard {
	
	private List<String> getusernnames() throws JsonParseException, JsonMappingException, IOException {
		
		final String SIGNUPURI = "/statistics/authinfo.txt";
		
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		TextDocumentManager docMgr = client.newTextDocumentManager();
		// create a handle to receive the document content
		StringHandle content = new StringHandle();	
		
		docMgr.read(SIGNUPURI, content);
		
		List<Authenticationinfo> authFromDB =new ObjectMapper().readValue(content.get(), 
				new ObjectMapper().getTypeFactory().constructCollectionType
				(List.class, Authenticationinfo.class));
		
		List<String> names = new ArrayList<String>();
		
		for(Authenticationinfo au:authFromDB) {
			names.add(au.getName());
		}
		client.release();
		return names;
	}
	private static ConcurrentHashMap<String, HashMap<String, Integer>> mapByUserName = new ConcurrentHashMap<String, HashMap<String,Integer>>();
	private static ConcurrentHashMap<String, HashMap<String, Integer>> mapByFileType = new ConcurrentHashMap<String, HashMap<String,Integer>>();
	ExecutorService dataFetchers = null;
	ExecutorService dataProcessers = null;
	
	public ConcurrentHashMap<String, HashMap<String, Integer>> getMapByUsername() {
		return mapByUserName;
	}
	
	public ConcurrentHashMap<String, HashMap<String, Integer>> getMapByFileType() {
		return mapByFileType;
	}
	
	public void setMapByFiletype(ConcurrentHashMap<String, HashMap<String, Integer>> map) {
		mapByFileType = map;
	}
	
	@GET
	@Produces("application/json")
	public Response createDashboard() {
		List<String> username;
		
		try {
			username = getusernnames();
			dataFetchers = Executors.newFixedThreadPool(username.size());
			dataProcessers = Executors.newFixedThreadPool(username.size());
			
			for(String uname:username) {
				Future<String> data = dataFetchers.submit(new getFilesBasedOnUserName(uname));
				ProcessdatabyName pdn = new ProcessdatabyName(uname, data);
				dataProcessers.submit(pdn);
				while(!data.isDone()) {
					
				}
				pdn.execute.set(true);
			}
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			dataFetchers.shutdown();
			dataProcessers.shutdown();
		}
		prepareFileTypeMap();
		return Response.status(Status.OK).entity(getContentByFileType()+getContentByUserName()).build();
	}
	
	public void prepareFileTypeMap() {
		while( !dataFetchers.isTerminated() || !dataProcessers.isTerminated()) {
			try {
				Thread.sleep(100L);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println(getMapByUsername().size());
		ConcurrentHashMap<String, HashMap<String, Integer>> mapByFileExt = getMapByFileType();
		Iterator<Entry<String, HashMap<String, Integer>>> it = getMapByUsername().entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, HashMap<String, Integer>> entry = (Map.Entry<String, HashMap<String, Integer>>) it.next();
			Map<String, Integer> map = entry.getValue();
			Iterator<Entry<String, Integer>> it1 = map.entrySet().iterator();
			//System.out.println(entry.getKey()+"~~~");
			while(it1.hasNext()) {
				Map.Entry<String, Integer> e = (Map.Entry<String, Integer>) it1.next();
				//System.out.println(e.getKey()+":"+e.getValue()+"\t");			
				
				if(mapByFileExt.containsKey(e.getKey())) {
					HashMap<String, Integer> m = mapByFileExt.get(e.getKey());
					m.put(entry.getKey(), e.getValue());
				} else {
					HashMap<String, Integer> m = new HashMap<String, Integer>();
					m.put(entry.getKey(), e.getValue());
					mapByFileExt.put(e.getKey(), m);
				}
			}
		}
		setMapByFiletype(mapByFileExt);
		
	}
	
	public String getContentByFileType() {
		String fileByExt = "{\"parent\":[";
		String childText = "\"child\":{\"series\": [";
		Iterator<Entry<String, HashMap<String, Integer>>> itByExt = getMapByFileType().entrySet().iterator();
		while(itByExt.hasNext()) {
			Map.Entry<String, HashMap<String, Integer>> entry = (Map.Entry<String, HashMap<String, Integer>>) itByExt.next();
			Map<String, Integer> map = entry.getValue();
			Iterator<Entry<String, Integer>> it1 = map.entrySet().iterator();
			//System.out.println(entry.getKey()+"~~~");
			fileByExt += "{\"name\":\""+entry.getKey()+"\",";
			childText += "{\"name\":\""+entry.getKey()+"\",\"id\":\""+entry.getKey()+"\",\"data\": [";
			//String extPerUser = "\"users\":[";
			int sum =0;
			while(it1.hasNext()) {
				Map.Entry<String, Integer> e = (Map.Entry<String, Integer>) it1.next();
				//System.out.println(e.getKey()+":"+e.getValue()+"\t");
				//extPerUser += "{\"user\":\""+e.getKey()+"\",\"count\":"+e.getValue()+"}";
				childText += "[\""+e.getKey()+"\","+e.getValue()+"]";
				if(it1.hasNext())
					childText += ",";
				sum += e.getValue();	
			}
			fileByExt += "\"y\":"+sum+",\"drilldown\":\""+entry.getKey()+"\"}";
			childText += "]}";
			//extPerUser += "]";
			//fileByExt += extPerUser+"}";
			if(itByExt.hasNext()) {
				fileByExt += ",";
				childText += ",";
			}
		}
		fileByExt +="],";
		childText +="]}}";
		fileByExt += childText;
		fileByExt = "{\"ftype\":"+fileByExt+",";
		System.out.println(fileByExt);
		return fileByExt;
	}
	
	public String getContentByUserName() {
		String fileByExt = "{\"parent\":[";
		String childText = "\"child\":{\"series\": [";
		Iterator<Entry<String, HashMap<String, Integer>>> itByExt = getMapByUsername().entrySet().iterator();
		while(itByExt.hasNext()) {
			Map.Entry<String, HashMap<String, Integer>> entry = (Map.Entry<String, HashMap<String, Integer>>) itByExt.next();
			Map<String, Integer> map = entry.getValue();
			Iterator<Entry<String, Integer>> it1 = map.entrySet().iterator();
			//System.out.println(entry.getKey()+"~~~");
			fileByExt += "{\"name\":\""+entry.getKey()+"\",";
			childText += "{\"name\":\""+entry.getKey()+"\",\"id\":\""+entry.getKey()+"\",\"data\": [";
			//String extPerUser = "\"users\":[";
			int sum =0;
			while(it1.hasNext()) {
				Map.Entry<String, Integer> e = (Map.Entry<String, Integer>) it1.next();
				//System.out.println(e.getKey()+":"+e.getValue()+"\t");
				//extPerUser += "{\"user\":\""+e.getKey()+"\",\"count\":"+e.getValue()+"}";
				childText += "[\""+e.getKey()+"\","+e.getValue()+"]";
				if(it1.hasNext())
					childText += ",";
				sum += e.getValue();	
			}
			fileByExt += "\"y\":"+sum+",\"drilldown\":\""+entry.getKey()+"\"}";
			childText += "]}";
			//extPerUser += "]";
			//fileByExt += extPerUser+"}";
			if(itByExt.hasNext()) {
				fileByExt += ",";
				childText += ",";
			}
		}
		fileByExt +="],";
		childText +="]}}";
		fileByExt += childText;
		fileByExt = "\"uname\":"+fileByExt+"}";
		System.out.println(fileByExt);
		return fileByExt;
	}
	
	
	
	/*public static void main(String[] args)  {
		Dashboard db = new Dashboard();
		db.createDashboard();
		String s = db.getContentByFileType()+db.getContentByUserName();
		System.out.println(s);
	}*/
}

class ProcessdatabyName implements Runnable {

	String username; 
	Future<String> data;
	AtomicBoolean execute = new AtomicBoolean(false);
	
	public ProcessdatabyName(String uname, Future<String> content) {
		username = uname;
		data = content;
	}
	
	@Override
	public void run() {
		while(!execute.get()) {
			
		} 
		HashMap<String, Integer> map = new HashMap<String, Integer>();
		String content[];
		try {
			content = data.get().split(",");
			for(String s:content) {
				String file_extension = s.substring(s.lastIndexOf(".")+1, s.length());
				if(map.containsKey(file_extension))
					map.put(file_extension, map.get(file_extension)+1);
				else {
					map.put(file_extension, 1);	
				}
			}
			new Dashboard().getMapByUsername().put(username, map);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		
	}
}


class getFilesBasedOnUserName implements Callable<String> {

	volatile String username;
	
	public getFilesBasedOnUserName(String s) {
		username = s;
	}
	
	
	@Override
	public String call() throws Exception {
		return getFiles(username);
	}
	
	private String getFiles(String user) {
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
				// create a manager for searching
		QueryManager queryMgr = client.newQueryManager();
		
		queryMgr.setPageLength(1000000);

		// create a handle for the search results
		SearchHandle resultsHandle = new SearchHandle();
		
		StringQueryDefinition searchquery = queryMgr.newStringDefinition();
		
		searchquery.setCollections(user);
		
		queryMgr.search(searchquery, resultsHandle);
		
		if(resultsHandle.getTotalResults() == 0)
			return null;
		
		
		MatchDocumentSummary[] results = resultsHandle.getMatchResults();
		String docURI = "";
		for (MatchDocumentSummary result: results) {
			if(docURI.equals(""))
				docURI = result.getUri();
			else
				docURI += ","+result.getUri();
		}
		client.release();
		return docURI;
	}
}

