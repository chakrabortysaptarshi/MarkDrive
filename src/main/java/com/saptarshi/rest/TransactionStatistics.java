package com.saptarshi.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.namespace.QName;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.admin.QueryOptionsManager;
import com.marklogic.client.admin.config.QueryOptions;
import com.marklogic.client.admin.config.QueryOptionsBuilder;
import com.marklogic.client.io.QueryOptionsHandle;
import com.marklogic.client.io.ValuesHandle;
import com.marklogic.client.query.CountedDistinctValue;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.ValuesDefinition;
import com.marklogic.client.query.ValuesDefinition.Frequency;

@Path("/stat")
public class TransactionStatistics {

	@GET
	@Path("/download")
	@Produces("plain/text")
	public Response getDownloadStat() {
		
		DocumentHistoryLogger dhl = new DocumentHistoryLogger();
		
		String downloadedFiles = dhl.getMostDownloads();
		
		if(downloadedFiles != null) {
			return Response.status(Status.OK).entity(downloadedFiles).build();
		} 
		return Response.status(Status.BAD_REQUEST).build();
	}
	
	@GET
	@Path("/upload")
	@Produces("plain/text") 
	public Response getUploadStat() {
		DocumentHistoryLogger dhl = new DocumentHistoryLogger();
		String uploadedFiles = dhl.getLatestUploads();
		
		if(uploadedFiles != null) 
			return Response.status(Status.OK).entity(uploadedFiles).build();
		return Response.status(Status.BAD_REQUEST).build();
	}
	
	@SuppressWarnings("deprecation")
	@GET
	@Path("/facets")
	@Produces("plain/text") 
	public Response getFacets() {
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				"admin", "admin", Authentication.DIGEST);
			// get an options manager
			QueryOptionsManager optionsMgr = client.newServerConfigManager().newQueryOptionsManager();
			// create a builder for constructing query options
			QueryOptionsBuilder qob = new QueryOptionsBuilder();
			// expose the "SPEAKER" element range index as "speaker" values
			QueryOptionsHandle options = new QueryOptionsHandle().withValues(
			    qob.values("Position",
			            qob.range(
			                qob.elementRangeIndex(new QName("Position"),
			                    qob.stringRangeType(QueryOptions.DEFAULT_COLLATION))),
			            "frequency-order"));
			// write the query options to the database
			optionsMgr.writeOptions("testfacets", options);		
			/**** VALUES RETRIEVAL ****/
			// create a manager for searching
			QueryManager queryMgr = client.newQueryManager();
			// create a values definition
			ValuesDefinition valuesDef = queryMgr.newValuesDefinition("Position", "testfacets");
			// count all element (item) appearances, rather than just 1 per document
			valuesDef.setFrequency(Frequency.ITEM);
			// retrieve the values
			ValuesHandle valuesHandle = queryMgr.values(valuesDef, new ValuesHandle());

			// print out the values and their frequencies
			String output = "";
			for (CountedDistinctValue value : valuesHandle.getValues()) {
			    output += value.get("xs:string",String.class) +","+value.getCount()+";";
				//System.out.println(
			      //  value.get("xs:string",String.class) + ": " + value.getCount());
			}
			output = output.substring(0, output.length()-1);
			System.out.println(output);
			// release the client
			client.release();
			
			return Response.ok(output).build();
	}
	
	public static void main(String args[]) {
		TransactionStatistics ts = new TransactionStatistics();
		ts.getFacets();
	}
}
