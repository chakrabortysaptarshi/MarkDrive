package com.saptarshi.rest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.xml.namespace.QName;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.document.BinaryDocumentManager;
import com.marklogic.client.document.GenericDocumentManager;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.document.TextDocumentManager;
import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.io.BytesHandle;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.DocumentMetadataHandle.Capability;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.io.StringHandle;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

@Path("/file")
public class FileService {
	
	@POST
	@Path("/upload/{username}")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response uploadFile(
			@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail,
			@QueryParam("category") String category,
			@PathParam("username") String uploadername) {

		try {
			BigInteger timestamp = new PointInTime().getTimeStamp();
			uploadFileToDB(uploadername, fileDetail.getFileName(), uploadedInputStream, category, timestamp);
			System.out.println(fileDetail.getFileName()+"\t"+category);
			new DocumentHistoryLogger(uploadername+"/"+fileDetail.getFileName(), "upload");
			//System.out.println("Tomcat Directory" +System.getProperty("catalina.base"));
			return Response.status(200).entity("Success").build();
		} catch(Exception e) {
			return Response.status(Status.BAD_REQUEST).entity("Failure").build();
		}
	}
	
	@GET
	@Path("/test")
	@Produces("application/json")
	public Response testing() {
		return Response.status(200).entity("Success").build();
	}
	
	@GET
	@Path("/download/{username}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response saveFile(@QueryParam("file") String fileName, 
			@PathParam("username") String username) {
		try {
			 downloadHelper(fileName, username);
			 return Response.ok().build();
		} catch (IOException e) {
			e.printStackTrace();
		}
	   return Response.status(Status.BAD_REQUEST).entity("BAD Download request").build();
	}
	
	@GET
	@Path("/download/save")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response downloadFile(@QueryParam("file") String fileName) {
		String absoluteFilename = fileName.substring(fileName.lastIndexOf("/")+1, fileName.length());
		String absoluteFilePath = System.getProperty("catalina.base")+File.separator+"temp"+File.separator+
				"markdrive"+File.separator+absoluteFilename;
		File file = new File(absoluteFilePath);
		
		new DocumentHistoryLogger(fileName, "download");
		ResponseBuilder response = Response.ok(file);
		response.header("Content-Disposition", "attachment; filename="+absoluteFilename);
		return response.build();
	}
	
	@GET
	@Path("/lastversion")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response getLastversionedFile(@QueryParam("file") String fileName) {
		BigInteger timestamp = getLastModifiedTimestamp(fileName);
		if(timestamp == null) {
			return Response.status(Status.BAD_REQUEST).build();
		}
		PointInTime pit = new PointInTime();
		try {
			pit.getLastVersion(fileName, timestamp);
			return Response.status(Status.OK).build();
		} catch(Exception e) {
			return Response.status(Status.BAD_REQUEST).build();
		}
	}
	
	@GET
	@Path("/lastversion/download")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response downloadLastversionedFile(@QueryParam("file") String fileName) {
		String absoluteFilename = fileName.substring(fileName.lastIndexOf("/")+1, fileName.length());
		String absoluteFilePath = System.getProperty("catalina.base")+File.separator+"temp"+File.separator+
				"markdrive"+File.separator+absoluteFilename;
		File file = new File(absoluteFilePath);
		ResponseBuilder response = Response.ok(file);
		response.header("Content-Disposition", "attachment; filename="+absoluteFilename);
		return response.build();
		
	}
	
	@Path("/delete/{username}")
	@DELETE
	public Response deleteFile(@QueryParam("file")
								String filename,
								@PathParam("username") String username) {
		
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
		
		// create a generic manager for documents
		GenericDocumentManager docMgr = client.newDocumentManager();
		// delete the documents
		try {
			docMgr.delete(filename);
			return Response.status(Status.OK).build();
		} catch(Exception e) {
			return Response.status(Status.NOT_FOUND).build();
		} finally {
		//release the client
			client.release();
		}
	}

	@Path("/permission")
	@POST
	@Consumes("plain/text")
	public Response setFilePermission(String uname,
			@QueryParam("file") String fileName) {
		List<Authenticationinfo> authFromDB = new UserAuthentication().getUserInfo();
		if(authFromDB == null)
			return Response.status(Status.BAD_REQUEST).build();
		
		List<String> permissions = new ArrayList<String>();
		List<String> username = new ArrayList<String>();
		List<String> usergroups = new ArrayList<String>();
		if(uname.contains(";")) {
			String users[] = uname.split(";");
			for(String s:users) {
				String t[] = s.split(",");
				username.add(t[0]);
				permissions.add(t[1]);
			}
		} else {
			String t[] = uname.split(",");
			username.add(t[0]);
			permissions.add(t[1]);
		}
		for(String s: username) {
			System.out.println(s);
			String ugroup = getGroups(authFromDB, s);
			if( ugroup != null)
				usergroups.add(ugroup);
		}

		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		
		for(int i=0; i<usergroups.size(); i++) {
			GenericDocumentManager docMgr = client.newDocumentManager();
			DocumentMetadataHandle metadata = new DocumentMetadataHandle();
			docMgr.readMetadata(fileName, metadata);
			if(permissions.get(i).equals("1"))
				metadata.getPermissions().add(usergroups.get(i), Capability.READ, Capability.UPDATE);
			else 
				metadata.getPermissions().remove(usergroups.get(i));
			docMgr.writeMetadata(fileName, metadata);
		}
		client.release();
		return Response.status(Status.OK).build();
	}
	
	
	private String getGroups(List<Authenticationinfo> authFromDB, String name) {
		for(Authenticationinfo au:authFromDB) {
			if(au.getName().equals(name)) {
				return au.getGroup();
			}
		}
		return null;
	}
	
