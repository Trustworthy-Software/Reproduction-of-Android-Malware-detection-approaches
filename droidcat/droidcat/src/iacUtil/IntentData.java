package iacUtil;

import java.util.ArrayList;

import java.util.regex.Pattern;

import android.net.Uri;


public class IntentData {
	String mimeType;
	StringBuilder uri;
	StringBuilder name;
	String scheme;
	String host;
	String port;
	String path;
	String pathPattern;
	String pathPrefix;

	public IntentData(){
	
		uri=null;

		scheme=null;
		
		name=null;
	
		host=null;
		
		port=null;
	
		path=null;
	
		pathPattern=null;
		
		pathPrefix=null;
	
		mimeType=null;
	}
	public IntentData (String data){
		
		scheme=null;

		host=null;

		port=null;
	
		path=null;
	
		pathPattern=null;
	
		pathPrefix=null;
	
		mimeType=null;
		
		uri=null;
		
		name=null;
		String [] temp=data.split(";");
		mimeType=new String(temp[0]);
		uri=new StringBuilder(temp[1]);
		name=new StringBuilder(temp[2]);
		Uri url=Uri.parse(temp[1]);
		scheme=url.getScheme();
		host=url.getHost();
		port=Integer.toString(url.getPort());
		path=url.getPath();
		
		
	}
	public String toString(){
		StringBuffer temp =new StringBuffer();
		temp.append(this.mimeType+";");
		if(this.name==null){
			temp.append(";");
		}
		else{
			temp.append(this.name.append(";"));
		}
		if(this.uri==null){
			temp.append(";");
		}
		else{
			temp.append(this.uri.append(";"));
		}
		temp.append(this.scheme+"://");
		temp.append(this.host+":");
		temp.append(this.port+"/");
		temp.append(this.path+";");
		temp.append(this.pathPrefix+";");
		temp.append(this.pathPattern);
		if(temp.toString().contains(",")){
			temp=new StringBuffer(temp.toString().replaceAll(Pattern.quote(","), "##"));
		}
		return temp.toString();
		
	}
	public Uri toUri(){
		String [] temp=this.toString().split(";");
		Uri url=Uri.parse(temp[1]);
		return url;
	}
	public void setscheme(String scheme){
		this.scheme=scheme;
	}
	public void sethost(String host){
		this.host=host;
	}
	public void setport(String port){
		this.port=port;
	}
	public void setpath(String path){
		this.path=path;
	}
	public void setpathPattern(String pathPattern){
		this.pathPattern=pathPattern;
	}
	public void setpathPrefix(String pathPrefix){
		this.pathPrefix=pathPrefix;
	}
	public void setmimeType (String mimeType){
		this.mimeType=mimeType;
	}
}
