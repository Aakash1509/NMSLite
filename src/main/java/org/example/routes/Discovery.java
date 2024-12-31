package org.example.routes;

import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.example.Constants;
import org.example.database.QueryUtility;
import org.example.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.Future;
import java.util.concurrent.TimeUnit;

import static org.example.Main.vertx;
import static org.example.Main.*;

public class Discovery implements CrudOperations
{
    private static final Logger logger = LoggerFactory.getLogger(Discovery.class);

    private static final WorkerExecutor executor = vertx.createSharedWorkerExecutor("discovery",5,60, TimeUnit.SECONDS);

    public void route(Router discoveryRouter)
    {
        try
        {
            discoveryRouter.post("/create").handler(this::create);

            discoveryRouter.put("/:id").handler(this::update);

            discoveryRouter.get("/").handler(this::getAll);

            discoveryRouter.get("/:id").handler(this::get);

            discoveryRouter.delete("/:id").handler(this::delete);

            discoveryRouter.post("/:id/run").handler(this::discover);
        }
        catch (Exception exception)
        {
            logger.error("Error in discovery routing", exception);
        }
    }

    @Override
    public void create(RoutingContext context)
    {
        try
        {
            var requestBody = context.body().asJsonObject();

            var name = requestBody.getString("discovery.name");

            var ip = requestBody.getString("discovery.ip");

            var port = requestBody.getInteger("discovery.port");

            var device_type = requestBody.getString("device.type");

            var credential_profiles = requestBody.getJsonArray("discovery.credential.profiles");

            if(name == null || name.isEmpty() || ip == null || ip.isEmpty() || port == null || credential_profiles == null || credential_profiles.isEmpty() || device_type == null || device_type.isEmpty())
            {
                context.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                                .put("status.code",500)
                                .put("message","Please enter required fields").encodePrettily());

                return;
            }

            if (!Helper.validIp(ip))
            {
                context.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                                .put("status.code",400)
                                .put("message","Invalid IP address provided").encodePrettily());
                return;
            }

