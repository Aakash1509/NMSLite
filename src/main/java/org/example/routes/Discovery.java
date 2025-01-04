package org.example.routes;

import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.example.Constants;
import org.example.Main;
import org.example.database.QueryUtility;
import org.example.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.Future;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import static org.example.Main.*;

public class Discovery implements CrudOperations
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Discovery.class);

    private static final WorkerExecutor discover = Main.vertx.createSharedWorkerExecutor("discovery",10,60, TimeUnit.SECONDS);

    public void route(Router discoveryRouter)
    {
        try
        {
            discoveryRouter.post("/").handler(this::create);

            discoveryRouter.put("/:id").handler(this::update);

            discoveryRouter.get("/").handler(this::getAll);

            discoveryRouter.get("/:id").handler(this::get);

            discoveryRouter.delete("/:id").handler(this::delete);

            discoveryRouter.post("/:id/run").handler(this::discover);
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in discovery routing", exception);
        }
    }

    @Override
    public void create(RoutingContext context)
    {
        try
        {
            var requestBody = context.body().asJsonObject();

            var validation = Helper.validateDiscovery(requestBody);

            if(!validation.isEmpty())
            {
                context.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE,500)
                                .put(Constants.MESSAGE,validation).encodePrettily());

                return;
            }

            QueryUtility.getInstance().insert(Constants.DISCOVERIES,new JsonObject()
                            .put("name",requestBody.getString("discovery.name"))
                            .put("ip",requestBody.getString("discovery.ip"))
                            .put("port",requestBody.getInteger("discovery.port"))
                            .put("credential_profiles",requestBody.getJsonArray("discovery.credential.profiles"))
                            .put("device_type",requestBody.getString("device.type"))
                            .put("status","Down"))
                    .onComplete(result->
                    {
                        if(result.succeeded())
                        {
                            Helper.insertInMap(discoveries,result.result(),new JsonObject()
                                    .put("discovery_id",result.result())
                                    .put("credential_profile",null)
                                    .put("name",requestBody.getString("discovery.name"))
                                    .put("ip",requestBody.getString("discovery.ip"))
                                    .put("port",requestBody.getInteger("discovery.port"))
                                    .put("credential_profiles",requestBody.getJsonArray("discovery.credential.profiles"))
                                    .put("device_type",requestBody.getString("device.type"))
                                    .put("hostname",null)
                                    .put("status","Down"));

                            context.response()
                                    .setStatusCode(201)
                                    .end(new JsonObject()
                                            .put(Constants.STATUS_CODE,201).put(Constants.MESSAGE,"Discovery created successfully")
                                            .put(Constants.CONTEXT,new JsonObject().put("discovery.id", result.result())).encodePrettily());
                        }
                        else
                        {
                            context.response()
                                    .setStatusCode(500)
                                    .end(new JsonObject()
                                            .put(Constants.STATUS_CODE,500)
                                            .put(Constants.MESSAGE,"Failed to create discovery")
                                            .put(Constants.ERROR,result.cause().getMessage()).encodePrettily());
                        }
                    });
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE,500)
                            .put(Constants.MESSAGE,"Server error in creating discovery")
                            .put(Constants.ERROR,exception.getCause().getMessage()).encodePrettily());
        }
    }

    @Override
    public void update(RoutingContext context)
    {
        var discoveryID = context.pathParam("id");

        var requestBody = context.body().asJsonObject();

        try
        {
            var id = Long.parseLong(discoveryID);

            QueryUtility.getInstance().update(Constants.DISCOVERIES, requestBody, new JsonObject().put("discovery_id", id))
                    .onComplete(result->
                    {
                        if(result.succeeded())
                        {
                            var existing = discoveries.get(id);

                            requestBody.forEach(entry->existing.put(entry.getKey(),entry.getValue()));

                            discoveries.put(id,existing);

                            context.response()
                                    .setStatusCode(200)
                                    .end(new JsonObject()
                                            .put(Constants.STATUS_CODE,200)
                                            .put(Constants.MESSAGE,"Discovery updated successfully").encodePrettily());
                        }
                        else
                        {
                            if (result.cause().getMessage().contains("No matching rows found"))
                            {
                                context.response()
                                        .setStatusCode(404)
                                        .end(new JsonObject()
                                                .put(Constants.STATUS_CODE,404)
                                                .put(Constants.MESSAGE,"Discovery not found of this ID").encodePrettily());
                            }
                            else
                            {
                                context.response()
                                        .setStatusCode(500)
                                        .end(new JsonObject()
                                                .put(Constants.STATUS_CODE,500)
                                                .put(Constants.MESSAGE,"Database error while updating discovery")
                                                .put(Constants.ERROR,result.cause().getMessage()).encodePrettily());
                            }
                        }
                    });
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE,500)
                            .put(Constants.MESSAGE,"Server error in updating discovery")
                            .put(Constants.ERROR,exception.getCause().getMessage()).encodePrettily());
        }
    }

    @Override
    public void delete(RoutingContext context)
    {
        var discoveryID = context.pathParam("id");

        if (Helper.validateField(discoveryID))
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE, 404)
                            .put(Constants.MESSAGE, "Please enter a valid discovery ID").encodePrettily());
            return;
        }
        try
        {
            var id = Long.parseLong(discoveryID);

            QueryUtility.getInstance().delete(Constants.DISCOVERIES,"discovery_id",id)
                    .onComplete(result ->
                    {
                        if (result.succeeded())
                        {
                            discoveries.remove(id);

                            context.response()
                                    .setStatusCode(200)
                                    .end(new JsonObject()
                                            .put(Constants.STATUS_CODE, 200)
                                            .put(Constants.MESSAGE, "Discovery deleted successfully").encodePrettily());
                        }
                        else
                        {
                            if (result.cause().getMessage().contains("Information not found"))
                            {
                                context.response()
                                        .setStatusCode(404)
                                        .end(new JsonObject()
                                                .put(Constants.STATUS_CODE, 404)
                                                .put(Constants.MESSAGE, "Discovery not found for this ID").encodePrettily());
                            }
                            else
                            {
                                context.response()
                                        .setStatusCode(500)
                                        .end(new JsonObject()
                                                .put(Constants.STATUS_CODE, 500)
                                                .put(Constants.MESSAGE, "Database error while deleting discovery")
                                                .put(Constants.ERROR, result.cause().getMessage()).encodePrettily());
                            }
                        }
                    });
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE, 500)
                            .put(Constants.MESSAGE, "Server error in deleting discovery")
                            .put(Constants.ERROR, "Please enter a valid discovery ID").encodePrettily());
        }
    }

    @Override
    public void get(RoutingContext context)
    {
        var discoveryID = context.pathParam("id");

        if (Helper.validateField(discoveryID))
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE, 404)
                            .put(Constants.MESSAGE, "Please enter a valid discovery ID").encodePrettily());
            return;
        }
        try
        {
            var id = Long.parseLong(discoveryID);

            if(discoveries.containsKey(id))
            {
                context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 200)
                                .put(Constants.MESSAGE, "Discovery fetched successfully")
                                .put(Constants.CONTEXT, discoveries.get(id)).encodePrettily());
            }
            else
            {
                context.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 404)
                                .put(Constants.MESSAGE, "Discovery not found for this ID").encodePrettily());
            }
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE, 500)
                            .put(Constants.MESSAGE, "Server error in fetching discovery")
                            .put(Constants.ERROR, "Please enter a valid discovery ID").encodePrettily());
        }
    }

    @Override
    public void getAll(RoutingContext context)
    {
        try
        {
            if (!discoveries.isEmpty())
            {
                var records = new JsonArray();

                discoveries.forEach((id, profile) -> records.add(profile));

                context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 200)
                                .put(Constants.MESSAGE, "Discoveries fetched successfully")
                                .put(Constants.CONTEXT, records).encodePrettily());
            }
            else
            {
                context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 200)
                                .put(Constants.MESSAGE, "Discoveries fetched successfully")
                                .put(Constants.CONTEXT, "No discovery currently").encodePrettily());
            }
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE,500)
                            .put(Constants.MESSAGE,"Server error in fetching discoveries")
                            .put(Constants.ERROR,exception.getCause().getMessage()).encodePrettily());
        }
    }

    private void discover(RoutingContext context)
    {
        var discoveryID = context.pathParam("id");

        if (Helper.validateField(discoveryID))
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE, 404)
                            .put(Constants.MESSAGE, "Please enter a valid discovery ID").encodePrettily());
            return;
        }
        try
        {
            var id = Long.parseLong(discoveryID);

            //Discovery ID must be present
            if(!discoveries.containsKey(id))
            {
                context.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 404)
                                .put(Constants.MESSAGE, "Discovery not found for this ID").encodePrettily());

                return;
            }

            var deviceInfo = discoveries.get(id);

            //If device is already provisioned, no need to go further
            if(Helper.isProvisioned(deviceInfo.getString("ip")))
            {
                context.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 404)
                                .put(Constants.MESSAGE, "Device is already provisioned").encodePrettily());

                return;
            }

            var records = new JsonArray();

            for(int i=0;i<deviceInfo.getJsonArray("credential_profiles").size();i++)
            {
                var profileID = deviceInfo.getJsonArray("credential_profiles").getLong(i);

                records.add(credentials.get(profileID));
            }

            deviceInfo.put("discovery.credential.profiles",records);

            ping(deviceInfo.getString("ip"))
                    .compose(result->
                    {
                        if(result)
                        {
                            return Future.succeededFuture(deviceInfo);
                        }
                        else
                        {
                            return Future.failedFuture("Device is down, ping failed");
                        }
                    })
                    .compose(result->
                    {
                        if(result.getInteger("port")==161 || isPortOpen(result.getString("ip"),result.getInteger("port")))
                        {
                            return Future.succeededFuture(result);
                        }
                        else
                        {
                            return Future.failedFuture("Ping done but port is closed for the specified connection");
                        }
                    })
                    .compose(result->
                    {
                        try
                        {
                            return validCredential(result);
                        }
                        catch (Exception exception)
                        {
                            return Future.failedFuture("Error during finding valid credential profile " + exception.getMessage());
                        }
                    })
                    .compose(result->
                    {
                        try
                        {
                            // Update the status in the database after removing credential profiles
                            result.remove("discovery.credential.profiles");

                            return QueryUtility.getInstance().update(Constants.DISCOVERIES,result,new JsonObject().put("discovery_id",id))
                                    .compose(updateResult ->
                                    {
                                        if (updateResult)
                                        {
                                            discoveries.put(id,result);

                                            return Future.succeededFuture("Device status updated in database");
                                        }
                                        else
                                        {
                                            return Future.failedFuture("Failed to update the database for the discoveryID");
                                        }
                                    });
                        }
                        catch (Exception exception)
                        {
                            return Future.failedFuture("Error during updating status of device in database " + exception.getMessage());
                        }
                    })
                    .onSuccess(finalResult -> context.response().setStatusCode(200).end(new JsonObject().put(Constants.STATUS_CODE,200).put(Constants.MESSAGE,finalResult).encodePrettily()))

                    .onFailure(error -> context.response().setStatusCode(400).end(new JsonObject().put(Constants.STATUS_CODE,400).put(Constants.MESSAGE,error.getMessage()).encodePrettily()));
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE,500)
                            .put(Constants.MESSAGE,"Server error in checking if the device is up or not")
                            .put(Constants.ERROR,exception.getCause().getMessage()).encodePrettily());
        }
    }

    private Future<JsonObject> validCredential(JsonObject deviceInfo)
    {
        return discover.executeBlocking(promise ->
        {
            try
            {
                Helper.checkConnection(deviceInfo);

                deviceInfo.remove(Constants.EVENT_TYPE);

                promise.complete(deviceInfo);
            }
            catch (Exception exception)
            {
                promise.fail("Error during finding valid credential profile: " + exception.getMessage());
            }
        });
    }

    private Future<Boolean> ping(String ip)
    {
        var promise = Promise.<Boolean>promise();

        discover.<Boolean>executeBlocking(pingFuture->
        {
            try
            {
                var process = new ProcessBuilder("ping","-c 5",ip).start();

                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                var down = false;

                //Due to network latency if 5 packets are not send, then waitFor
                var status = process.waitFor(5, TimeUnit.SECONDS); //Will return boolean , while exitvalue returns 0 or other value

                if(!status)
                {
                    process.destroy();

                    pingFuture.complete(false);

                    return;
                }

                for (var line = reader.readLine(); line != null; line = reader.readLine())
                {
                    if (line.contains("100% packet loss"))
                    {
                        down = true;

                        break;
                    }
                }
                //If status is true , but exit value can be 1
                if(process.exitValue()!=0 || down)
                {
                    pingFuture.complete(false);
                }
                else
                {
                    pingFuture.complete(true);
                }
            }
            catch (Exception exception)
            {
                pingFuture.fail(exception);
            }
        },false,result->
        {
            if (result.succeeded())
            {
                promise.complete(result.result());
            }
            else
            {
                promise.fail(result.cause());
            }
        });
        return promise.future();
    }

    private boolean isPortOpen(String ip,Integer port)
    {
        var socket = new Socket();

        var address = new InetSocketAddress(ip,port);

        try
        {
            socket.connect(address,2000);

            return true; //Port is open
        }
        catch (Exception exception)
        {
            return false; //Port is closed
        }
        finally
        {
            try
            {
                socket.close();
            }
            catch (Exception exception)
            {
                LOGGER.error(exception.getMessage());
            }
        }
    }
}
