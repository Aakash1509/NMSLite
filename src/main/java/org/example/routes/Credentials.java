package org.example.routes;
import io.vertx.core.json.JsonArray;
import org.example.Constants;
import org.example.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.example.database.QueryUtility;

import static org.example.Main.credentials;

public class Credentials implements CrudOperations
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Credentials.class);

    public void route(Router credentialRouter)
    {
        try
        {
            credentialRouter.post("/").handler(this::create);

            credentialRouter.put("/:id").handler(this::update);

            credentialRouter.get("/").handler(this::getAll);

            credentialRouter.get("/:id").handler(this::get);

            credentialRouter.delete("/:id").handler(this::delete);
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in credential routing", exception);
        }
    }

    //Creating credential profile
    @Override
    public void create(RoutingContext context)
    {
        try
        {
            var requestBody = context.body().asJsonObject();

            var validation = Helper.validateCredential(requestBody);

            if(!validation.isEmpty())
            {
                context.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE,500)
                                .put(Constants.MESSAGE,validation).encodePrettily());

                return;
            }

            // If name is unique, insert in database
            QueryUtility.getInstance().insert(Constants.CREDENTIALS, new JsonObject()
                            .put("profile_name", requestBody.getString("credential.profile.name"))
                            .put("profile_protocol", requestBody.getString("credential.profile.protocol"))
                            .put("user_name", requestBody.getString("user.name"))
                            .put("user_password", requestBody.getString("user.password"))
                            .put("community", requestBody.getString("community"))
                            .put("version", requestBody.getString("version")))
                    .onComplete(result ->
                    {
                        if (result.succeeded())
                        {
                            Helper.insertInMap(credentials, result.result(), new JsonObject()
                                    .put("profile_id", result.result())
                                    .put("profile_name", requestBody.getString("credential.profile.name"))
                                    .put("profile_protocol", requestBody.getString("credential.profile.protocol"))
                                    .put("user_name", requestBody.getString("user.name"))
                                    .put("user_password", requestBody.getString("user.password"))
                                    .put("community", requestBody.getString("community"))
                                    .put("version", requestBody.getString("version")));

                            context.response()
                                    .setStatusCode(201)
                                    .end(new JsonObject()
                                            .put(Constants.STATUS_CODE, 201)
                                            .put(Constants.MESSAGE, "Credential profile created successfully")
                                            .put(Constants.CONTEXT, new JsonObject().put("credential.profile.id", result.result())).encodePrettily());
                        }
                        else
                        {
                            context.response()
                                    .setStatusCode(500)
                                    .end(new JsonObject()
                                            .put(Constants.STATUS_CODE, 500)
                                            .put(Constants.MESSAGE, "Failed to create credential profile")
                                            .put(Constants.ERROR, result.cause().getMessage()).encodePrettily());
                        }
                    });
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE, 500)
                            .put(Constants.MESSAGE, "Server error in creating credential profile")
                            .put(Constants.ERROR, exception.getCause().getMessage()).encodePrettily());
        }
    }

    //Updating credential profile
    @Override
    public void update(RoutingContext context)
    {

        var credentialID = context.pathParam("id");

        var requestBody = context.body().asJsonObject();

        try
        {
            var id = Long.parseLong(credentialID);

            QueryUtility.getInstance().update(Constants.CREDENTIALS, requestBody, new JsonObject().put("profile_id", id))
                    .onComplete(result->
                    {
                        if(result.succeeded())
                        {
                            var existing = credentials.get(id);

                            requestBody.forEach(entry->existing.put(entry.getKey(),entry.getValue()));

                            credentials.put(id,existing);

                            context.response()
                                    .setStatusCode(200)
                                    .end(new JsonObject()
                                            .put(Constants.STATUS_CODE,200)
                                            .put(Constants.MESSAGE,"Credential profile updated successfully").encodePrettily());
                        }
                        else
                        {
                            if (result.cause().getMessage().contains("No matching rows found"))
                            {
                                context.response()
                                        .setStatusCode(404)
                                        .end(new JsonObject()
                                                .put(Constants.STATUS_CODE,404)
                                                .put(Constants.MESSAGE,"Credential profile not found of this ID").encodePrettily());
                            }
                            else
                            {
                                context.response()
                                        .setStatusCode(500)
                                        .end(new JsonObject()
                                                .put(Constants.STATUS_CODE,500)
                                                .put(Constants.MESSAGE,"Database error while updating credential profile")
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
                            .put(Constants.MESSAGE,"Server error in updating credential profile")
                            .put(Constants.ERROR,exception.getCause().getMessage()).encodePrettily());
        }
    }

    //Deleting credential profile
    @Override
    public void delete(RoutingContext context)
    {
        var credentialID = context.pathParam("id");

        if (Helper.validateField(credentialID))
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE, 404)
                            .put(Constants.MESSAGE, "Please enter a valid credential ID").encodePrettily());
            return;
        }
        try
        {
            var id = Long.parseLong(credentialID);

            QueryUtility.getInstance().delete(Constants.CREDENTIALS,"profile_id",id)
                    .onComplete(result ->
                    {
                        if (result.succeeded())
                        {
                            credentials.remove(id);

                            context.response()
                                    .setStatusCode(200)
                                    .end(new JsonObject()
                                            .put(Constants.STATUS_CODE, 200)
                                            .put(Constants.MESSAGE, "Credential profile deleted successfully").encodePrettily());
                        }
                        else
                        {
                            if (result.cause().getMessage().contains("Information not found"))
                            {
                                context.response()
                                        .setStatusCode(404)
                                        .end(new JsonObject()
                                                .put(Constants.STATUS_CODE, 404)
                                                .put(Constants.MESSAGE, "Credential profile not found for this ID").encodePrettily());
                            }
                            else
                            {
                                context.response()
                                        .setStatusCode(500)
                                        .end(new JsonObject()
                                                .put(Constants.STATUS_CODE, 500)
                                                .put(Constants.MESSAGE, "Database error while deleting credential profile")
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
                            .put(Constants.MESSAGE, "Server error in deleting credential profile")
                            .put(Constants.ERROR, "Please enter a valid credential profile ID").encodePrettily());
        }
    }


    //Fetching credential profile
    @Override
    public void get(RoutingContext context)
    {
        var credentialID = context.pathParam("id");

        if (Helper.validateField(credentialID))
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE, 404)
                            .put(Constants.MESSAGE, "Please enter a valid credential ID").encodePrettily());
            return;
        }
        try
        {
            var id = Long.parseLong(credentialID);

            if(credentials.containsKey(id))
            {
                context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 200)
                                .put(Constants.MESSAGE, "Credential profile fetched successfully")
                                .put(Constants.CONTEXT, credentials.get(id)).encodePrettily());
            }
            else
            {
                context.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 404)
                                .put(Constants.MESSAGE, "Credential profile not found for this ID").encodePrettily());
            }
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE, 500)
                            .put(Constants.MESSAGE, "Server error in fetching credential profile")
                            .put(Constants.ERROR, "Please enter a valid credential profile ID").encodePrettily());
        }
    }

    //Fetching credential profiles
    @Override
    public void getAll(RoutingContext context)
    {
        try
        {
            if (!credentials.isEmpty())
            {
                var records = new JsonArray();

                credentials.forEach((id, profile) -> records.add(profile));

                context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 200)
                                .put(Constants.MESSAGE, "Credential profiles fetched successfully")
                                .put(Constants.CONTEXT, records).encodePrettily());
            }
            else
            {
                context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                                .put(Constants.STATUS_CODE, 200)
                                .put(Constants.MESSAGE, "Credential profiles fetched successfully")
                                .put(Constants.CONTEXT, "No credential profiles currently").encodePrettily());
            }
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put(Constants.STATUS_CODE,500)
                            .put(Constants.MESSAGE,"Server error in fetching credential profiles")
                            .put(Constants.ERROR,exception.getCause().getMessage()).encodePrettily());
        }
    }
}
