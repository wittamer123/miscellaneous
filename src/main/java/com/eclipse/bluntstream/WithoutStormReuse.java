package com.eclipse.bluntstream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.eclipse.stormdataflows.DAGUtils;

public class WithoutStormReuse {
	public static void main(String[] args) throws Exception {
		Map<String, String> spoutidmap = new HashMap<String, String>();
		Map<String, String> boltidmap = new HashMap<String, String>();
		
		int globalcounter=0;
		
		BufferedWriter writr=null;
		BufferedWriter topidwritr = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(DAGUtils.topidfile)));
		BufferedWriter graphwrtr = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(DAGUtils.graphsfile)));
		int logicalctr=0, taskPerCore=8;
		List<String> srcdest = new LinkedList<>();
		List<String> spoutlist = new ArrayList<String>();
		BufferedReader bfrdr = new BufferedReader(new InputStreamReader(new FileInputStream("/home/sahil/opmw.json")));
		//BufferedReader bfrdr = new BufferedReader(new InputStreamReader(new FileInputStream("/home/dreamlab/Desktop/opmw.json")));
		StringBuilder stringBuilder = new StringBuilder("");
		String str;
		while((str = bfrdr.readLine()) != null) {
			stringBuilder = stringBuilder.append(str);	
		}
		bfrdr.close();
		
		JSONParser parser = new JSONParser();
		Object obj = parser.parse(stringBuilder.toString());
		JSONObject jsonObject = (JSONObject)obj;
		JSONArray dfarr = (JSONArray)jsonObject.get("dataflows");
		
		String dfseq = "6,32,16,35,28,13,20,31,10,5,8,1,30,27,14,19,11,7,15,33,18,25,9,24,4,3,29,17,34,12,23,2,26,22,21";
		String[] dfseqarr = dfseq.split(",");
		Iterator<JSONObject> itr1 = dfarr.iterator();
		HashMap<Long, JSONObject> dfmap = new HashMap<Long, JSONObject>();
		while(itr1.hasNext()) {
			JSONObject jsonob = itr1.next();
			Long dfId = (Long)jsonob.get("id");
			dfmap.put(dfId, jsonob);
		}
		
		for(String dfval : dfseqarr) {
			Set<String> spoutset = new HashSet<String>();
			Set<String> boltset = new HashSet<String>();
			
			Long did = Long.parseLong(dfval);
			JSONObject dfobj = dfmap.get(did);
			logicalctr++;
			StringBuilder spoutbuilder = new StringBuilder("# spout definition\nspouts:\n");
			StringBuilder boltstrbuilder = new StringBuilder("# bolt definitions\nbolts:\n");
			StringBuilder streambuilder = new StringBuilder("# stream definitions\nstreams:\n");
			Map<String, String> nodemapping = new HashMap<String, String>();
			
			JSONArray spoutarr = (JSONArray)dfobj.get("inputs");
			Iterator<JSONObject> itr2 = spoutarr.iterator();
			while(itr2.hasNext()) {
				JSONObject idobj = itr2.next();
				//System.out.println(idobj.get("id").toString());
				spoutlist.add(idobj.get("id").toString());
				spoutset.add(idobj.get("id").toString());
			}
			
			JSONArray edgearr = (JSONArray)dfobj.get("edges");
			itr2 = edgearr.iterator();
			while(itr2.hasNext()) {
				JSONObject edgeobj = itr2.next();
				//System.out.println(edgeobj.get("src").toString() + "_" + edgeobj.get("des").toString());
				srcdest.add(edgeobj.get("src").toString() + "_" + edgeobj.get("des").toString());
				if(!spoutset.contains(edgeobj.get("src").toString())) {
					boltset.add(edgeobj.get("src").toString());
				} 
				if(!spoutset.contains(edgeobj.get("des").toString())) {
					boltset.add(edgeobj.get("des").toString());
				}
			}
			
			//add to respective hash maps to get id dump file
			StringBuilder spoutidbuilder = new StringBuilder();
			StringBuilder boltidbuilder = new StringBuilder();
			Iterator<String> iditr = spoutset.iterator();
			while(iditr.hasNext()) {
				spoutidbuilder.append(iditr.next()+",");
			}
			spoutidbuilder = new StringBuilder(spoutidbuilder.toString().substring(0, spoutidbuilder.toString().length()-1));
			System.out.println("spout for topology-"+did+": " + spoutidbuilder.toString());
			spoutidmap.put("topology-"+did, spoutidbuilder.toString());
			
			iditr = boltset.iterator();
			while(iditr.hasNext()) {
				boltidbuilder.append(iditr.next()+",");
			}
			boltidbuilder = new StringBuilder(boltidbuilder.toString().substring(0, boltidbuilder.toString().length()-1));
			System.out.println("BOLT for topology-"+did+": " + boltidbuilder.toString());
			boltidmap.put("topology-"+did, boltidbuilder.toString());
			
			System.out.println("no is: " + did);
			writr= new BufferedWriter(new OutputStreamWriter(new FileOutputStream(DAGUtils.yamlfiles + "/storm-" + did + ".yaml")));
			//System.out.println(dfId);
			
			int rs=0, rb=0, taskcounter=0, workers=0;
			JSONArray taskarr = (JSONArray)dfobj.get("tasks");
			//Integer workers = (int)Math.ceil((double)taskarr.size()/taskPerCore);
			itr2 = taskarr.iterator();
			while(itr2.hasNext()) {
				JSONObject taskObj = (JSONObject)itr2.next();
				Long taskId = (Long)taskObj.get("id");
				String taskType = (String)taskObj.get("type");
				if(spoutlist.contains(String.valueOf(taskId))) {
					//spout
					spoutbuilder.append("  - id: \"" + "spout-" + taskId + "\"\n    className: \"" + "com.eclipse.OPMW.OPMWSpout" + "\"\n    "
										+ "parallelism: 1\n    constructorArgs:\n      - \"" + taskId + "\"\n\n");
					nodemapping.put(String.valueOf(taskId), "spout-" + taskId);
					rs++;
				} else {
					//bolt
					boltstrbuilder.append("  - id: \"" + "bolt-" + taskId + "\"\n    className: \"" + "com.eclipse.OPMW.OPMWBolt" + "\"\n    "
							+ "parallelism: 1\n    constructorArgs:\n      - \"" + "bolt-" + taskId + "\"\n      - \"" + taskId + "\"\n\n");
					nodemapping.put(String.valueOf(taskId), "bolt-" + taskId);
					rb++;
				}
			}
			taskcounter = rs + rb;
			workers = (int)Math.ceil((double)taskcounter/taskPerCore);
			System.out.println("TT,"+did+","+taskcounter+","+workers);
			globalcounter = globalcounter + workers;
			writr.write("name: \"topology-" + did + "\"\nconfig:\n  topology.workers: " + workers + "\n\n");
			writr.write(spoutbuilder.toString());
			writr.write(boltstrbuilder.toString());
			
		
			Set<String> idset = nodemapping.keySet();
			Iterator<String> itr3 = idset.iterator();
			while(itr3.hasNext()) {
				String srckey = itr3.next();
				Set<String> innerset = nodemapping.keySet();
				Iterator<String> itr4 = innerset.iterator();
				while(itr4.hasNext()) {
					String destkey = itr4.next();
					//System.out.println("val in iteration: " + srckey+"_"+destkey);
					if(srcdest.contains(srckey+"_"+destkey)) {
						streambuilder.append("  - name: \"" + nodemapping.get(srckey) + " --> " + nodemapping.get(destkey) +"\"\n    from: \"" 
								+ nodemapping.get(srckey) + "\"\n    to: \"" + nodemapping.get(destkey) + "\"\n    grouping:\n      type: SHUFFLE\n\n");
					}
				}
				
			}
			
			streambuilder = new StringBuilder(streambuilder.toString().substring(0, streambuilder.toString().length() -2));
			writr.write(streambuilder.toString());
			writr.close();
			
			String outputyaml = DAGUtils.yamlfiles + "/storm-" + did + ".yaml";
			ProcessBuilder builder = new ProcessBuilder(DAGUtils.storm, "jar", DAGUtils.compiledJAR, "org.apache.storm.flux.Flux", DAGUtils.mode, outputyaml);
			builder.redirectOutput(new File(DAGUtils.stormlogs + "logger-" + did + ".txt"));
			Process p = builder.start();
			
			graphwrtr.write(logicalctr + "," + "abc,xyz," + System.currentTimeMillis() + "\n");
			//System.out.println("going to sleep for 1 minute to let topology do it's thing...");
			Thread.sleep(60000);
			
		}
		
		System.out.println("TT total cores:" + globalcounter);
		
		//additional sleep to prevent skewing
		Thread.sleep(60000);
		
		if(logicalctr < 71) {
			
			String seq = "31,27,13,9,14,5,30,4,19,11,21,3,24,25,17,12,22,28,18,2,7,34,15,23,6,10,1,29,33,32,20,26,8,16,35";
			String[] seqarr = seq.split(",");
			for(String top : seqarr) {
				logicalctr++;
				System.out.println("!!!!!!! going to kill topology-" + top);
				ProcessBuilder builder = new ProcessBuilder(DAGUtils.storm, "kill", "topology-" + top);
				//before killing this, grab all spout and bolt ids for this topology
				String spoutstr = spoutidmap.get("topology-"+top);
				String boltstr = boltidmap.get("topology-"+top);
				if(spoutstr != null) {
					for(String str1: spoutstr.split(",")) {
						topidwritr.write("OPMW_SS,"+System.currentTimeMillis()+",RS,spout-"+str1+",true,false\n");
					}
				}
				
				if(boltstr != null) {
					for(String str1: boltstr.split(",")) {
						topidwritr.write("OPMW_SS,"+ System.currentTimeMillis()+",BT,bolt-"+str1+",true,false\n");
					}
				}
				
				Process p = builder.start();
				graphwrtr.write(logicalctr + "," + "abc,xyz," + System.currentTimeMillis() + "\n");
				Thread.sleep(60000);
			}
			
		}
		
		topidwritr.close();
		graphwrtr.close();
		System.out.println("done...");
	}
}