package mas.rest.implementation;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.internal.inject.AbstractBinder;

import com.google.gson.Gson;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import mas.rest.mediation.TranslAg;

@Singleton
@Path("/services")
@Api(value = "/services")
public class RestImplDF extends AbstractBinder {

    TranslAg tAg = new TranslAg();

    @Override
    protected void configure() {
        bind(new RestImplDF()).to(RestImplDF.class);
    }

    /**
     * Get MAS Directory Facilitator (agents and services).
     *
     * Following the format suggested in the second example of
     * https://opensource.adobe.com/Spry/samples/data_region/JSONDataSetSample.html
     * We are providing lists of maps
     *
     * @return HTTP 200 Response (ok status) or 500 Internal Server Error in case of
     *         error (based on https://tools.ietf.org/html/rfc7231#section-6.6.1)
     *         when ok JSON of the DF Sample output (jsonifiedDF):
     *         {"marcos":{"agent":"marcos","services":["vender(banana)","iamhere"]}}
     *         Testing platform: http://json.parser.online.fr/
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get MAS Directory Facilitator (agents and services).")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "success"),
            @ApiResponse(code = 500, message = "internal error")
    })
    public Response getServices() {
        try {
            Gson gson = new Gson();

            return Response
                    .ok()
                    .entity(gson.toJson(tAg.getJsonifiedDF()))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500, e.getMessage()).build();
        }
    }
}
