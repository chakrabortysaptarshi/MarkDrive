package com.saptarshi.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.io.SearchHandle;
import com.marklogic.client.query.MatchDocumentSummary;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.StringQueryDefinition;

@Path("/search")
public class SearchService {

	@GET
	@Path("/{username}")
	@Produces("text/plain")
	public Response search(@QueryParam("query") String query,
			@QueryParam("regexquery") String regexQuery,
			@QueryParam("category") String category,
			@PathParam("username") String username) {
		
		// create the client
		String uname = Configuration.USERNAME;
		String password = Configuration.PASSWORD;
		if(username.equals("saptarshi") || username.equals("avirup")) {
			uname = "saptarshi"; 
			password = "ticmark";
		} else if (username.equals("debarshi")) {
			uname = "debarshi";
			password = "ticmark";
		} else if (username.equals("rasha")) {
			uname = "rasha";
			password = "ticmark";
		}
		
		// create the client;
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				uname, password, Authentication.DIGEST);
				// create a manager for searching
		QueryManager queryMgr = client.newQueryManager();
		
		queryMgr.setPageLength(1000000);

		// create a handle for the search results
		SearchHandle resultsHandle = new SearchHandle();
		
		StringQueryDefinition searchquery = queryMgr.newStringDefinition();
		
		if(query != null) {
			searchquery.setCriteria(query);
		} else if (regexQuery != null) {
			searchquery.setCriteria("*"+regexQuery+"*");
		} else {
			searchquery.setCollections(category);

		}
		queryMgr.search(searchquery, resultsHandle);
		// release the client
		client.release();
		if(resultsHandle.getTotalResults() == 0)
			return Response.status(Status.NOT_FOUND).build();
		return Response.status(Status.OK).entity(displayResults(resultsHandle)).build();
	}
	
	public static String displayResults(SearchHandle resultsHandle) {
		System.out.println("Matched "+resultsHandle.getTotalResults()+" documents\n");
		if(resultsHandle.getTotalResults() == 0)
			return null;
		
		// Get the list of matching documents in this page of results
		MatchDocumentSummary[] results = resultsHandle.getMatchResults();
		System.out.println("Listing "+results.length+" documents:\n");
		
		String docURI = "";
		
		// Iterate over the results
		for (MatchDocumentSummary result: results) {
			// get the list of match locations for this result
			if(docURI.equals(""))
				docURI = result.getUri();
			else
				docURI += ","+result.getUri();
		}
		
		return docURI;
	}
	
}
