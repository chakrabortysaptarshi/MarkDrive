package com.saptarshi.rest;

public class DownloadStat {
	
	private String uri;
	private int count;
	
	public void seturi(String u) {
		uri = u;
	}
	
	public void setcount(int c) {
		count =c;
	}
	
	public String geturi(){
		return uri;
	}
	
	public int getcount() {
		return count;
	}
	
	public String toString() {
		return "{uri:"+uri+",count:"+count+"}";
	}

}
