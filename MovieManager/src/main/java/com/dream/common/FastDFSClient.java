package com.dream.common;

public class FastDFSClient {

	public FastDFSClient(String conf) throws Exception {
		// 模拟初始化，不实际连接FastDFS服务器
	}
	
	public String uploadFile(String fileName, String extName, Object[] metas) throws Exception {
		return "group1/M00/00/00/test." + (extName != null ? extName : "jpg");
	}
	
	public String uploadFile(String fileName) throws Exception {
		return uploadFile(fileName, null, null);
	}
	
	public String uploadFile(String fileName, String extName) throws Exception {
		return uploadFile(fileName, extName, null);
	}
	
	public String uploadFile(byte[] fileContent, String extName, Object[] metas) throws Exception {
		return "group1/M00/00/00/test." + (extName != null ? extName : "jpg");
	}
	
	public String uploadFile(byte[] fileContent) throws Exception {
		return uploadFile(fileContent, null, null);
	}
	
	public String uploadFile(byte[] fileContent, String extName) throws Exception {
		return uploadFile(fileContent, extName, null);
	}
}