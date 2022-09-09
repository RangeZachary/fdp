package pers.range.fdp.mds;

import java.util.Scanner;

public class Main
{
	/** 测试登陆账号*/
	public static final String USERNAME = "customer528";
	/** 测试登录密码*/
	public static final String PASSWORD = "tJxCsAyr";
	
	private static Scanner input;
  
	public static void main(String[] args)
	{
	    System.out.println("********************************************************************");
	    System.out.println("*                        MDS(行情)接口演示                           *");
	    System.out.println("********************************************************************");
	    System.out.println("请输入用例类型(1:TCP行情; 2:UDP组播行情):");
	    input = new Scanner(System.in);
	    String type = input.nextLine().trim();
	   
	    if (type.equals("1")) { // TCP行情
	    	MdsExample example = new MdsExample();
	        try {
	        	example.startup();
	        }
	        catch (Exception e) {
	        	e.printStackTrace();
	        }
	    }else { // UDP 组播行情
	    	MdsUdpExample example = new MdsUdpExample();
	        try {
	        	example.startup();
	        }
	        catch (Exception e) {
	        	e.printStackTrace();
	        }
	    }
	}
}