            if (Helper.validPort(port))
            {
                context.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                                .put("status.code",400)
                                .put("message","Invalid port provided").encodePrettily());
                return;
            }

            if(Helper.isNotUnique(discoveries,name,"name"))
            {
                context.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                                .put("status.code", 400)
                                .put("message", "Discovery name should be unique").encodePrettily());
                return;
            }

            QueryUtility.getInstance().insert(Constants.DISCOVERIES,new JsonObject()
                            .put("name",name)
                            .put("ip",ip)
                            .put("port",port)
                            .put("credential_profiles",credential_profiles)
                            .put("device_type",device_type)
                            .put("status","Down"))
                    .onComplete(result->
                    {
                        if(result.succeeded())
                        {
                            Helper.insertInMap(discoveries,result.result(),new JsonObject()
                                    .put("discovery_id",result.result())
                                    .put("credential_profile",null)
                                    .put("name",name)
                                    .put("ip",ip)
                                    .put("port",port)
                                    .put("credential_profiles",credential_profiles)
                                    .put("device_type",device_type)
                                    .put("hostname",null)
                                    .put("status","Down"));

                            context.response()
                                    .setStatusCode(201)
                                    .end(new JsonObject()
                                            .put("status.code",201).put("message","Discovery created successfully")
                                            .put("data",new JsonObject().put("discovery.id", result.result())).encodePrettily());
                        }
                        else
                        {
                            context.response()
                                    .setStatusCode(500)
                                    .end(new JsonObject()
                                            .put("status.code",500)
                                            .put("message","Failed to create discovery")
                                            .put("error",result.cause().getMessage()).encodePrettily());
                        }
                    });
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code",500)
                            .put("message","Server error in creating discovery")
                            .put("error",exception.getCause().getMessage()).encodePrettily());
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
                                            .put("status.code",200)
                                            .put("message","Discovery updated successfully").encodePrettily());
                        }
                        else
                        {
                            if (result.cause().getMessage().contains("No matching rows found"))
                            {
                                context.response()
                                        .setStatusCode(404)
                                        .end(new JsonObject()
                                                .put("status.code",404)
                                                .put("message","Discovery not found of this ID").encodePrettily());
                            }
                            else
                            {
                                context.response()
                                        .setStatusCode(500)
                                        .end(new JsonObject()
                                                .put("status.code",500)
                                                .put("message","Database error while updating discovery")
                                                .put("error",result.cause().getMessage()).encodePrettily());
                            }
                        }
                    });
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code",500)
                            .put("message","Server error in updating discovery")
                            .put("error",exception.getCause().getMessage()).encodePrettily());
        }
    }

    @Override
    public void delete(RoutingContext context)
    {
        var discoveryID = context.pathParam("id");

        if (discoveryID == null || discoveryID.isEmpty())
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put("status.code", 404)
                            .put("message", "Please enter a valid discovery ID").encodePrettily());
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
                                            .put("status.code", 200)
                                            .put("message", "Discovery deleted successfully").encodePrettily());
                        }
                        else
                        {
                            if (result.cause().getMessage().contains("Information not found"))
                            {
                                context.response()
                                        .setStatusCode(404)
                                        .end(new JsonObject()
                                                .put("status.code", 404)
                                                .put("message", "Discovery not found for this ID").encodePrettily());
                            }
                            else
                            {
                                context.response()
                                        .setStatusCode(500)
                                        .end(new JsonObject()
                                                .put("status.code", 500)
                                                .put("message", "Database error while deleting discovery")
                                                .put("error", result.cause().getMessage()).encodePrettily());
                            }
                        }
                    });
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code", 500)
                            .put("message", "Server error in deleting discovery")
                            .put("error", "Please enter a valid discovery ID").encodePrettily());
        }
    }

    @Override
    public void get(RoutingContext context)
    {
        var discoveryID = context.pathParam("id");

        if (discoveryID == null || discoveryID.isEmpty())
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put("status.code", 404)
                            .put("message", "Please enter a valid discovery ID").encodePrettily());
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
                                .put("status.code", 200)
                                .put("message", "Discovery fetched successfully")
                                .put("data", discoveries.get(id)).encodePrettily());
            }
            else
            {
                context.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put("status.code", 404)
                                .put("message", "Discovery not found for this ID").encodePrettily());
            }
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code", 500)
                            .put("message", "Server error in fetching discovery")
                            .put("error", "Please enter a valid discovery ID").encodePrettily());
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
                                .put("status.code", 200)
                                .put("message", "Discoveries fetched successfully")
                                .put("data", records).encodePrettily());
            }
            else
            {
                context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                                .put("status.code", 200)
                                .put("message", "Discoveries fetched successfully")
                                .put("data", "No discovery currently").encodePrettily());
            }
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code",500)
                            .put("message","Server error in fetching discoveries")
                            .put("error",exception.getCause().getMessage()).encodePrettily());
        }
    }

    private void discover(RoutingContext context)
    {
        var discoveryID = context.pathParam("id");

        if (discoveryID == null || discoveryID.isEmpty())
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put("status.code", 404)
                            .put("message", "Please enter a valid discovery ID").encodePrettily());
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
                                .put("status.code", 404)
                                .put("message", "Discovery not found for this ID").encodePrettily());

                return;
            }

            //If device is already provisioned, no need to go further
            if(objects.containsKey(id))
            {
                context.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put("status.code", 404)
                                .put("message", "Device is already provisioned").encodePrettily());

                return;
            }

            var deviceInfo = discoveries.get(id);

            var records = new JsonArray();

            for(int i=0;i<deviceInfo.getJsonArray("credential_profiles").size();i++)
            {
                var profileID = deviceInfo.getJsonArray("credential_profiles").getLong(i);

                records.add(credentials.get(profileID));
            }

            deviceInfo.put("discovery.credential.profiles",records);

            executor.<JsonObject>executeBlocking(pingFuture->
                    {
                        try
                        {
                            if (Helper.ping(deviceInfo.getString("ip")))
                            {
                                pingFuture.complete(deviceInfo);
                            }
                            else
                            {
                                pingFuture.fail("Device is down, ping failed");
                            }
                        }
                        catch (Exception exception)
                        {
                            pingFuture.fail("Error during ping: " + exception.getMessage());
                        }
                    })
                    .compose(result->
                    {
                        if(result.getInteger("port")==161 || Helper.isPortOpen(result.getString("ip"),result.getInteger("port")))
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
                    .onSuccess(finalResult -> context.response().setStatusCode(200).end(new JsonObject().put("status.code",200).put("message",finalResult).encodePrettily()))

                    .onFailure(error -> context.response().setStatusCode(400).end(new JsonObject().put("status.code",400).put("message",error.getMessage()).encodePrettily()));
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code",500)
                            .put("message","Server error in checking if the device is up or not")
                            .put("error",exception.getCause().getMessage()).encodePrettily());
        }
    }

    private Future<JsonObject> validCredential(JsonObject deviceInfo)
    {
        return executor.executeBlocking(promise ->
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
}
