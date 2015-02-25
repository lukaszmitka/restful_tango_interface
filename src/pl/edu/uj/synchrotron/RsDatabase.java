package pl.edu.uj.synchrotron;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.ApiUtil;
import fr.esrf.TangoApi.Database;

// Plain old Java Object it does not extend as class or implements an interface
// The class registers its methods for the HTTP GET request using the @GET annotation.
// Using the @Produces annotation, it defines that it can deliver several MIME types,
// text, XML and HTML.
// The browser requests per default the HTML MIME type.
// Sets the path to base URL + /Database
@Path("/Database")
public class RsDatabase {
	String	devices[];
	String	host	= "192.168.106.101";
	String	port	= "10000";

	/*
	 * // This method is called if TEXT_PLAIN is request
	 * 
	 * @GET
	 * 
	 * @Produces(MediaType.TEXT_PLAIN) public String sayPlainTextHello() {
	 * return "This is plain text device list."; }
	 * 
	 * // This method is called if XML is request
	 * 
	 * @GET
	 * 
	 * @Produces(MediaType.TEXT_XML) public String sayXMLHello() { return
	 * "<?xml version=\"1.0\"?>" + "<hello> This is XML device list." +
	 * "</hello>"; }
	 */
	// This method is called if HTML is request
	@GET
	@Produces(MediaType.TEXT_HTML)
	public String databaseInfo() {
		String retStr = "<html><title>Database</title><body><h1>Database info:</h1>";
		if (System.getenv("TANGO_HOST") == null) {
			retStr = retStr + "Variable TANGO_HOST is undefined" + "<br>";
			System.out.println("Variable TANGO_HOST is undefined");
		} else {
			retStr = retStr + "TANGO_HOST=" + System.getenv("TANGO_HOST") + "<br>";
			System.out.println("TANGO_HOST=" + System.getenv("TANGO_HOST"));
		}
		return retStr + "</body>" + "</html> ";
	}

	@GET
	@Path("/{method}")
	@Produces(MediaType.TEXT_HTML)
	public String getDeviceList(@PathParam("method") String method) {
		String retStr = "<html> " + "<title>" + "Database" + "</title>" + "<body><h1>Device list:</h1>";
		try {
			System.out.println("Connecting to database");
			Database db = ApiUtil.get_db_obj(host, port);
			retStr = retStr + "Database connected at: " + host + ":" + port + "<br><br>";
			if (method.equals("get_device_list")) {
				System.out.println("Getting data from database");
				devices = db.get_device_list("*");
				for (int i = 0; i < devices.length; i++) {
					retStr = retStr + devices[i] + "<br>";
				}
				retStr = retStr + "</body>" + "</html>";
			} else {
				retStr = retStr + "Unknown command</body>" + "</html>";
			}
		} catch (DevFailed e) {
			retStr = retStr + "Unable to connect to database";
			e.printStackTrace();
		}
		return retStr;
	}
}