	private BigInteger getLastModifiedTimestamp(String fileName) {
		String File_ext  = (fileName.substring(fileName.indexOf(".")));
		
		// create the client;
		DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
				Configuration.USERNAME, Configuration.PASSWORD, Authentication.DIGEST);
		
		DocumentMetadataHandle metadata = new DocumentMetadataHandle();
		
		if (File_ext.toLowerCase().equals(".json")) {
			// create a manager for JSON documents
			JSONDocumentManager docMgr = client.newJSONDocumentManager();
			// read the document content
			docMgr.readMetadata(fileName, metadata);
		}
		else if (File_ext.toLowerCase().equals(".xml")){
			// create a manager for XML documents
			XMLDocumentManager docMgr = client.newXMLDocumentManager();
		
			docMgr.readMetadata(fileName, metadata);
		}
		else if (File_ext.toLowerCase().equals(".txt")){
			// get a manager for text documents
			TextDocumentManager docMgr = client.newTextDocumentManager();
			docMgr.readMetadata(fileName, metadata);
		}
		else {
			// get a manager for binary documents
			BinaryDocumentManager docMgr = client.newBinaryDocumentManager();		
			docMgr.readMetadata(fileName, metadata);
		}
		client.release();
		for (Entry<QName, Object> prop : metadata.getProperties().entrySet()) {
		    if(prop.getKey().getLocalPart().equals("lmt"))
			return BigInteger.valueOf((Long) prop.getValue());
		}
		return null;
	}
	
	
	private String downloadHelper(String fileName, String username) throws IOException {		
		FileOutputStream Op_File = null;
		String absoluteFilename = fileName.substring(fileName.lastIndexOf("/")+1, fileName.length());
		String absoluteFilePath = System.getProperty("catalina.base")+"\\temp\\markdrive\\"+absoluteFilename;
		System.out.println(absoluteFilename);
		try {
			Op_File = new FileOutputStream(absoluteFilePath);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		String File_ext  = (fileName.substring(fileName.indexOf(".")));
		
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
		
				
		if (File_ext.toLowerCase().equals(".json")) {
			// create a manager for JSON documents
			JSONDocumentManager docMgr = client.newJSONDocumentManager();
			// create a handle to receive the document content
			StringHandle handle = new StringHandle();
			// read the document content
			docMgr.read(fileName, handle);
			try {
				Op_File.write(handle.get().getBytes());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else if (File_ext.toLowerCase().equals(".xml")){
			// create a manager for XML documents
			XMLDocumentManager docMgr = client.newXMLDocumentManager();
			// create a handle to receive the document content
			BytesHandle content = new BytesHandle();
			// read the document content
			docMgr.read(fileName, content);
			// access the document content
			try {
				Op_File.write(content.get());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else if (File_ext.toLowerCase().equals(".txt")){
			// get a manager for text documents
			TextDocumentManager docMgr = client.newTextDocumentManager();
			// create a handle to receive the document content
			StringHandle content = new StringHandle();	
			// read the document content
			docMgr.read(fileName, content);
			try {
				Op_File.write(content.get().getBytes());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else {
			// get a manager for binary documents
			BinaryDocumentManager docMgr = client.newBinaryDocumentManager();
			// create a handle to receive the document content
			BytesHandle content = new BytesHandle();			
			docMgr.read(fileName, content);
			// get the document content as a byte array
			byte[] contentBytes = content.get();
			try {
				Op_File.write(contentBytes);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		try {
			Op_File.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// release the client
		client.release();
		return absoluteFilePath;
	}
	
	private void uploadFileToDB(String uploadername, String fileName, InputStream in, 
			String category, BigInteger timestamp) {
				
				String File_ext  = (fileName.substring(fileName.indexOf(".")));
				String username = Configuration.USERNAME;
				String password = Configuration.PASSWORD;
				if(uploadername.equals("saptarshi") || uploadername.equals("avirup")) {
					username = "saptarshi"; 
					password = "ticmark";
				} else if (uploadername.equals("debarshi")) {
					username = "debarshi";
					password = "ticmark";
				} else if (uploadername.equals("rasha")) {
					username = "rasha";
					password = "ticmark";
				}
				// create the client;
				DatabaseClient client = DatabaseClientFactory.newClient(Configuration.CONNECTIONURL, Configuration.PORT,
						username, password, Authentication.DIGEST);
				
				InputStreamHandle handle = new InputStreamHandle(in);
				
				DocumentMetadataHandle metadata = new DocumentMetadataHandle();
				System.out.println(category);
				
				String[] categoryArray = category.split(",");
				
				for(int i=0;i<categoryArray.length;i++) 
					metadata.getCollections().add(categoryArray[i].trim());
				metadata.getCollections().add(uploadername);
				
				metadata.getProperties().put("lmt", timestamp);
						
				if (File_ext.toLowerCase().equals(".json")) {
					// acquire the content				
					JSONDocumentManager docMgr = client.newJSONDocumentManager();
					// write the document content
					docMgr.write(uploadername+"/"+fileName, metadata, handle);
				}
				else if (File_ext.toLowerCase().equals(".xml")) {					
					XMLDocumentManager docMgr = client.newXMLDocumentManager();
					// write the document content
					docMgr.write(uploadername+"/"+fileName, metadata, handle);
				}
				else if (File_ext.toLowerCase().equals(".txt")) {
					
					TextDocumentManager docMgr = client.newTextDocumentManager();
					// write the document content
					docMgr.write(uploadername+"/"+fileName, metadata, handle);
				}
				else {
					// acquire the content				
					BinaryDocumentManager docMgr = client.newBinaryDocumentManager();
					// write the document content
					docMgr.write(uploadername+"/"+fileName, metadata, handle);
				}
				// release the client
				client.release();
			}

}