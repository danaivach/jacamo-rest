package jacamo.rest;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import jacamo.infra.JaCaMoLauncher;
import jason.JasonException;

public final class TestUtils {
    
    public static URI launchSystem() {
        URI uri = null;
        try {
            // Wait for JaCaMo be finished by other tests
            while (JaCaMoLauncher.getRunner() != null) {
                Thread.sleep(400);
            }

            // Launch jacamo and jacamo-rest running test0.jcm
            new Thread() {
                public void run() {
                    String[] arg = { "src/test/test0.jcm" };
                    try {
                        JaCaMoLauncher.main(arg);
                    } catch (JasonException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
            
            // wait for start of jacamo rest
            while (uri == null) {
                System.out.println("waiting for jacamo to start ....");
                if (JCMRest.getRestHost() != null)
                    uri = UriBuilder.fromUri(JCMRest.getRestHost()).build();
                else
                    Thread.sleep(400);
            }
            // wait for agents
            while (JaCaMoLauncher.getRunner().getNbAgents() == 0) {
                System.out.println("waiting for agents to start...");
                Thread.sleep(200);
            }
            Thread.sleep(600);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }
    
}
