package pl.edu.uj.synchrotron;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import fr.esrf.Tango.AttrDataFormat;
import fr.esrf.Tango.AttrQuality;
import fr.esrf.Tango.AttrWriteType;
import fr.esrf.Tango.DevEncoded;
import fr.esrf.Tango.DevFailed;
import fr.esrf.Tango.DevInfo;
import fr.esrf.Tango.DevSource;
import fr.esrf.Tango.DevState;
import fr.esrf.Tango.DevVarDoubleStringArray;
import fr.esrf.Tango.DevVarLongStringArray;
import fr.esrf.Tango.TimeVal;
import fr.esrf.TangoApi.ApiUtil;
import fr.esrf.TangoApi.AttributeInfo;
import fr.esrf.TangoApi.CommandInfo;
import fr.esrf.TangoApi.CommunicationFailed;
import fr.esrf.TangoApi.Database;
import fr.esrf.TangoApi.DbDatum;
import fr.esrf.TangoApi.DeviceAttribute;
import fr.esrf.TangoApi.DeviceData;
import fr.esrf.TangoApi.DeviceInfo;
import fr.esrf.TangoApi.DeviceProxy;
import fr.esrf.TangoDs.TangoConst;

// Plain old Java Object it does not extend as class or implements an interface
// The class registers its methods for the HTTP GET request using the @GET annotation.
// Using the @Produces annotation, it defines that it can deliver several MIME types,
// text, XML and HTML.
// The browser requests per default the HTML MIME type.
// Sets the path to base URL + /Database
@Path("/{host}:{port}")
public class RsDbDevices implements TangoConst {
	// private DeviceProxy device;
	String	devices[];

	// below is Tango database address and port, they are set from environment variable TANGO_HOST
	// String host; // = "192.168.0.19";
	// String port; // = "10000";
	@GET
	@Produces(MediaType.TEXT_HTML)
	public String devicesHtmlDefaulAction(@PathParam("host") String host, @PathParam("port") String port) {
		String retStr = "<html><title>Device</title><body><h1>Device list:</h1>";
		getHostFromEnv();
		try {
			System.out.println("Connecting to database at: " + host + ":" + port);
			Database db = ApiUtil.get_db_obj(host, port);
			System.out.println("Getting data from database");
			devices = db.get_device_list("*");
			for (int i = 0; i < devices.length; i++) {
				retStr = retStr + devices[i] + "<br>";
			}
		} catch (DevFailed e) {
			retStr = retStr + "Unable to connect to database";
			e.printStackTrace();
		}
		return retStr + "</body></html>";
	}

