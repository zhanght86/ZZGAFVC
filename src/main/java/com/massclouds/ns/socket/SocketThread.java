package com.massclouds.ns.socket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import javax.enterprise.inject.New;

import org.tempuri1.FRSServiceStub.HitDetail;
import org.tempuri1.FRSServiceStub.HitRecordExtend;
import org.tempuri1.FRSServiceStub.NotHitRecordExtend;
import org.tempuri1.FRSServiceStub.UserBaseExtend;

import com.alibaba.fastjson.JSON;
import com.massclouds.ns.common.util.StringUtil;
import com.massclouds.ns.util.JsonHelper;
import com.massclouds.ns.util.WebServiceServe;

import sun.misc.*;

public class SocketThread implements Runnable {

	private Socket socket; // socket对象
	private String encoding; // 编码方式
	private ArrayList<Integer> oldIdListNotHit; // 抓拍记录列表
	private ArrayList<Integer> oldIdListHit; // 报警记录列表

	public SocketThread(Socket socket, String encoding) {
		this.socket = socket;
		this.encoding = encoding;
		oldIdListNotHit = new ArrayList<Integer>();
		oldIdListHit = new ArrayList<Integer>();
	}

	// 报警信息
	private String getHitInfo(String userType, int timespan) {
		HitRecordExtend[] array = WebServiceServe.getObj().GetLatestHitRecord(
				userType, timespan);
		// 为null 跳出
		if (array == null) {
			return null;
		}

		ArrayList<Integer> newIdList = new ArrayList<Integer>();
		for (int i = 0; i < array.length; i++) {
			newIdList.add(array[i].getId());
		}

		ArrayList<Integer> reIdList = new ArrayList<Integer>();
		// 比较筛选
		for (int i = 0; i < newIdList.size(); i++) {
			boolean notEqual = true;
			for (int j = 0; j < oldIdListHit.size(); j++) {
				if (newIdList.get(i).compareTo(oldIdListHit.get(j)) == 0) {
					notEqual = false;
					break;
				}
			}
			if (notEqual) {
				reIdList.add(newIdList.get(i));
			}

		}
		 System.out.println("HitOld---" + oldIdListHit.toString());
		 System.out.println("HitNew---" + newIdList.toString());
		 System.out.println("HitRe---" + reIdList.toString());
		Collections.sort(reIdList); // 升序排序
		// 完善筛选
		HitRecordExtend[] rearr = new HitRecordExtend[reIdList.size()];
		for (int i = 0; i < reIdList.size(); i++) {
			for (int j = 0; j < array.length; j++) {
				if (reIdList.get(i).compareTo(new Integer(array[j].getId())) == 0) {
					rearr[i] = array[j];
					break;
				}
			}
		}
		String str = hitToString(rearr); // 调用拼接函数
		oldIdListHit = new ArrayList<Integer>(); // 清空旧list
		oldIdListHit.addAll(newIdList); // 新变旧 谈下一话题
		return str;
	}

	// 转换完整字符串(报警)
	private String hitToString(HitRecordExtend[] array) {
		if (array.length == 0) {
			return null;
		}
		JDBC jdbc = new JDBC();
		String str = "[";
		for (int i = 0; i < array.length; i++) {
			str += "{";
			// 拼接json
			str += "\"queryImage\":\"" + array[i].getQueryImage() + "\",";
			str += "\"id\":\"" + array[i].getId() + "\",";
			str += "\"createTime\":\"" + array[i].getCreateTime() + "\",";
			str += "\"sourceId\":\"" + array[i].getSourceId() + "\",";
			// 取摄像机名称
			String sql = "select CameraName from face_camera where CameraSourceId='"
					+ array[i].getSourceId() + "'";
			Map map = JsonHelper.toMap(jdbc.sqlSelect(sql));
			str += "\"cameraName\":\"" + (String) map.get("CameraName") + "\",";
			str += "\"detail\":[";
			// 取比中记录详情 拼接json
			HitDetail[] userArr = array[i].getDetails().getHitDetail();
			for (int j = 0, k = 0; j < userArr.length; j++, k++) {
				if (k >= 2) {
					break;
				}
				UserBaseExtend ube = WebServiceServe.getObj().GetUserBase(
						userArr[j].getUserID());
				str += "{";
				str += "\"score\":\"" + userArr[j].getScore() + "\","; // 分数
				str += "\"faceImage\":\"" + ube.getFaceImage() + "\","; // 图片
				str += "\"id\":\"" + ube.getId() + "\","; // 主键id
				str += "\"name\":\"" + ube.getName() + "\","; // 姓名
				str += "\"type\":\"" + ube.getIdentityType() + "\","; // 所属库
				str += "\"mac\":\"" + ube.getMacAddr() + "\","; // mac 地址
				str += "\"level\":\"" + getLevel(userArr[j].getScore(),ube.getMacAddr(),10,"1001") + "\""; // mac 地址
				str += "}";
				if (j != userArr.length - 1 && k != 1) {
					str += ",";
				}

			}
			str += "]}";
			if (i != array.length - 1) {
				str += ",";
			}
		}
		str += "]";
		jdbc.close();
		return str;

	}
	
