/*
 * Copyright 2003-2016 MarkLogic Corporation
 */
package com.saptarshi.rest;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.io.StringHandle;
import com.marklogic.xcc.ContentSource;
import com.marklogic.xcc.ContentSourceFactory;
import com.marklogic.xcc.Request;
import com.marklogic.xcc.RequestOptions;
import com.marklogic.xcc.ResultItem;
import com.marklogic.xcc.ResultSequence;
import com.marklogic.xcc.Session;
import com.marklogic.xcc.exceptions.RequestException;
import com.marklogic.xcc.exceptions.XccConfigException;

public class PointInTime {
    private Session session;
    private RequestOptions options = null;
    
    
    public BigInteger getTimeStamp() {
    	try {
			URI serverUri = new URI("xcc://admin:admin@localhost:8003/Documents");
			ContentSource cs = ContentSourceFactory.newContentSource(serverUri);
			session = cs.newSession();
			return session.getCurrentServerPointInTime();
		} catch (Exception e) {
			return null;
		} finally {
			session.close();
		}
    }
    
    public PointInTime() {
    	
    }
    
    public void getLastVersion(String uri, BigInteger timestamp) {
    	String absoluteFilename = uri.substring(uri.lastIndexOf("/")+1, uri.length());
		String absoluteFilePath = System.getProperty("catalina.base")+"\\temp\\markdrive\\"+absoluteFilename;
		OutputStream outStream = null;
		try {
			outStream = new FileOutputStream(absoluteFilePath);
			URI serverUri = new URI("xcc://admin:admin@localhost:8003/Documents");
			ContentSource cs = ContentSourceFactory.newContentSource(serverUri);
			session = cs.newSession();
			options = new RequestOptions();
			options.setEffectivePointInTime(timestamp);
			session.setDefaultRequestOptions(options);
	        options.setCacheResult(false);
	        fetch(uri, outStream);
		} catch (Exception e) {

		} finally {
			session.close();
			try {
				outStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    }
    
    public PointInTime(URI serverUri) throws XccConfigException, RequestException {
        ContentSource cs = ContentSourceFactory.newContentSource(serverUri);

        session = cs.newSession();
        options = new RequestOptions();
        
        //Session session = getSession();
		BigInteger timestamp;
		timestamp = session.getCurrentServerPointInTime();
		//System.out.println(timestamp+"\t"+System.currentTimeMillis());
		Long l = 14791947604790000L;
		//timestamp = BigInteger.valueOf(l);
		System.out.println(timestamp);
		options.setEffectivePointInTime (timestamp);
		session.setDefaultRequestOptions (options);
        options.setCacheResult(false); // stream by default
    }

    private void fetch(String docUri, OutputStream outStream) throws RequestException, IOException {
        Request request = session.newAdhocQuery("doc (\"" + docUri + "\")", options);
        ResultSequence rs = session.submitRequest(request);
        ResultItem item = rs.next();

        if (item == null) {
            throw new IllegalArgumentException("No document found with URI '" + docUri + "'");
        }

        item.writeTo(outStream);
        session.close();
    }

    public void setRequestOptions(RequestOptions options) {
        this.options = options;
    }

    public static void main(String[] args) throws Exception {
        URI serverUri = new URI("xcc://admin:admin@localhost:8003/Documents");
        String docUri = "C:\\Users\\ssapt\\Desktop\\Referenxcc.txt";
        OutputStream outStream = new FileOutputStream("C:\\Users\\ssapt\\Desktop\\Referenxcc1.txt");
        

        PointInTime fetcher = new PointInTime(serverUri);
        fetcher.fetch(docUri, outStream);
        if (outStream != System.out) {
            outStream.close();
        }
    }
}
