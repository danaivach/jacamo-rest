package jacamo.rest;

import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;

import com.google.gson.Gson;

import jacamo.platform.DefaultPlatformImpl;
import jacamo.rest.config.RestAgArch;
import jacamo.rest.config.RestAppConfig;
import jason.runtime.RuntimeServicesFactory;


public class JCMRest extends DefaultPlatformImpl {

    public static String JaCaMoZKAgNodeId = "/jacamo/agents";
    public static String JaCaMoZKDFNodeId = "/jacamo/df";
    public static String JaCaMoZKMDNodeId = "metadata";

    private static JCMRest singleton = null;
    public  static JCMRest getJCMRest() {
        return singleton;
    }

    protected HttpServer restHttpServer = null;
    protected URI restServerURI = null;
    protected String mainRest = null;
    
    protected Map<String, Map<String,Object>> ans = new TreeMap<String, Map<String,Object>>();

    
    public String getRestHost() {
        if (restServerURI == null)
            return null;
        else
            return restServerURI.toString();
    }
    
    public boolean isMain() {
        return mainRest == null;
    }
    public String getMainRest() {
        return mainRest;
    }

    @Override
    public void init(String[] args) throws Exception {
        
        // change the runtimeservices
        RuntimeServicesFactory.set( new JCMRuntimeServices(this) );
        
        // adds RestAgArch as default ag arch when using this platform
        RuntimeServicesFactory.get().registerDefaultAgArch(RestAgArch.class.getName());

        int restPort = 3280;

        // Used when deploying on heroku
        String webPort = System.getenv("PORT");
        if (webPort == null || webPort.isEmpty()) {
            restPort = 8080;
        } else {
            restPort = Integer.parseInt(webPort);
        }

        if (args.length > 0) {
            String la = "";
            for (String a: args[0].split(" ")) {
                if (la.equals("--restPort"))
                    try {
                        restPort = Integer.parseInt(a);
                    } catch (Exception e) {
                        System.err.println("The argument for restPort is not a number.");
                    }

                if (la.equals("--connect")) {
                    mainRest = a;
                }
                la = a;
            }
        }

        restHttpServer = startRestServer(restPort,0);
        singleton = this;
        
        System.out.println("JaCaMo Rest API is running on "+restServerURI+ (mainRest == null ? "" : ", connected to "+mainRest)  );
    }

    @Override
    public void stop() {
        System.out.println("Stopping jacamo-rest...");

        System.out.println("Stopping http server...");
        if (restHttpServer != null)
            try {
                restHttpServer.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        restHttpServer = null;
        System.out.println("Http server stopped!");
    }

    public HttpServer startRestServer(int port, int tryc) {
        if (tryc > 20) {
            System.err.println("Error starting rest server!");
            return null;
        }
        try {
            restServerURI = UriBuilder.fromUri("http://"+InetAddress.getLocalHost().getHostAddress()+"/").port(port).build();

            RestAppConfig rc = new RestAppConfig();

            // get a server from factory
            HttpServer s = GrizzlyHttpServerFactory.createHttpServer(restServerURI, rc);
            return s;
        } catch (javax.ws.rs.ProcessingException e) {
            System.out.println("trying next port for rest server "+(port+1)+". e="+e);
            return startRestServer(port+1,tryc+1);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    
    //
    // ANS services
    //

    Client client = ClientBuilder.newClient();

    public void registerAgent(String agentName, Map<String,Object> metadata) {
        ans.put(agentName, metadata);
        
        if (!isMain()) {
            // register also in main
            synchronized (client) {
                metadata.put("uri", getRestHost()+"agents/"+agentName);
                metadata.put("type", "JaCaMoAgent");
                metadata.put("inbox", getRestHost()+"agents/"+agentName+"/inbox");
               
                // add new entry
                //Response response = 
                client
                    .target(mainRest)
                    .path("/agents/"+agentName)
                    .queryParam("only_wp", "true")
                    .queryParam("force", "true")
                    .request(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_PLAIN)
                    .post(Entity.json(new Gson().toJson( metadata )));               
            }
        }
    }
    
    public boolean deregisterAgent(String agentName) {
        if (!isMain()) {
            synchronized (client) {
                //Response response = 
                client
                        .target(mainRest)
                        .path("/agents/"+agentName)
                        .request()
                        .delete();
                //return response.getStatus() == 200;
            }
        }
        return ans.remove(agentName) != null;
    }
    
    @SuppressWarnings("unchecked")
    public Map<String,Object> getAgentMetaData(String agentName) {
        if (ans.get(agentName) != null)
            return ans.get(agentName);
        if (!isMain()) {
            // TODO: implement some cache
            synchronized (client) {
                Response response = client
                        .target(mainRest)
                        .path("/agents/"+agentName)
                        .request(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .get();
                if (response.getStatus() == 200) {
                    return response.readEntity(Map.class); 
                }
            }
        }
        return null;
    }
    
    public Map<String,Map<String,Object>> getWP() throws Exception {
        Map<String,Map<String,Object>> data = new HashMap<>();
        for (String ag : ans.keySet()) {
            Map<String,Object> md = ans.getOrDefault(ag, new HashMap<>());
            data.put(ag, md);
        }
        return data;
    }
}