	//获取报警等级
	public static  int getLevel(Float score, String mac, Integer minute, String place) {
		try {
			boolean statu = WebServiceServe.getObj().CheckMacAppeared(mac,
					minute, place);
			int level = 0;
			if (statu) {
				level = 4;
				return level;
			}
			if (score < 0.6) {
				level = 1;
			} else if (score < 0.8) {
				level = 2;
			} else if (score < 0.9) {
				level = 3;
			} else if (score <= 1) {
				level = 4;
			}
			return level;
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	// 抓拍信息
	private String getNotHitInfo(String userType, int timespan) {
		NotHitRecordExtend[] array = WebServiceServe.getObj()
				.GetLatestCapturedFaces(userType, timespan);
		// 为null 跳出
		if (array == null) {
			return null;
		}

		ArrayList<Integer> newIdList = new ArrayList<Integer>();
		for (int i = 0; i < array.length; i++) {
			newIdList.add(array[i].getId());
		}

		ArrayList<Integer> reIdList = new ArrayList<Integer>();

		// 比较筛选
		for (int i = 0; i < newIdList.size(); i++) {
			boolean notEqual = true;
			for (int j = 0; j < oldIdListNotHit.size(); j++) {
				if (newIdList.get(i).compareTo(oldIdListNotHit.get(j)) == 0) {
					notEqual = false;
					break;
				}
			}
			if (notEqual) {
				reIdList.add(newIdList.get(i));
			}
			
			//10条数据
			if(reIdList.size()>=5){
				break;
			}

		}
		 System.out.println("notHitOld---"+oldIdListNotHit.toString());
		 System.out.println("notHitNew---"+newIdList.toString());
		 System.out.println("notHitRe---" + reIdList.toString());
		Collections.sort(reIdList); // 升序排序
		// 完善筛选
		NotHitRecordExtend[] rearr = new NotHitRecordExtend[reIdList.size()];
		for (int i = 0; i < reIdList.size(); i++) {
			for (int j = 0; j < array.length; j++) {
				if (reIdList.get(i).compareTo(new Integer(array[j].getId())) == 0) {
					rearr[i] = array[j];
					break;
				}
			}
		}

		String str = notHitToString(rearr);
		oldIdListNotHit = new ArrayList<Integer>(); // 清空旧list
		oldIdListNotHit.addAll(newIdList); // 新变旧 谈下一话题
		return str;
	}

	// 转换完整字符串(抓拍)
	private String notHitToString(NotHitRecordExtend[] array) {
		if (array.length == 0) {
			return null;
		}
		JDBC jdbc = new JDBC();
		for (int i = 0; i < array.length; i++) {
			String sql = "select CameraName from face_camera where CameraSourceId='"
					+ array[i].getSourceID() + "'";
			Map map = JsonHelper.toMap(jdbc.sqlSelect(sql));
			array[i].setSourceID((String) map.get("CameraName"));
		}
		jdbc.close();
		return JSON.toJSONString(array);

	}
	
	private String getNotHitInfoEx(String userType, int timespan,int topN,String sourceId){
		try {
			NotHitRecordExtend[] array = WebServiceServe.getObj()
					.GetLatestCapturedFacesEx(userType, timespan, sourceId, topN);
			ArrayList<Integer> newIdList = new ArrayList<Integer>();
			for (int i = 0; i < array.length; i++) {
				newIdList.add(array[i].getId());
			}

			ArrayList<Integer> reIdList = new ArrayList<Integer>();

			// 比较筛选
			for (int i = 0; i < newIdList.size(); i++) {
				boolean notEqual = true;
				for (int j = 0; j < oldIdListNotHit.size(); j++) {
					if (newIdList.get(i).compareTo(oldIdListNotHit.get(j)) == 0) {
						notEqual = false;
						break;
					}
				}
				if (notEqual) {
					reIdList.add(newIdList.get(i));
				}

			}
			 System.out.println("notHitOld---"+oldIdListNotHit.toString());
			 System.out.println("notHitNew---"+newIdList.toString());
			 System.out.println("notHitRe---" + reIdList.toString());
			// 完善筛选
			NotHitRecordExtend[] rearr = new NotHitRecordExtend[reIdList.size()];
			for (int i = 0; i < reIdList.size(); i++) {
				for (int j = 0; j < array.length; j++) {
					if (reIdList.get(i).compareTo(new Integer(array[j].getId())) == 0) {
						rearr[i] = array[j];
						break;
					}
				}
			}

			String str = notHitToString(rearr);
			oldIdListNotHit = new ArrayList<Integer>(); // 清空旧list
			oldIdListNotHit.addAll(newIdList); // 新变旧 谈下一话题
			return str;
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
	}

	/**
	 * 与客户端交互代码
	 */
	@Override
	public void run() {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					socket.getInputStream(), encoding));
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
					socket.getOutputStream(), encoding));
			String getMsg;
			while ((getMsg = br.readLine()) != null
					&& !"exit".equalsIgnoreCase(getMsg)) {
				 
				String[] msgArr = getMsg.split("@@@"); //分割字符串
				 
				String userType = msgArr[0]; //用户]
				String sourceId = StringUtil.unescape( msgArr[1]) ; //sourceid
				System.out.println("客户端发来的信息是:" +userType+"<-->"+sourceId+"<-->"+new
						 SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
						 .format(new Date()));
				
				int timespan = 1;
				String hitInfo = this.getHitInfo(userType, timespan);
//				String notHitInfo = this.getNotHitInfo(userType, timespan);
				String notHitInfo = this.getNotHitInfoEx(userType, timespan,5,sourceId);
				
				if (hitInfo == null && notHitInfo == null) {
					continue;
				}
				bw.append(hitInfo + "|||||" + notHitInfo + "!@#$%");
				bw.flush();
			}
			// 客户端提出"exit"请求，关闭当前socket...
			br.close();
			bw.close();
			socket.close();
			System.out.println("当前Socket连接结束......");
		} catch (Exception e) {
			e.printStackTrace();
			if (!socket.isClosed()) {
				try {
					socket.close();
					SocketServer.getObj(); //重新开启
				} catch (IOException e1) {
					// e1.printStackTrace();
				}
			}
			throw new RuntimeException("Socket线程类发送异常...", e);
		}
	}
}