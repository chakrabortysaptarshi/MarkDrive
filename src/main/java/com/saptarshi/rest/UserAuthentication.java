package com.saptarshi.rest;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.ResourceNotFoundException;
import com.marklogic.client.document.TextDocumentManager;
import com.marklogic.client.io.StringHandle;

@Path("/auth")
public class UserAuthentication {

	private static final String SIGNUPURI = "/statistics/authinfo.txt";
	
	@Path("/signup")
	@POST
	@Consumes("application/json")
	public Response signup(String signup) {
		
		System.out.println(signup);
		
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		TextDocumentManager docMgr = client.newTextDocumentManager();
		try {
			// create a handle to receive the document content
			StringHandle content = new StringHandle();	
			// read the document content
			docMgr.read(SIGNUPURI, content);
			List<Authenticationinfo> au =new ObjectMapper().readValue(signup, 
					new ObjectMapper().getTypeFactory().constructCollectionType
					(List.class, Authenticationinfo.class));
			
			List<Authenticationinfo> auFromDB =new ObjectMapper().readValue(content.get(), 
					new ObjectMapper().getTypeFactory().constructCollectionType
					(List.class, Authenticationinfo.class));
			auFromDB.add(au.get(0));
			String newContent = new ObjectMapper().writeValueAsString(auFromDB);
			StringHandle stringhandler = new StringHandle(newContent);
			docMgr.write(SIGNUPURI, stringhandler);
			return Response.status(Status.OK).build();
		} catch (JsonParseException e) {
			e.printStackTrace();
			return Response.status(Status.BAD_REQUEST).build();
		} catch (JsonMappingException e) {
			e.printStackTrace();
			return Response.status(Status.BAD_REQUEST).build();
		} catch (IOException e) {
			e.printStackTrace();
			return Response.status(Status.BAD_REQUEST).build();
		} catch(ResourceNotFoundException rnfe) {
			StringHandle stringhandler = new StringHandle(signup);
			docMgr.write(SIGNUPURI, stringhandler);
			return Response.status(Status.OK).build();
		} finally {
			client.release();
		}
	}
	
	@Path("/signin")
	@POST
	@Consumes("application/json")
	public Response signin(String signin) {
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		TextDocumentManager docMgr = client.newTextDocumentManager();
		// create a handle to receive the document content
		StringHandle content = new StringHandle();	
		// read the document content
		try {
			docMgr.read(SIGNUPURI, content);
			List<Authenticationinfo> auFromDB =new ObjectMapper().readValue(content.get(), 
					new ObjectMapper().getTypeFactory().constructCollectionType
					(List.class, Authenticationinfo.class));
			List<Authenticationinfo> signininfo =new ObjectMapper().readValue(signin, 
					new ObjectMapper().getTypeFactory().constructCollectionType
					(List.class, Authenticationinfo.class));
			if(signininfo.get(0).getName().equals("admin") && 
					signininfo.get(0).getPassword().equals("admin"))
				return Response.status(Status.OK).build();
				
			for(Authenticationinfo au:auFromDB) {
				if(au.getName().equals(signininfo.get(0).getName()) &&
						au.getPassword().equals(signininfo.get(0).getPassword()))
					return Response.status(Status.OK).build();
			}
			return Response.status(Status.BAD_REQUEST).build();		
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(Status.BAD_REQUEST).build();
		
		} finally {
			client.release();
		}
	}
	
	@Path("/getuser/{username}")
	@GET
	@Consumes("plain/text")
	public Response getUsers(@PathParam("username") String user) {
		List<Authenticationinfo> authFromDB = getUserInfo();
		if(authFromDB == null)
			return Response.status(Status.BAD_REQUEST).build();
		
		String loggedInUserGroup ="";
		for(Authenticationinfo au:authFromDB) {
			if(au.getName().equals(user)) {
				loggedInUserGroup = au.getGroup();
				break;
			}
		}
		String users ="";
		for(Authenticationinfo au:authFromDB) {
			if(!au.getGroup().equals(loggedInUserGroup)) {
				if(users.equals(""))
					users += au.getName();
				else
					users += ","+au.getName();
			}
		}
		System.out.println(users);
		return Response.status(Status.OK).entity(users).build();
		
	}
	
	public List<Authenticationinfo> getUserInfo() {
		final String SIGNUPURI = "/statistics/authinfo.txt";
		
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		TextDocumentManager docMgr = client.newTextDocumentManager();
		// create a handle to receive the document content
		StringHandle content = new StringHandle();
		
		docMgr.read(SIGNUPURI, content);
		try {
		List<Authenticationinfo> authFromDB =new ObjectMapper().readValue(content.get(), 
				new ObjectMapper().getTypeFactory().constructCollectionType
				(List.class, Authenticationinfo.class));
		return authFromDB;
		} catch(Exception e) {
			return null;
		} finally{
			client.release();
		}
	}
}
