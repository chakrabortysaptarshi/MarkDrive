package com.saptarshi.rest;

public class Authenticationinfo {
	
	private String name;
	private String email;
	private String password ;
	private String group;

	public void setName(String s) {
		name = s;
	}
	
	public void setEmail(String s){
		email = s;
	}
	
	public void setPassword(String s) {
		password = s;
	}
	
	public void setGroup(String s) {
		group = s;
	}
	
	public String getName() {
		return name;
	}
	
	public String getEmail() {
		return email;
	}
	
	public String getPassword() {
		return password;
	}
	
	public String getGroup() {
		return group;
	}
}