	@GET
	@Path("/Device.json")
	@Produces(MediaType.APPLICATION_JSON)
	public Response devicesJsonDefaultAction(@PathParam("host") String host, @PathParam("port") String port) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to database");
				Database db;
				DeviceProxy dp;
				db = ApiUtil.get_db_obj(host, port);
				System.out.println("Getting data from database");
				devices = db.get_device_list("*");
				retJSONObject.put("numberOfDevices", devices.length);
				long t0 = System.nanoTime();
				for (int i = 0; i < devices.length; i++) {
					retJSONObject.put("device" + i, devices[i].toUpperCase());
					try{
						dp = new DeviceProxy(devices[i].toUpperCase(),host,port);
						dp.ping();
						retJSONObject.put(devices[i].toUpperCase() + "isDeviceAlive", true);
						//System.out.println("Device "+classes[j]+"is alive");
					} catch (DevFailed e){
						e.printStackTrace();
						retJSONObject.put(devices[i].toUpperCase() + "isDeviceAlive", false);
					}
				}
				retJSONObject.put("connectionStatus", "OK");
				long t1 = System.nanoTime();
				long time = (t1 - t0) / 1000000;
				retJSONObject.put("Operation time", time);
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		// System.out.println("Header contain " + header.length() + " values");
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;// */
	}

	@GET
	@Path("/SortedDeviceList.json/{sorting_type}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response devicesJsonSortedDeviceList(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("sorting_type") String sortType) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to database");
				Database db = ApiUtil.get_db_obj(host, port);
				DeviceProxy dp;
				int sType = Integer.parseInt(sortType);
				long t0, t1, time;
				switch (sType) {
					case 1: // sort by class
						t0 = System.nanoTime();
						String classes[] = db.get_class_list("*");
						// int device_count = 0;
						retJSONObject.put("numberOfClasses", classes.length);
						for (int i = 0; i < classes.length; i++) {
							retJSONObject.put("className" + i, classes[i]);
							// System.out.println("Add class: [" + i + "] " + classes[i] + " to JSON response.");
							// Get the list of device name of the specified class
							DeviceData argin = new DeviceData();
							String request = "select name from device where class='" + classes[i] + "' order by name";
							argin.insert(request);
							DeviceData argout = db.command_inout("DbMySqlSelect", argin);
							DevVarLongStringArray arg = argout.extractLongStringArray();
							for (int j = 0; j < arg.svalue.length; j++) {
								retJSONObject.put(classes[i] + "DevCount", arg.svalue.length);
								retJSONObject.put(classes[i] + "Device" + j, arg.svalue[j].toUpperCase());
								// checking if device is alive
								try {
									dp = new DeviceProxy(classes[i], host, port);
									dp.ping();
									retJSONObject.put(classes[i] + "isDeviceAlive" + j, true);
									// System.out.println("Device "+classes[j]+"is alive");
								} catch (DevFailed e) {
									e.printStackTrace();
									retJSONObject.put(classes[i] + "isDeviceAlive" + j, false);
								}
								// System.out.print("	Device[" + device_count + "]: " + arg.svalue[j]);
								// device_count++;
							}
						}
						retJSONObject.put("connectionStatus", "OK");
						t1 = System.nanoTime();
						time = (t1 - t0) / 1000000;
						retJSONObject.put("Operation time", time);
						break;
					case 2: // sort by server
						t0 = System.nanoTime();
						System.out.println("Getting devices sorted by servers");
						String servers[];
						servers = db.get_server_name_list();
						// int device_count = 0;
						retJSONObject.put("ServerCount", servers.length);
						String[] instances;
						String[] srvList;
						String admName;
						String admNameSub;
						String classSub;
						String devSub;
						String[] devList;
						String[] dbList;
						for (int i = 0; i < servers.length; i++) {
							// System.out.println("Server name: [" + i + "] " + servers[i]);
							retJSONObject.put("Server" + i, servers[i]);
							instances = db.get_instance_name_list(servers[i]);
							retJSONObject.put(servers[i] + "InstCnt", instances.length);
							admNameSub = "dserver/" + servers[i] + "/";
							for (int j = 0; j < instances.length; j++) {
								classSub = "Se" + i + "In" + j + "Cl";
								// System.out.println("		Instance [" + j + "] name: " + instances[j]);
								retJSONObject.put(servers[i] + "Instance" + j, instances[j]);
								dbList = null;
								dbList = db.get_server_class_list(servers[i] + "/" + instances[j]);
								// Get the list from the database
								if (dbList.length > 0) {
									retJSONObject.put("Se" + i + "In" + j + "ClassCnt", dbList.length);
									for (int k = 0; k < dbList.length; k++) {
										// System.out.println("				Class name: " + dbList[k]);
										retJSONObject.put(classSub + k, dbList[k]);
										devList = db.get_device_name(servers[i] + "/" + instances[j], dbList[k]);
										retJSONObject.put("Se" + i + "In" + j + "Cl" + k + "DCnt", devList.length);
										// prepare string for naming devices
										devSub = classSub + k + "Dev";
										for (int l = 0; l < devList.length; l++) {
											retJSONObject.put(devSub + l, devList[l].toUpperCase());
											// System.out.println("					Device[" + device_count + "]: " + devList[l]);
											// device_count++;
											try {
												dp = new DeviceProxy(devList[l], host, port);
												dp.ping();
												retJSONObject.put(devList[l] + "isDeviceAlive" + l, true);
												// System.out.println("Device "+classes[j]+"is alive");
											} catch (DevFailed e) {
												retJSONObject.put(devList[l] + "isDeviceAlive" + l, false);
											}
										}
									}
								} else {
									retJSONObject.put("Se" + i + "In" + j + "ClassCnt", dbList.length);
									srvList = null;
									try {
										// Try to get class list through the admin device
										admName = admNameSub + instances[j];
										DeviceProxy adm = new DeviceProxy(admName, host, port);
										DeviceData datum = adm.command_inout("QueryClass");
										srvList = datum.extractStringArray();
									} catch (DevFailed e) {
										System.out.println("Adm name try error: " + e.getMessage());
									}
									if (srvList != null) {
										retJSONObject.put("Se" + i + "In" + j + "ClassCnt", srvList.length);
										for (int k = 0; k < srvList.length; k++) {
											// System.out.println("				Server name: " + srvList[k]);
											retJSONObject.put(classSub + k, srvList[k]);
											devList = db.get_device_name(servers[i] + "/" + instances[j], srvList[k]);
											retJSONObject.put(classSub + k + "DCnt", devList.length);
											devSub = classSub + k + "Dev";
											for (int l = 0; l < devList.length; l++) {
												retJSONObject.put(devSub + l, devList[l].toUpperCase());
												// System.out.println("					Device[" + device_count + "]: " + devList[l]);
												// device_count++;
												try {
													dp = new DeviceProxy(devList[l], host, port);
													dp.ping();
													retJSONObject.put(devList[l] + "isDeviceAlive" + l, true);
													// System.out.println("Device "+classes[j]+"is alive");
												} catch (DevFailed e) {
													e.printStackTrace();
													retJSONObject.put(devList[l] + "isDeviceAlive" + l, false);
												}
											}
										}
									}
								}
							}
						}
						// System.out.println("Device count: " + device_count);
						retJSONObject.put("connectionStatus", "OK");
						t1 = System.nanoTime();
						time = (t1 - t0) / 1000000;
						retJSONObject.put("Operation time", time);
						break;
					case 3: // sort by devices
						t0 = System.nanoTime();
						System.out.println("Connecting to database");
						// db = ApiUtil.get_db_obj(host, port);
						System.out.println("Getting data from database");
						devices = db.get_device_list("*");
						retJSONObject.put("connectionStatus", "OK");
						retJSONObject.put("numberOfDevices", devices.length);
						String[] splitted = new String[3];
						int i = devices.length - 1;
						int domainCount = 0;
						int classCount = 0;
						int deviceCount = 0;
						String devDomain = new String("");
						String devClass = new String("");
						int j = 0;
						while (j < i) {
							splitted = devices[j].toUpperCase().split("/");
							devDomain = splitted[0];
							retJSONObject.put("domain" + domainCount, devDomain);
							classCount = 0;
							// DevClassList dcl = new DevClassList(devDomain);
							// System.out.println("Petla 1 :" + devDomain + "  " + splitted[0]);
							while (devDomain.equals(splitted[0]) && (j < i)) {
								splitted = devices[j].toUpperCase().split("/");
								devClass = splitted[1];
								// System.out.println("    Petla 2 :" + devClass + "  " + splitted[1]);
								retJSONObject.put("domain" + domainCount + "class" + classCount, devClass);
								// ArrayList<String> members = new ArrayList<String>();
								deviceCount = 0;
								while (devClass.equals(splitted[1]) && (j < i) && devDomain.equals(splitted[0])) {
									// System.out.println("      Petla 3 :" + splitted[2]);
									retJSONObject.put("domain" + domainCount + "class" + classCount + "device" + deviceCount,
											devices[j].toUpperCase());
									deviceCount++;
									// System.out.println("Processing device: " + devDomain + "/" + devClass + "/" +
									// splitted[2]);
									try {
										dp = new DeviceProxy(devices[j].toUpperCase(), host, port);
										dp.ping();
										retJSONObject.put(devices[j].toUpperCase() + "isDeviceAlive", true);
										// System.out.println("Device "+classes[j]+"is alive");
									} catch (DevFailed e) {
										e.printStackTrace();
										retJSONObject.put(devices[j].toUpperCase() + "isDeviceAlive", false);
									}
									j++;
									if (j < i) {
										splitted = devices[j].toUpperCase().split("/");
									} else {
										break;
									}
								}
								retJSONObject.put("domain" + domainCount + "class" + classCount + "devCount", deviceCount);
								classCount++;
							}
							retJSONObject.put("domain" + domainCount + "classCount", classCount);
							domainCount++;
						}
						retJSONObject.put("domainCount", domainCount);
						retJSONObject.put("connectionStatus", "OK");
						t1 = System.nanoTime();
						time = (t1 - t0) / 1000000;
						retJSONObject.put("Operation time", time);
						/*long t0 = System.nanoTime();
						for (int i = 0; i < devices.length; i++) {
							retJSONObject.put("device" + i, devices[i]);
							try {
								dp = new DeviceProxy(devices[i], host, port);
								dp.ping();
								retJSONObject.put(devices[i] + "isDeviceAlive" + i, true);
								// System.out.println("Device "+classes[j]+"is alive");
							} catch (DevFailed e) {
								e.printStackTrace();
								retJSONObject.put(devices[i] + "isDeviceAlive" + i, false);
							}
						}
						long t1 = System.nanoTime();
						long time = (t1 - t0) / 1000000;
						retJSONObject.put("Operation time", time);*/
						break;
					default:
						retJSONObject.put("connectionStatus", "Unknown type of sorting!");
						break;
				}
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("/Device/{domain}/{class}/{member}")
	public String deviceHtmlDefaultAction(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		String retStr = "<html><title>Device " + devDomain + "/" + devClass + "/" + devMember + "</title>"
				+ "<body><h1>Device : " + devDomain + "/" + devClass + "/" + devMember + "</h1>";
		System.out.print(retStr);
		try {
			DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
			DeviceInfo di = dp.get_info();
			retStr = retStr + "<h2>Device info</h2>";
			retStr = retStr + di.toString() + "<br>";
			retStr = retStr + "<h2>Device properties</h2>";
			String prop_list[] = dp.get_property_list("*");
			for (int i = 0; i < prop_list.length; i++) {
				retStr = retStr + prop_list[i].toString() + "<br>";
			}
			retStr = retStr + "<h2>Device attributes</h2>";
			String att_list[] = dp.get_attribute_list();
			for (int i = 0; i < att_list.length; i++) {
				retStr = retStr + att_list[i].toString() + "<br>";
			}
		} catch (DevFailed e) {
			retStr = retStr + "Unable to connect to device<br>";
			e.printStackTrace();
		}
		return retStr + "</body></html>";
	}

	@GET
	@Path("/Device/{domain}/{class}/{member}/get_info")
	@Produces(MediaType.TEXT_HTML)
	public String deviceHtmlGetInfo(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		String retStr = "<html><title>Device " + devDomain + "/" + devClass + "/" + devMember + "</title>"
				+ "<body><h1>Device : " + devDomain + "/" + devClass + "/" + devMember + "</h1>";
		try {
			System.out.println("Connecting to device");
			DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
			DeviceInfo di = dp.get_info();
			System.out.println("Getting info from device");
			retStr = retStr + "<h2>Device info</h2>";
			retStr = retStr + di.toString() + "<br>";
		} catch (DevFailed e) {
			System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
					+ devMember);
			e.printStackTrace();
		}
		return retStr + "</body></html>";
	}

	@GET
	@Path("/Device/{domain}/{class}/{member}/get_info.json")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deviceJsonGetInfo(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				DeviceInfo di = dp.get_info();
				System.out.println("Getting info from device");
				retJSONObject.put("connectionStatus", "OK");
				retJSONObject.put("deviceInfo", di.toString());
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@PUT
	@Path("/Device/{domain}/{class}/{member}/set_source.json/{source}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deviceJsonSetSource(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("source") String source) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				int idx = Integer.parseInt(source);
				switch (idx) {
					case 0:
						dp.set_source(DevSource.CACHE);
						retJSONObject.put("connectionStatus", "OK");
						System.out.println("Set source CACHE");
						break;
					case 1:
						dp.set_source(DevSource.CACHE_DEV);
						retJSONObject.put("connectionStatus", "OK");
						System.out.println("Set source CACHE_DEVICE");
						break;
					case 2:
						dp.set_source(DevSource.DEV);
						retJSONObject.put("connectionStatus", "OK");
						System.out.println("Set source DEVICE");
						break;
					default:
						retJSONObject.put("connectionStatus", "Error! Unknown command");
				}
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Path("/Device/{domain}/{class}/{member}/get_source.json")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deviceJsonGetSource(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				DevSource source = dp.get_source();
				if (source.equals(DevSource.CACHE)) {
					retJSONObject.put("source", 0);
				}
				if (source.equals(DevSource.CACHE_DEV)) {
					retJSONObject.put("source", 1);
				}
				if (source.equals(DevSource.DEV)) {
					retJSONObject.put("source", 2);
				}
				// System.out.println("Source: "+ source.toString());
				// System.out.println("Source value: "+ source.value());
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Path("/Device/{domain}/{class}/{member}/get_device_info.json")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deviceJsonGetDeviceInfo(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				long t0 = System.currentTimeMillis();
				DevInfo out = dp.info();
				long t1 = System.currentTimeMillis();
				String message = new String("Command: " + devDomain + "/" + devClass + "/" + devMember + "/Info\n");
				message = message + "Duration: " + (t1 - t0) + " msec\n\n";
				message = message + "Server: " + out.server_id + "\n";
				message = message + "Server host: " + out.server_host + "\n";
				message = message + "Server version: " + out.server_version + "\n";
				message = message + "Class: " + out.dev_class + "\n";
				message = message + out.doc_url + "\n";
				System.out.println("Getting info from device");
				retJSONObject.put("connectionStatus", "OK");
				retJSONObject.put("deviceInfo", message);
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				retJSONObject.put("message", e.getMessage());
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Path("/Device/{domain}/{class}/{member}/ping_device.json")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deviceJsonPingDevice(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				String message = new String("Ping: : " + devDomain + "/" + devClass + "/" + devMember + "\n");
				long t0 = System.currentTimeMillis();
				dp.ping();
				long t1 = System.currentTimeMillis();
				message = message + new String("Duration: " + (t1 - t0) + " msec\n\n");
				message = message + "Device is alive\n";
				retJSONObject.put("pingStatus", message);
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				retJSONObject.put("message", e.getMessage());
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Path("/Device/{domain}/{class}/{member}/poll_status.json")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deviceJsonPollStatus(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				String message = new String("Polling: " + devDomain + "/" + devClass + "/" + devMember + "\n");
				DeviceData argin = new DeviceData();
				argin.insert(devDomain + "/" + devClass + "/" + devMember);
				try {
					// TODO Sprawdzić czy potrzebne jest get_adm_dev
					DeviceProxy deviceAdm = dp.get_adm_dev();
					DeviceData argout = deviceAdm.command_inout("DevPollStatus", argin);
					String[] pollStatus = argout.extractStringArray();
					for (int i = 0; i < pollStatus.length; i++) {
						message = message + pollStatus[i] + "\n\n";
					}
					retJSONObject.put("pollStatus", message);
					retJSONObject.put("connectionStatus", "OK");
				} catch (DevFailed e) {
					retJSONObject.put("message", "Cannot get device administration mode");
					retJSONObject.put("connectionStatus", "ERROR");
				}
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				retJSONObject.put("message", e.getMessage());
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@PUT
	@Path("/Device/{domain}/{class}/{member}/restart.json")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deviceJsonRestart(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				DeviceData argin = new DeviceData();
				argin.insert(devDomain + "/" + devClass + "/" + devMember);
				try {
					// TODO Sprawdzić czy potrzebne jest get_adm_dev
					DeviceProxy deviceAdm = dp.get_adm_dev();
					argin = new DeviceData();
					argin.insert(dp.name());
					deviceAdm.command_inout("DevRestart", argin);
					retJSONObject.put("connectionStatus", "OK");
				} catch (DevFailed e) {
					System.out.println(e.getMessage());
					e.printStackTrace();
					retJSONObject.put("message", "Cannot get device administration mode");
					retJSONObject.put("connectionStatus", "ERROR");
				}
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				retJSONObject.put("message", e.getMessage());
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("/Device/{domain}/{class}/{member}/get_property_list")
	public String deviceHtmlGetPropertyList(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		String retStr = "<html><title>Device " + devDomain + "/" + devClass + "/" + devMember + "</title>"
				+ "<body><h1>Device : " + devDomain + "/" + devClass + "/" + devMember + "</h1>";
		try {
			DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
			retStr = retStr + "<h2>Device properties</h2>";
			String prop_list[] = dp.get_property_list("*");
			for (int i = 0; i < prop_list.length; i++) {
				retStr = retStr + prop_list[i].toString() + "<br>";
			}
		} catch (DevFailed e) {
			retStr = retStr + "Unable to connect to device<br>";
			e.printStackTrace();
		}
		return retStr + "</body></html>";
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/get_property_list.json")
	public Response deviceJsonGetPropertyList(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				String prop_list[] = dp.get_property_list("*");
				retJSONObject.put("propertyCount", prop_list.length);
				System.out.println("Found " + prop_list.length + " properties");
				for (int i = 0; i < prop_list.length; i++) {
					retJSONObject.put("property" + i, prop_list[i].toString());
					DbDatum dbd = dp.get_property(prop_list[i]);
					retJSONObject.put("propValue" + i, dbd.extractString());
				}
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device");
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("/Device/{domain}/{class}/{member}/get_property/{prop_name}")
	public String deviceGetProperty(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("prop_name") String propName) {
		String retStr = "<html><title>Device " + devDomain + "/" + devClass + "/" + devMember + "</title>"
				+ "<body><h1>Device : " + devDomain + "/" + devClass + "/" + devMember + "</h1>";
		try {
			DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
			DbDatum devDbDatum = dp.get_property(propName);
			String propValue = devDbDatum.extractString();
			retStr = retStr + "Property name: " + propName + ", Value: " + propValue;
		} catch (DevFailed e) {
			retStr = retStr + "Unable to connect to device<br>";
			e.printStackTrace();
		}
		return retStr + "</body></html>";
	}

	@PUT
	@Produces(MediaType.TEXT_HTML)
	@Path("/Device/{domain}/{class}/{member}/put_property/{prop_name}/{prop_value}")
	public String deviceHtmlPutProperty(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("prop_name") String propName,
			@PathParam("prop_value") String propValue) {
		String retStr = "<html><title>Device " + devDomain + "/" + devClass + "/" + devMember + "</title>"
				+ "<body><h1>Device : " + devDomain + "/" + devClass + "/" + devMember + "</h1>";
		try {
			DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
			DbDatum devDbDatum = new DbDatum(propName, propValue);
			dp.put_property(devDbDatum);
			retStr = retStr + "Property name: " + propName + ", New value: " + propValue;
		} catch (DevFailed e) {
			retStr = retStr + "Unable to connect to device<br>";
			e.printStackTrace();
		}
		return retStr + "</body></html>";
	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/set_timeout_milis.json/{timeout}")
	public Response deviceJsonSetTimeoutMilis(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("timeout") String timeout) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				dp.set_timeout_millis(Integer.parseInt(timeout));
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Timeout value not updated. Unable to connect with device: "
						+ devDomain + "/" + devClass + "/" + devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/black_box.json/{nbCmd}")
	public Response deviceJsonBlackBox(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("nbCmd") String nbCmd) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				long t0 = System.currentTimeMillis();
				String[] out = dp.black_box(Integer.parseInt(nbCmd));
				long t1 = System.currentTimeMillis();
				String message = new String("Command: " + dp.name() + "/BlackBox\n" + "Duration: " + (t1 - t0)
						+ " msec\n\n");
				for (int i = 0; i < out.length; i++) {
					message = message + "[" + i + "]\t " + out[i] + "\n";
				}
				retJSONObject.put("blackBoxReply", message);
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "BlackBox not executed. Unable to connect with device: " + devDomain
						+ "/" + devClass + "/" + devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/put_property.json/{prop_name}/{prop_value}")
	public Response deviceJsonPutProperty(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("prop_name") String propName,
			@PathParam("prop_value") String propValue) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				DbDatum devDbDatum = new DbDatum(propName, propValue);
				dp.put_property(devDbDatum);
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Property " + propName
						+ " not updated. Unable to connect with device " + devDomain + "/" + devClass + "/" + devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("/Device/{domain}/{class}/{member}/get_attribute_list")
	public String deviceHtmlGetAttributeList(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		String retStr = "<html><title>Device " + devDomain + "/" + devClass + "/" + devMember + "</title>"
				+ "<body><h1>Device : " + devDomain + "/" + devClass + "/" + devMember + "</h1>";
		try {
			DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
			retStr = retStr + "<h2>Device attributes</h2>";
			String att_list[] = dp.get_attribute_list();
			for (int i = 0; i < att_list.length; i++) {
				retStr = retStr + att_list[i].toString() + "<br>";
			}
		} catch (DevFailed e) {
			retStr = retStr + "Unable to connect to device<br>";
			e.printStackTrace();
		}
		return retStr + "</body></html>";
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/get_attribute_list.json")
	public Response deviceJsonGetAttributeList(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device: " + devDomain + "/" + devClass + "/" + devMember);
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				System.out.println("Getting attribute list");
				String att_list[] = dp.get_attribute_list();
				// System.out.println("Add attribute count to reply");
				retJSONObject.put("attCount", att_list.length);
				for (int i = 0; i < att_list.length; i++) {
					// System.out.println("Add attribute data to reply");
					retJSONObject.put("attribute" + i, att_list[i].toString());
					System.out.println("Getting attribute[" + i + "]: " + att_list[i]);
					try {
						DeviceAttribute da = dp.read_attribute(att_list[i]);
						System.out.println("Getting attribute[" + i + "] info ");
						AttributeInfo ai = dp.get_attribute_info(att_list[i]);
						System.out.println("Getting attribute format");
						if (ai.data_format.value() == AttrDataFormat._SCALAR) {
							retJSONObject.put("attScalar" + i, true);
						} else {
							retJSONObject.put("attScalar" + i, false);
						}
						// System.out.println("Getting attribute value");
						retJSONObject.put("attValue" + i, extractDataValue(da, ai));
						// System.out.println("Getting attribute isWritable");
						retJSONObject.put("attWritable" + i, isWritable(ai));
						// System.out.println("Getting attribute isPlottable");
						retJSONObject.put("attPlotable" + i, isPlotable(ai));
						// System.out.println("Getting attribute description");
						retJSONObject.put("attDesc" + i, "Name: " + ai.name + "\n" + "Label: " + ai.label + "\n"
								+ "Writable: " + getWriteString(ai) + "\n" + "Data format: " + getFormatString(ai) + "\n"
								+ "Data type: " + Tango_CmdArgTypeName[ai.data_type] + "\n" + "Max Dim X: " + ai.max_dim_x
								+ "\n" + "Max Dim Y: " + ai.max_dim_y + "\n" + "Unit: " + ai.unit + "\n" + "Std Unit: "
								+ ai.standard_unit + "\n" + "Disp Unit: " + ai.display_unit + "\n" + "Format: " + ai.format
								+ "\n" + "Min value: " + ai.min_value + "\n" + "Max value: " + ai.max_value + "\n"
								+ "Min alarm: " + ai.min_alarm + "\n" + "Max alarm: " + ai.max_alarm + "\n" + "Description: "
								+ ai.description);
					} catch (DevFailed e) {
						System.out.println("Attribute is unreadable, cause: " + e.getMessage());
						retJSONObject.put("attScalar" + i, true);
						retJSONObject.put("attValue" + i, "Unreadable");
						retJSONObject.put("attWritable" + i, false);
						retJSONObject.put("attPlotable" + i, false);
						retJSONObject.put("attDesc" + i, e.getMessage());
					}
				}
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device " + devDomain + "/" + devClass + "/"
						+ devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("/Device/{domain}/{class}/{member}/read_attribute/{att_name}")
	public String deviceReadAttribute(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("att_name") String attName) {
		String retStr = "<html><title>Device " + devDomain + "/" + devClass + "/" + devMember + "</title>"
				+ "<body><h1>Device : " + devDomain + "/" + devClass + "/" + devMember + "</h1>";
		try {
			DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
			retStr = retStr + "<h2>Device attribute " + attName + "</h2>";
			DeviceAttribute da = dp.read_attribute(attName);
			AttributeInfo ai = dp.get_attribute_info(attName);
			String data = extractDataValue(da, ai);
			retStr = retStr + "Attribute: " + attName + ", value: " + data + "<br>";
			/*
			 * Check if attribute could be plotted.
			 */
			if ((ai.data_type == Tango_DEV_STRING || ai.data_type == Tango_DEV_STATE || ai.data_type == Tango_DEV_BOOLEAN)) {
				retStr = retStr + "Attribute is not plottable<br>";
			} else if (ai.data_format.value() == AttrDataFormat._SPECTRUM
					|| ai.data_format.value() == AttrDataFormat._IMAGE) {
				retStr = retStr + "Attribute is plottable<br>";
			} else {
				retStr = retStr + "Attribute is not plottable<br>";
			}
			/*
			 * Check if attribute can be written with new value.
			 */
			if ((ai.writable.value() == AttrWriteType._READ_WITH_WRITE)
					|| (ai.writable.value() == AttrWriteType._READ_WRITE) || (ai.writable.value() == AttrWriteType._WRITE)) {
				retStr = retStr + "Attribute is writable<br>";
			}
		} catch (DevFailed e) {
			retStr = retStr + "Unable to connect to device<br>";
			e.printStackTrace();
		}
		return retStr + "</body></html>";
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/read_attribute.json/{att_name}")
	public Response deviceJsonReadAttribute(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("att_name") String attName) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				try {
					DeviceAttribute da = dp.read_attribute(attName);
					AttributeInfo ai = dp.get_attribute_info(attName);
					retJSONObject.put("devName", dp.name());
					retJSONObject.put("attName", ai.name);
					retJSONObject.put("attValue", extractDataValues(da, ai));
				} catch (CommunicationFailed e) {
					System.out.println("Attribute unreadable, cause: " + e.getMessage());
					retJSONObject.put("devName", dp.name());
					retJSONObject.put("attName", attName);
					retJSONObject.put("attValue", "Attribute unreadable, cause: " + e.getMessage());
				}
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Attribute " + attName + " not read. Unable to connect with device "
						+ devDomain + "/" + devClass + "/" + devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/plot_attribute.json/{att_name}")
	public Response deviceJsonPlotAttribute(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("att_name") String attName) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				DeviceAttribute da = dp.read_attribute(attName);
				AttributeInfo ai = dp.get_attribute_info(attName);
				retJSONObject.put("plotLabel", ai.label + "[" + ai.unit + "]");
				switch (ai.data_format.value()) {
					case AttrDataFormat._SPECTRUM:
						retJSONObject.put("dataFormat", "SPECTRUM");
						double[] dataToPlot = extractSpectrumPlotData(da, ai);
						JSONArray array = new JSONArray();
						for (int i = 0; i < dataToPlot.length; i++) {
							// System.out.println("Data to plot["+i+"]: " + dataToPlot[i]);
							array.put(i, dataToPlot[i]);
						}
						System.out.println("Inserting array: " + array.toString());
						retJSONObject.put("plotData", array);
						break;
					case AttrDataFormat._IMAGE:
						retJSONObject.put("dataFormat", "IMAGE");
						double[][] doubleArray = extractImagePlotData(da, ai);
						retJSONObject.put("rows", doubleArray.length);
						retJSONObject.put("cols", doubleArray[0].length);
						for (int i = 0; i < doubleArray.length; i++) {
							JSONArray oneDimArray = new JSONArray();
							for (int j = 0; j < doubleArray[0].length; j++) {
								// System.out.println("Inserting double: "+ doubleArray[i][j]);
								oneDimArray.put(j, doubleArray[i][j]);
							}
							System.out.println("Inserting array: " + oneDimArray.toString());
							retJSONObject.put("row" + i, oneDimArray);
						}
						break;
				}
				// System.out.println(retJSONObject.toString());
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Attribute " + attName + " not read. Unable to connect with device "
						+ devDomain + "/" + devClass + "/" + devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Nie udało się zapisać danych");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@PUT
	@Produces(MediaType.TEXT_HTML)
	@Path("/Device/{domain}/{class}/{member}/write_attribute/{att_name}/{att_value}")
	public String deviceHtmlWriteAttribute(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("att_name") String attName,
			@PathParam("att_value") String attValue) {
		String retStr = "<html><title>Device " + devDomain + "/" + devClass + "/" + devMember + "</title>"
				+ "<body><h1>Device : " + devDomain + "/" + devClass + "/" + devMember + "</h1>";
		try {
			DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
			retStr = retStr + "<h2>Device attribute " + attName + "</h2>";
			DeviceAttribute da = dp.read_attribute(attName);
			AttributeInfo ai = dp.get_attribute_info(attName);
			da = insertData(attValue, da, ai);
			dp.write_attribute(da);
			String data = extractDataValue(da, ai);
			retStr = retStr + "Attribute: " + attName + ", new value: " + data;
		} catch (DevFailed e) {
			retStr = retStr + "Unable to connect to device<br>";
			e.printStackTrace();
		}
		return retStr + "</body></html>";
	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/write_attribute.json/{att_name}/{att_value}")
	public Response deviceJsonWriteAttribute(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("att_name") String attName,
			@PathParam("att_value") String attValue) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device");
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				DeviceAttribute da = dp.read_attribute(attName);
				AttributeInfo ai = dp.get_attribute_info(attName);
				da = insertData(attValue, da, ai);
				dp.write_attribute(da);
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Attribute " + attName
						+ " not updated. Unable to connect with device " + devDomain + "/" + devClass + "/" + devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			System.out.println("Unable to write data.");
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/command_list_query.json")
	public Response deviceJsonCommandListQuery(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember) {
		JSONObject retJSONObject = new JSONObject();
		try {
			try {
				System.out.println("Connecting to device: " + devDomain + "/" + devClass + "/" + devMember);
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				CommandInfo comm_info[] = dp.command_list_query();
				retJSONObject.put("commandCount", comm_info.length);
				for (int i = 0; i < comm_info.length; i++) {
					retJSONObject.put("command" + i, comm_info[i].cmd_name);
					retJSONObject.put("inType" + i, comm_info[i].in_type);
					retJSONObject.put("outType" + i, comm_info[i].out_type);
					retJSONObject.put("isPlottable" + i, isPlotable(comm_info[i].out_type));
					retJSONObject.put("inDesc" + i, comm_info[i].in_type_desc);
					retJSONObject.put("outDesc" + i, comm_info[i].out_type_desc);
				}
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device " + devDomain + "/" + devClass + "/"
						+ devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/command_inout.json/{commandName}/{argin}")
	public Response deviceJsonCommandInOut(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("commandName") String commandName,
			@PathParam("argin") String inputArgument) {
		JSONObject retJSONObject = new JSONObject();
		if (inputArgument.equals("DevVoidArgument")) {
			inputArgument = "";
		}
		try {
			try {
				System.out.println("Connecting to device: " + devDomain + "/" + devClass + "/" + devMember);
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				// CommandInfo comm_info[] = dp.command_list_query();
				CommandInfo ci = dp.command_query(commandName);
				DeviceData argin = new DeviceData();
				argin = insertData(inputArgument, argin, ci.in_type);
				long t0 = System.currentTimeMillis();
				DeviceData argout = dp.command_inout(commandName, argin);
				long t1 = System.currentTimeMillis();
				System.out.print("----------------------------------------------------\n");
				System.out.print("Command: " + dp.name() + "/" + commandName + "\n");
				System.out.print("Duration: " + (t1 - t0) + " msec\n");
				retJSONObject.put("replyHeader", "Command: " + dp.name() + "/" + commandName + "\nDuration: " + (t1 - t0)
						+ " msec\n");
				if (ci.out_type == Tango_DEV_VOID) {
					System.out.println("Command OK");
					retJSONObject.put("commandReply", "Command OK");
				} else {
					System.out.println("Output argument(s) :");
					String commandOut = new String();
					commandOut = extractData(argout, ci.out_type);
					System.out.print(commandOut);
					retJSONObject.put("commandReply", commandOut);
				}
				retJSONObject.put("connectionStatus", "OK");
			} catch (NumberFormatException e) {
				retJSONObject.put("connectionStatus", "Number Format Exception");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device " + devDomain + "/" + devClass + "/"
						+ devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/Device/{domain}/{class}/{member}/extract_plot_data.json/{commandName}/{argin}")
	public Response deviceJsonCommandPlot(@PathParam("host") String host, @PathParam("port") String port,
			@PathParam("domain") String devDomain, @PathParam("class") String devClass,
			@PathParam("member") String devMember, @PathParam("commandName") String commandName,
			@PathParam("argin") String inputArgument) {
		JSONObject retJSONObject = new JSONObject();
		if (inputArgument.equals("DevVoidArgument")) {
			inputArgument = "";
		}
		try {
			try {
				System.out.println("Connecting to device: " + devDomain + "/" + devClass + "/" + devMember);
				DeviceProxy dp = new DeviceProxy(devDomain + "/" + devClass + "/" + devMember, host, port);
				// CommandInfo comm_info[] = dp.command_list_query();
				CommandInfo ci = dp.command_query(commandName);
				DeviceData argin = new DeviceData();
				argin = insertData(inputArgument, argin, ci.in_type);
				long t0 = System.currentTimeMillis();
				DeviceData argout = dp.command_inout(commandName, argin);
				long t1 = System.currentTimeMillis();
				// System.out.print("----------------------------------------------------\n");
				// System.out.print("Command: " + dp.name() + "/" + commandName + "\n");
				// System.out.print("Duration: " + (t1 - t0) + " msec\n");
				retJSONObject.put("replyHeader", "Command: " + dp.name() + "/" + commandName + "\nDuration: " + (t1 - t0)
						+ " msec\n");
				double[] values = extractPlotData(argout, ci.out_type);
				JSONArray array = new JSONArray();
				for (int i = 0; i < values.length; i++) {
					array.put(i, values[i]);
				}
				retJSONObject.put("plotData", array);
				retJSONObject.put("connectionStatus", "OK");
			} catch (DevFailed e) {
				retJSONObject.put("connectionStatus", "Unable to connect with device " + devDomain + "/" + devClass + "/"
						+ devMember);
				System.out.println("Problem occured while connecting with device: " + devDomain + "/" + devClass + "/"
						+ devMember);
				e.printStackTrace();
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		Response finalResponse = Response.ok(retJSONObject.toString(), MediaType.APPLICATION_JSON).build();
		return finalResponse;
	}

	/**
	 * Extract data read from device and converts to String.
	 * 
	 * @param data
	 *           Data read from device
	 * @param outType
	 *           Identifier of data type.
	 * @return String with data.
	 */
	private String extractData(DeviceData data, int outType) {
		StringBuffer ret_string = new StringBuffer();
		switch (outType) {
			case Tango_DEV_VOID:
				break;
			case Tango_DEV_BOOLEAN:
				ret_string.append(Boolean.toString(data.extractBoolean()));
				ret_string.append("\n");
				break;
			case Tango_DEV_USHORT:
				ret_string.append(Integer.toString(data.extractUShort()));
				ret_string.append("\n");
				break;
			case Tango_DEV_SHORT:
				ret_string.append(Short.toString(data.extractShort()));
				ret_string.append("\n");
				break;
			case Tango_DEV_ULONG:
				ret_string.append(Long.toString(data.extractULong()));
				ret_string.append("\n");
				break;
			case Tango_DEV_LONG:
				ret_string.append(Integer.toString(data.extractLong()));
				ret_string.append("\n");
				break;
			case Tango_DEV_FLOAT:
				ret_string.append(Float.toString(data.extractFloat()));
				ret_string.append("\n");
				break;
			case Tango_DEV_DOUBLE:
				ret_string.append(Double.toString(data.extractDouble()));
				ret_string.append("\n");
				break;
			case Tango_DEV_STRING:
				ret_string.append(data.extractString());
				ret_string.append("\n");
				break;
			case Tango_DEVVAR_CHARARRAY: {
				byte[] dummy = data.extractByteArray();
				int start = getLimitMin(ret_string, dummy.length);
				int end = getLimitMax(ret_string, dummy.length);
				for (int i = start; i < end; i++) {
					ret_string.append("[" + i + "]\t " + Integer.toString(dummy[i]));
					if (dummy[i] >= 32)
						ret_string.append(" '" + (new Character((char) dummy[i]).toString()) + "'");
					else
						ret_string.append(" '.'");
					ret_string.append("\n");
				}
			}
				break;
			case Tango_DEVVAR_USHORTARRAY: {
				int[] dummy = data.extractUShortArray();
				int start = getLimitMin(ret_string, dummy.length);
				int end = getLimitMax(ret_string, dummy.length);
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + Integer.toString(dummy[i]) + "\n");
			}
				break;
			case Tango_DEVVAR_SHORTARRAY: {
				short[] dummy = data.extractShortArray();
				int start = getLimitMin(ret_string, dummy.length);
				int end = getLimitMax(ret_string, dummy.length);
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + Short.toString(dummy[i]) + "\n");
			}
				break;
			case Tango_DEVVAR_ULONGARRAY: {
				long[] dummy = data.extractULongArray();
				int start = getLimitMin(ret_string, dummy.length);
				int end = getLimitMax(ret_string, dummy.length);
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + Long.toString(dummy[i]) + "\n");
			}
				break;
			case Tango_DEVVAR_LONGARRAY: {
				int[] dummy = data.extractLongArray();
				int start = getLimitMin(ret_string, dummy.length);
				int end = getLimitMax(ret_string, dummy.length);
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + Integer.toString(dummy[i]) + "\n");
			}
				break;
			case Tango_DEVVAR_FLOATARRAY: {
				float[] dummy = data.extractFloatArray();
				int start = getLimitMin(ret_string, dummy.length);
				int end = getLimitMax(ret_string, dummy.length);
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + Float.toString(dummy[i]) + "\n");
			}
				break;
			case Tango_DEVVAR_DOUBLEARRAY: {
				double[] dummy = data.extractDoubleArray();
				int start = getLimitMin(ret_string, dummy.length);
				int end = getLimitMax(ret_string, dummy.length);
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t" + Double.toString(dummy[i]) + "\n");
			}
				break;
			case Tango_DEVVAR_STRINGARRAY: {
				String[] dummy = data.extractStringArray();
				int start = getLimitMin(ret_string, dummy.length);
				int end = getLimitMax(ret_string, dummy.length);
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + dummy[i] + "\n");
			}
				break;
			case Tango_DEVVAR_LONGSTRINGARRAY: {
				DevVarLongStringArray dummy = data.extractLongStringArray();
				int start = getLimitMin(ret_string, dummy.lvalue.length);
				int end = getLimitMax(ret_string, dummy.lvalue.length);
				ret_string.append("lvalue:\n");
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + Integer.toString(dummy.lvalue[i]) + "\n");
				start = getLimitMin(ret_string, dummy.svalue.length);
				end = getLimitMax(ret_string, dummy.svalue.length);
				ret_string.append("svalue:\n");
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + dummy.svalue[i] + "\n");
			}
				break;
			case Tango_DEVVAR_DOUBLESTRINGARRAY: {
				DevVarDoubleStringArray dummy = data.extractDoubleStringArray();
				int start = getLimitMin(ret_string, dummy.dvalue.length);
				int end = getLimitMax(ret_string, dummy.dvalue.length);
				ret_string.append("dvalue:\n");
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + Double.toString(dummy.dvalue[i]) + "\n");
				start = getLimitMin(ret_string, dummy.svalue.length);
				end = getLimitMax(ret_string, dummy.svalue.length);
				ret_string.append("svalue:\n");
				for (int i = start; i < end; i++)
					ret_string.append("[" + i + "]\t " + dummy.svalue[i] + "\n");
			}
				break;
			case Tango_DEV_STATE:
				ret_string.append(Tango_DevStateName[data.extractDevState().value()]);
				ret_string.append("\n");
				break;
			default:
				ret_string.append("Unsupported command type code=" + outType);
				ret_string.append("\n");
				break;
		}
		return ret_string.toString();
	}

	/**
	 * Check maximum length of response.
	 * 
	 * @param retStr
	 *           Response string.
	 * @param length
	 *           Length of current response.
	 * @return Maximum response length.
	 */
	private int getLimitMax(StringBuffer retStr, int length) {
		if (length < 100) {
			return length;
		}
		return 100;
	}

	/**
	 * Check minimum length of response.
	 * 
	 * @param retStr
	 *           Response string.
	 * @param length
	 *           Length of current response.
	 */
	private int getLimitMin(StringBuffer retStr, int length) {
		// if(length<=common.getAnswerLimitMin()) {
		// retStr.append("Array cannot be displayed. (You may change the AnswerLimitMin)\n");
		// return length;
		// } else {
		// return common.getAnswerLimitMin();
		return 0;
		// }
	}

	/**
	 * Check if attribute could be plotted.
	 * 
	 * @param ai
	 *           Attribute to be checked.
	 * @return True when attribute can be plotted.
	 */
	/*private boolean isPlotable(AttributeInfo ai) {
		if ((ai.data_type == Tango_DEV_STRING) || (ai.data_type == Tango_DEV_STATE)
				|| (ai.data_type == Tango_DEV_BOOLEAN))
			return false;
		return (ai.data_format.value() == AttrDataFormat._SPECTRUM) || (ai.data_format.value() == AttrDataFormat._IMAGE);
	}*/
	/**
	 * Check if attribute can be written with new value.
	 * 
	 * @param ai
	 *           Attribute to be checked.
	 * @return True when attribute can be written.
	 */
	/*private boolean isWritable(AttributeInfo ai) {
		return (ai.writable.value() == AttrWriteType._READ_WITH_WRITE)
				|| (ai.writable.value() == AttrWriteType._READ_WRITE) || (ai.writable.value() == AttrWriteType._WRITE);
	}*/
	// -----------------------------------------------------
	// Private stuff
	// -----------------------------------------------------
	/*/**
	 * Get alphabetically sorted list of attributes.
	 * 
	 * @return Array of attributes.
	 * @throws DevFailed
	 *            When device is uninitialized or there was problem with connection.
	 */
	/*private AttributeInfo[] getAttributeList() throws DevFailed {
		int i, j;
		boolean end;
		AttributeInfo tmp;
		AttributeInfo[] lst = device.get_attribute_info();
		// Sort the list
		end = false;
		j = lst.length - 1;
		while (!end) {
			end = true;
			for (i = 0; i < j; i++) {
				if (lst[i].name.compareToIgnoreCase(lst[i + 1].name) > 0) {
					end = false;
					tmp = lst[i];
					lst[i] = lst[i + 1];
					lst[i + 1] = tmp;
				}
			}
			j--;
		}
		return lst;
	}*/
	/**
	 * Check whether attribute could be read, written or both.
	 * 
	 * @param ai
	 *           Attribute to be checked.
	 * @return String defining write permission.
	 */
	static String getWriteString(AttributeInfo ai) {
		switch (ai.writable.value()) {
			case AttrWriteType._READ:
				return "READ";
			case AttrWriteType._READ_WITH_WRITE:
				return "READ_WITH_WRITE";
			case AttrWriteType._READ_WRITE:
				return "READ_WRITE";
			case AttrWriteType._WRITE:
				return "WRITE";
		}
		return "Unknown";
	}

	/**
	 * Check whether attribute could be presented as scalar, spectrum or image.
	 * 
	 * @param ai
	 *           Attribute to be checked.
	 * @return String defining presentation format.
	 */
	static String getFormatString(AttributeInfo ai) {
		switch (ai.data_format.value()) {
			case AttrDataFormat._SCALAR:
				return "Scalar";
			case AttrDataFormat._SPECTRUM:
				return "Spectrum";
			case AttrDataFormat._IMAGE:
				return "Image";
		}
		return "Unknown";
	}

	/**
	 * Extract data read from device and convert to String.
	 * 
	 * @param data
	 *           Data read from device
	 * @param ai
	 *           Parameter of read data.
	 * @return String with data.
	 */
	private String extractDataValue(DeviceAttribute data, AttributeInfo ai) {
		StringBuffer ret_string = new StringBuffer();
		try {
			// Add dimension of the attribute but only if having a meaning
			boolean printIndex = true;
			boolean checkLimit = true;
			// Add values
			switch (ai.data_type) {
				case Tango_DEV_STATE: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						ret_string.append(data.extractState());
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_UCHAR: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						short dummy = data.extractUChar();
						ret_string.append(Short.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_SHORT: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						short dummy = data.extractShort();
						ret_string.append(Short.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_BOOLEAN: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						boolean dummy = data.extractBoolean();
						ret_string.append(Boolean.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_USHORT: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						int dummy = data.extractUShort();
						ret_string.append(Integer.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_LONG: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						int dummy = data.extractLong();
						ret_string.append(Integer.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_ULONG: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						long dummy = data.extractULong();
						ret_string.append(Long.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_LONG64: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						long dummy = data.extractLong64();
						ret_string.append(Long.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_ULONG64: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						long dummy = data.extractULong64();
						ret_string.append(Long.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_DOUBLE: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						double dummy = data.extractDouble();
						ret_string.append(Double.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_FLOAT: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						float dummy = data.extractFloat();
						ret_string.append(Float.toString(dummy));
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_STRING: {
					if (ai.data_format.value() == AttrDataFormat._SCALAR) {
						String dummy = data.extractString();
						ret_string.append(dummy);
					} else {
						ret_string.append("Too many values!");
					}
				}
					break;
				case Tango_DEV_ENCODED: {
					printIndex = true;
					DevEncoded e = data.extractDevEncoded();
					ret_string.append("Format: " + e.encoded_format + "\n");
					int nbRead = e.encoded_data.length;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++) {
						short vs = (short) e.encoded_data[i];
						vs = (short) (vs & 0xFF);
						printArrayItem(ret_string, i, printIndex, Short.toString(vs), false);
					}
				}
					break;
				default:
					ret_string.append("Unsupported attribute type code=" + ai.data_type + "\n");
					break;
			}
		} catch (DevFailed e) {
			// ErrorPane.showErrorMessage(this,device.name() + "/" + ai.name,e);
		}
		return ret_string.toString();
	}

	/**
	 * Extract data read from device and convert to String.
	 * 
	 * @param data
	 *           Data read from device
	 * @param ai
	 *           Parameter of read data.
	 * @return String with data.
	 */
	private String extractDataValues(DeviceAttribute data, AttributeInfo ai) {
		StringBuffer ret_string = new StringBuffer();
		try {
			// Add the date of the measure in two formats
			TimeVal t = data.getTimeVal();
			java.util.Date date = new java.util.Date((long) (t.tv_sec * 1000.0 + t.tv_usec / 1000.0));
			SimpleDateFormat dateformat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			dateformat.setTimeZone(TimeZone.getDefault());
			ret_string.append("measure date: " + dateformat.format(date) + " + " + (t.tv_usec / 1000) + "ms\n");
			// Add the quality information
			AttrQuality q = data.getQuality();
			ret_string.append("quality: ");
			switch (q.value()) {
				case AttrQuality._ATTR_VALID:
					ret_string.append("VALID\n");
					break;
				case AttrQuality._ATTR_INVALID:
					ret_string.append("INVALID\n");
					return ret_string.toString();
				case AttrQuality._ATTR_ALARM:
					ret_string.append("ALARM\n");
					break;
				case AttrQuality._ATTR_CHANGING:
					ret_string.append("CHANGING\n");
					break;
				case AttrQuality._ATTR_WARNING:
					ret_string.append("WARNING\n");
					break;
				default:
					ret_string.append("UNKNOWN\n");
					break;
			}
			// Add dimension of the attribute but only if having a meaning
			boolean printIndex = true;
			boolean checkLimit = true;
			switch (ai.data_format.value()) {
				case AttrDataFormat._SCALAR:
					printIndex = false;
					checkLimit = false;
					break;
				case AttrDataFormat._SPECTRUM:
					ret_string.append("dim x: " + data.getDimX() + "\n");
					break;
				case AttrDataFormat._IMAGE:
					ret_string.append("dim x: " + data.getDimX() + "\n");
					ret_string.append("dim y: " + data.getDimY() + "\n");
					break;
				default:
					break;
			}
			// Add values
			switch (ai.data_type) {
				case Tango_DEV_STATE: {
					DevState[] dummy = data.extractDevStateArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Tango_DevStateName[dummy[i].value()], false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Tango_DevStateName[dummy[i + nbRead].value()], true);
					}
				}
					break;
				case Tango_DEV_UCHAR: {
					short[] dummy = data.extractUCharArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Short.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Short.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_SHORT: {
					short[] dummy = data.extractShortArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Short.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Short.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_BOOLEAN: {
					boolean[] dummy = data.extractBooleanArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Boolean.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Boolean.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_USHORT: {
					int[] dummy = data.extractUShortArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Integer.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Integer.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_LONG: {
					int[] dummy = data.extractLongArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Integer.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Integer.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_ULONG: {
					long[] dummy = data.extractULongArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Long.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Long.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_LONG64: {
					long[] dummy = data.extractLong64Array();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Long.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Long.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_ULONG64: {
					long[] dummy = data.extractULong64Array();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Long.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Long.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_DOUBLE: {
					double[] dummy = data.extractDoubleArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Double.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Double.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_FLOAT: {
					float[] dummy = data.extractFloatArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, Float.toString(dummy[i]), false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, Float.toString(dummy[i + nbRead]), true);
					}
				}
					break;
				case Tango_DEV_STRING: {
					String[] dummy = data.extractStringArray();
					int nbRead = data.getNbRead();
					int nbWritten = dummy.length - nbRead;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++)
						printArrayItem(ret_string, i, printIndex, dummy[i], false);
					if (isWritable(ai)) {
						start = getLimitMin(checkLimit, ret_string, nbWritten);
						end = getLimitMax(checkLimit, ret_string, nbWritten, true);
						for (int i = start; i < end; i++)
							printArrayItem(ret_string, i, printIndex, dummy[i + nbRead], true);
					}
				}
					break;
				case Tango_DEV_ENCODED: {
					printIndex = true;
					DevEncoded e = data.extractDevEncoded();
					ret_string.append("Format: " + e.encoded_format + "\n");
					int nbRead = e.encoded_data.length;
					int start = getLimitMin(checkLimit, ret_string, nbRead);
					int end = getLimitMax(checkLimit, ret_string, nbRead, false);
					for (int i = start; i < end; i++) {
						short vs = (short) e.encoded_data[i];
						vs = (short) (vs & 0xFF);
						printArrayItem(ret_string, i, printIndex, Short.toString(vs), false);
					}
				}
					break;
				default:
					ret_string.append("Unsupported attribute type code=" + ai.data_type + "\n");
					break;
			}
		} catch (DevFailed e) {
			// ErrorPane.showErrorMessage(this,device.name() + "/" + ai.name,e);
		}
		return ret_string.toString();
	}

	/**
	 * Parses string to be printed
	 * 
	 * @param str
	 *           String to be printed.
	 * @param idx
	 *           Number of value in array.
	 * @param printIdx
	 *           Defines if array has more than one value.
	 * @param value
	 *           Value that was read/written.
	 * @param writeable
	 *           Defines if value is writable.
	 */
	private void printArrayItem(StringBuffer str, int idx, boolean printIdx, String value, boolean writeable) {
		if (!writeable) {
			if (printIdx)
				str.append("Read [" + idx + "]\t" + value + "\n");
			else
				str.append("Read:\t" + value + "\n");
		} else {
			if (printIdx)
				str.append("Set [" + idx + "]\t" + value + "\n");
			else
				str.append("Set:\t" + value + "\n");
		}
	}

	/**
	 * Check maximum length of response.
	 * 
	 * @param checkLimit
	 * @param retStr
	 *           Response string.
	 * @param length
	 *           Length of current response.
	 * @param writable
	 *           Defines if value is writable.
	 * @return Maximum response length.
	 */
	private int getLimitMax(boolean checkLimit, StringBuffer retStr, int length, boolean writable) {
		if (length < 100) {
			return length;
		}
		return 100;
	}

	/**
	 * Check minimum length of response.
	 * 
	 * @param checkLimit
	 * @param retStr
	 *           Response string.
	 * @param length
	 *           Length of current response.
	 * @return Minimum response length.
	 */
	private int getLimitMin(boolean checkLimit, StringBuffer retStr, int length) {
		return 0;
	}

	/**
	 * Adds value to DeviceAttribute.
	 * 
	 * @param argin
	 *           Value to be added.
	 * @param send
	 *           Value will be added to this DeviceAttribute.
	 * @param ai
	 *           Define data format.
	 * @return DeviceAttribute with new value.
	 * @throws NumberFormatException
	 */
	private DeviceAttribute insertData(String argin, DeviceAttribute send, AttributeInfo ai)
			throws NumberFormatException {
		ArgParser arg = new ArgParser(argin);
		switch (ai.data_type) {
			case Tango_DEV_UCHAR:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert_uc(arg.parse_uchar());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert_uc(arg.parse_uchar_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert_uc(arg.parse_uchar_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_BOOLEAN:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert(arg.parse_boolean());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert(arg.parse_boolean_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert(arg.parse_boolean_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_SHORT:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert(arg.parse_short());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert(arg.parse_short_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert(arg.parse_short_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_USHORT:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert_us(arg.parse_ushort());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert_us(arg.parse_ushort_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert_us(arg.parse_ushort_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_LONG:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert(arg.parse_long());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert(arg.parse_long_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert(arg.parse_long_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_ULONG:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert_ul(arg.parse_ulong());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert_ul(arg.parse_ulong_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert_ul(arg.parse_ulong_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_LONG64:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert(arg.parse_long64());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert(arg.parse_long64_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert(arg.parse_long64_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_ULONG64:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert_u64(arg.parse_long64());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert_u64(arg.parse_long64_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert_u64(arg.parse_long64_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_FLOAT:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert(arg.parse_float());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert(arg.parse_float_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert(arg.parse_float_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_DOUBLE:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert(arg.parse_double());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert(arg.parse_double_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert(arg.parse_double_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			case Tango_DEV_STRING:
				switch (ai.data_format.value()) {
					case AttrDataFormat._SCALAR:
						send.insert(arg.parse_string());
						break;
					case AttrDataFormat._SPECTRUM:
						send.insert(arg.parse_string_array());
						break;
					case AttrDataFormat._IMAGE:
						send.insert(arg.parse_string_image(), arg.get_image_width(), arg.get_image_height());
						break;
				}
				break;
			default:
				throw new NumberFormatException("Attribute type not supported code=" + ai.data_type);
		}
		return send;
	}

	/**
	 * Adds value to DeviceData.
	 * 
	 * @param argin
	 *           Value to be added.
	 * @param send
	 *           Value will be added to this DeviceData.
	 * @param outType
	 *           Identifier of data type.
	 * @return DeviceData with new value.
	 * @throws NumberFormatException
	 */
	private DeviceData insertData(String argin, DeviceData send, int outType) throws NumberFormatException {
		if (outType == Tango_DEV_VOID)
			return send;
		ArgParser arg = new ArgParser(argin);
		switch (outType) {
			case Tango_DEV_BOOLEAN:
				send.insert(arg.parse_boolean());
				break;
			case Tango_DEV_USHORT:
				send.insert_us(arg.parse_ushort());
				break;
			case Tango_DEV_SHORT:
				send.insert(arg.parse_short());
				break;
			case Tango_DEV_ULONG:
				send.insert_ul(arg.parse_ulong());
				break;
			case Tango_DEV_LONG:
				send.insert(arg.parse_long());
				break;
			case Tango_DEV_FLOAT:
				send.insert(arg.parse_float());
				break;
			case Tango_DEV_DOUBLE:
				send.insert(arg.parse_double());
				break;
			case Tango_DEV_STRING:
				send.insert(arg.parse_string());
				break;
			case Tango_DEVVAR_CHARARRAY:
				send.insert(arg.parse_char_array());
				break;
			case Tango_DEVVAR_USHORTARRAY:
				send.insert_us(arg.parse_ushort_array());
				break;
			case Tango_DEVVAR_SHORTARRAY:
				send.insert(arg.parse_short_array());
				break;
			case Tango_DEVVAR_ULONGARRAY:
				send.insert_ul(arg.parse_ulong_array());
				break;
			case Tango_DEVVAR_LONGARRAY:
				send.insert(arg.parse_long_array());
				break;
			case Tango_DEVVAR_FLOATARRAY:
				send.insert(arg.parse_float_array());
				break;
			case Tango_DEVVAR_DOUBLEARRAY:
				send.insert(arg.parse_double_array());
				break;
			case Tango_DEVVAR_STRINGARRAY:
				send.insert(arg.parse_string_array());
				break;
			case Tango_DEVVAR_LONGSTRINGARRAY:
				send.insert(new DevVarLongStringArray(arg.parse_long_array(), arg.parse_string_array()));
				break;
			case Tango_DEVVAR_DOUBLESTRINGARRAY:
				send.insert(new DevVarDoubleStringArray(arg.parse_double_array(), arg.parse_string_array()));
				break;
			case Tango_DEV_STATE:
				send.insert(DevState.from_int(arg.parse_ushort()));
				break;
			default:
				throw new NumberFormatException("Command type not supported code=" + outType);
		}
		return send;
	}

	/**
	 * Extract data from DeviceData to one dimensional array.
	 * 
	 * @param data
	 *           DeviceData to extract data from.
	 * @param outType
	 *           Identifier of data type.
	 * @return Array of data that can be plotted.
	 */
	private double[] extractPlotData(DeviceData data, int outType) {
		double[] ret = new double[0];
		int i;
		switch (outType) {
			case Tango_DEVVAR_CHARARRAY: {
				byte[] dummy = data.extractByteArray();
				int start = this.getLimitMinForPlot(dummy.length);
				int end = this.getLimitMaxForPlot(dummy.length);
				ret = new double[end - start];
				for (i = start; i < end; i++)
					ret[i - start] = (double) dummy[i];
			}
				break;
			case Tango_DEVVAR_USHORTARRAY: {
				int[] dummy = data.extractUShortArray();
				int start = this.getLimitMinForPlot(dummy.length);
				int end = this.getLimitMaxForPlot(dummy.length);
				ret = new double[end - start];
				for (i = start; i < end; i++)
					ret[i - start] = (double) dummy[i];
			}
				break;
			case Tango_DEVVAR_SHORTARRAY: {
				short[] dummy = data.extractShortArray();
				int start = this.getLimitMinForPlot(dummy.length);
				int end = this.getLimitMaxForPlot(dummy.length);
				ret = new double[end - start];
				for (i = start; i < end; i++)
					ret[i - start] = (double) dummy[i];
			}
				break;
			case Tango_DEVVAR_ULONGARRAY: {
				long[] dummy = data.extractULongArray();
				int start = this.getLimitMinForPlot(dummy.length);
				int end = this.getLimitMaxForPlot(dummy.length);
				ret = new double[end - start];
				for (i = start; i < end; i++)
					ret[i - start] = (double) dummy[i];
			}
				break;
			case Tango_DEVVAR_LONGARRAY: {
				int[] dummy = data.extractLongArray();
				int start = this.getLimitMinForPlot(dummy.length);
				int end = this.getLimitMaxForPlot(dummy.length);
				ret = new double[end - start];
				for (i = start; i < end; i++)
					ret[i - start] = (double) dummy[i];
			}
				break;
			case Tango_DEVVAR_FLOATARRAY: {
				float[] dummy = data.extractFloatArray();
				int start = this.getLimitMinForPlot(dummy.length);
				int end = this.getLimitMaxForPlot(dummy.length);
				ret = new double[end - start];
				for (i = start; i < end; i++)
					ret[i - start] = (double) dummy[i];
			}
				break;
			case Tango_DEVVAR_DOUBLEARRAY: {
				double dummy[] = data.extractDoubleArray();
				int start = this.getLimitMinForPlot(dummy.length);
				int end = this.getLimitMaxForPlot(dummy.length);
				ret = new double[end - start];
				for (i = start; i < end; i++)
					ret[i - start] = dummy[i];
			}
				break;
		}
		return ret;
	}

	/**
	 * Extract data from DeviceAttribute to one dimensional array.
	 * 
	 * @param data
	 *           DeviceAttribute to extract data from.
	 * @param ai
	 *           Define data format.
	 * @return Array of data that can be plotted.
	 */
	private double[] extractSpectrumPlotData(DeviceAttribute data, AttributeInfo ai) {
		double[] ret = new double[0];
		int i;
		try {
			int start = getLimitMinForPlot(data.getNbRead());
			int end = getLimitMaxForPlot(data.getNbRead());
			System.out.println("Start: " + start);
			System.out.println("End:   " + end);
			switch (ai.data_type) {
				case Tango_DEV_UCHAR: {
					short[] dummy = data.extractUCharArray();
					ret = new double[end - start];
					for (i = start; i < end; i++)
						ret[i - start] = (double) dummy[i];
				}
					break;
				case Tango_DEV_SHORT: {
					short[] dummy = data.extractShortArray();
					ret = new double[end - start];
					for (i = start; i < end; i++)
						ret[i - start] = (double) dummy[i];
				}
					break;
				case Tango_DEV_USHORT: {
					int[] dummy = data.extractUShortArray();
					ret = new double[end - start];
					for (i = start; i < end; i++)
						ret[i - start] = (double) dummy[i];
				}
					break;
				case Tango_DEV_LONG: {
					int[] dummy = data.extractLongArray();
					ret = new double[end - start];
					for (i = start; i < end; i++)
						ret[i - start] = (double) dummy[i];
				}
					break;
				case Tango_DEV_DOUBLE: {
					double[] dummy = data.extractDoubleArray();
					ret = new double[end - start];
					for (i = start; i < end; i++)
						ret[i - start] = dummy[i];
				}
					break;
				case Tango_DEV_FLOAT: {
					float[] dummy = data.extractFloatArray();
					ret = new double[end - start];
					for (i = start; i < end; i++)
						ret[i - start] = (double) dummy[i];
				}
					break;
				case Tango_DEV_LONG64: {
					long[] dummy = data.extractLong64Array();
					ret = new double[end - start];
					for (i = start; i < end; i++)
						ret[i - start] = (double) dummy[i];
				}
					break;
				case Tango_DEV_ULONG64: {
					long[] dummy = data.extractULong64Array();
					ret = new double[end - start];
					for (i = start; i < end; i++)
						ret[i - start] = (double) dummy[i];
				}
					break;
				case Tango_DEV_ULONG: {
					long[] dummy = data.extractULongArray();
					ret = new double[end - start];
					for (i = start; i < end; i++)
						ret[i - start] = (double) dummy[i];
				}
					break;
			}
		} catch (DevFailed e) {
			// ErrorPane.showErrorMessage(this, device.name() + "/" + ai.name, e);
		}
		return ret;
	}

	/**
	 * Extract data from DeviceAttribute to two dimensional array.
	 * 
	 * @param data
	 *           DeviceAttribute to extract data from.
	 * @param ai
	 *           Define data format.
	 * @return Array of data that can be plotted.
	 */
	private double[][] extractImagePlotData(DeviceAttribute data, AttributeInfo ai) {
		double[][] ret = new double[0][0];
		int i, j, k, dimx, dimy;
		try {
			dimx = data.getDimX();
			dimy = data.getDimY();
			switch (ai.data_type) {
				case Tango_DEV_UCHAR: {
					short[] dummy = data.extractUCharArray();
					ret = new double[dimy][dimx];
					for (j = 0, k = 0; j < dimy; j++)
						for (i = 0; i < dimx; i++)
							ret[j][i] = (double) dummy[k++];
				}
					break;
				case Tango_DEV_SHORT: {
					short[] dummy = data.extractShortArray();
					ret = new double[dimy][dimx];
					for (j = 0, k = 0; j < dimy; j++)
						for (i = 0; i < dimx; i++)
							ret[j][i] = (double) dummy[k++];
				}
					break;
				case Tango_DEV_USHORT: {
					int[] dummy = data.extractUShortArray();
					ret = new double[dimy][dimx];
					for (j = 0, k = 0; j < dimy; j++)
						for (i = 0; i < dimx; i++)
							ret[j][i] = (double) dummy[k++];
				}
					break;
				case Tango_DEV_LONG: {
					int[] dummy = data.extractLongArray();
					ret = new double[dimy][dimx];
					for (j = 0, k = 0; j < dimy; j++)
						for (i = 0; i < dimx; i++)
							ret[j][i] = (double) dummy[k++];
				}
					break;
				case Tango_DEV_DOUBLE: {
					double[] dummy = data.extractDoubleArray();
					ret = new double[dimy][dimx];
					for (j = 0, k = 0; j < dimy; j++)
						for (i = 0; i < dimx; i++)
							ret[j][i] = dummy[k++];
				}
					break;
				case Tango_DEV_FLOAT: {
					float[] dummy = data.extractFloatArray();
					ret = new double[dimy][dimx];
					for (j = 0, k = 0; j < dimy; j++)
						for (i = 0; i < dimx; i++)
							ret[j][i] = (double) dummy[k++];
				}
					break;
			}
		} catch (DevFailed e) {
			// ErrorPane.showErrorMessage(this, device.name() + "/" + ai.name, e);
		}
		return ret;
	}

	/**
	 * Check maximum length of data.
	 * 
	 * @param length
	 *           Length of current data.
	 * @return Maximum length.
	 */
	private int getLimitMaxForPlot(int length) {
		if (length < 100) {
			return length;
		}
		return 100;
	}

	/**
	 * Check minimum length of data.
	 * 
	 * @param length
	 *           Length of current data.
	 * @return Minimum length.
	 */
	private int getLimitMinForPlot(int length) {
		return 0;
	}

	/**
	 * Check if attribute can be written with new value.
	 * 
	 * @param ai
	 *           Attribute to be checked.
	 * @return True when attribute can be written.
	 */
	private boolean isWritable(AttributeInfo ai) {
		return (ai.writable.value() == AttrWriteType._READ_WITH_WRITE)
				|| (ai.writable.value() == AttrWriteType._READ_WRITE) || (ai.writable.value() == AttrWriteType._WRITE);
	}

	/**
	 * Check if data can be plotted.
	 * 
	 * @param outType
	 *           Identifier of data type.
	 * @return True if plotable.
	 */
	private boolean isPlotable(int outType) {
		switch (outType) {
			case Tango_DEVVAR_CHARARRAY:
			case Tango_DEVVAR_USHORTARRAY:
			case Tango_DEVVAR_SHORTARRAY:
			case Tango_DEVVAR_ULONGARRAY:
			case Tango_DEVVAR_LONGARRAY:
			case Tango_DEVVAR_FLOATARRAY:
			case Tango_DEVVAR_DOUBLEARRAY:
				return true;
		}
		return false;
	}

	/**
	 * Check if attribute could be plotted.
	 * 
	 * @param ai
	 *           Attribute to be checked.
	 * @return True when attribute can be plotted.
	 */
	private boolean isPlotable(AttributeInfo ai) {
		if ((ai.data_type == Tango_DEV_STRING) || (ai.data_type == Tango_DEV_STATE)
				|| (ai.data_type == Tango_DEV_BOOLEAN))
			return false;
		return (ai.data_format.value() == AttrDataFormat._SPECTRUM) || (ai.data_format.value() == AttrDataFormat._IMAGE);
	}

	private void getHostFromEnv() {
		/*String envName = "TANGO_HOST";
		String value = System.getenv(envName);
		if (value != null) {
			System.out.format("%s=%s%n", envName, value);
			String[] tangoHost = value.split(":");
			if (tangoHost.length == 2) {
				host = tangoHost[0];
				port = tangoHost[1];
				System.out.println("Using new host value: " + host + ":" + port);
			} else {
				System.out.println(envName + " not set correctly, should be in form: \"hostname:port\"");
			}
		} else {
			System.out.format("%s not set. Using previously set host: %s:%s%n", envName, host, port);
		
		}*/
	}
}