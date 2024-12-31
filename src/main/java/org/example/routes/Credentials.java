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
    private static final Logger logger = LoggerFactory.getLogger(Credentials.class);

    public void route(Router credentialRouter)
    {
        try
        {
            credentialRouter.post("/create").handler(this::create);

            credentialRouter.put("/:id").handler(this::update);

            credentialRouter.get("/").handler(this::getAll);

            credentialRouter.get("/:id").handler(this::get);

            credentialRouter.delete("/:id").handler(this::delete);
        }
        catch (Exception exception)
        {
            logger.error("Error in credential routing", exception);
        }
    }

    //Creating credential profile
    @Override
    public void create(RoutingContext context)
    {
        try
        {
            var requestBody = context.body().asJsonObject();

            var name = requestBody.getString("credential.profile.name");

            var protocol = requestBody.getString("credential.profile.protocol");

            if (name == null || name.isEmpty() || protocol == null || protocol.isEmpty())
            {
                context.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                                .put("status.code", 500)
                                .put("message", "Please enter both username and protocol").encodePrettily());

                return;
            }

            // Validate protocol-specific fields
            if ("SSH".equals(protocol))
            {
                var userName = requestBody.getString("user.name");

                var userPassword = requestBody.getString("user.password");

                if (userName == null || userName.isEmpty() || userPassword == null || userPassword.isEmpty())
                {
                    context.response()
                            .setStatusCode(500)
                            .end(new JsonObject()
                                    .put("status.code", 500)
                                    .put("message", "For SSH protocol, both user.name and user.password are required")
                                    .encodePrettily());
                    return;
                }
            }
            else if ("SNMP".equals(protocol))
            {
                var community = requestBody.getString("community");

                var version = requestBody.getString("version");

                if (community == null || community.isEmpty() || version == null || version.isEmpty())
                {
                    context.response()
                            .setStatusCode(500)
                            .end(new JsonObject()
                                    .put("status.code", 500)
                                    .put("message", "For SNMP protocol, both community and version are required")
                                    .encodePrettily());
                    return;
                }
            }
            else
            {
                // Invalid protocol
                context.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                                .put("status.code", 500)
                                .put("message", "Unsupported protocol: " + protocol)
                                .encodePrettily());
                return;
            }

            if (Helper.isNotUnique(credentials, name, "profile_name"))
            {
                context.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                                .put("status.code", 400)
                                .put("message", "Credential profile name should be unique").encodePrettily());
                return;
            }

            // If name is unique, insert in database
            QueryUtility.getInstance().insert(Constants.CREDENTIALS, new JsonObject()
                            .put("profile_name", name)
                            .put("profile_protocol", protocol)
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
                                    .put("profile_name", name)
                                    .put("profile_protocol", protocol)
                                    .put("user_name", requestBody.getString("user.name"))
                                    .put("user_password", requestBody.getString("user.password"))
                                    .put("community", requestBody.getString("community"))
                                    .put("version", requestBody.getString("version")));

                            context.response()
                                    .setStatusCode(201)
                                    .end(new JsonObject()
                                            .put("status.code", 201)
                                            .put("message", "Credential profile created successfully")
                                            .put("data", new JsonObject().put("credential.profile.id", result.result())).encodePrettily());
                        }
                        else
                        {
                            context.response()
                                    .setStatusCode(500)
                                    .end(new JsonObject()
                                            .put("status.code", 500)
                                            .put("message", "Failed to create credential profile")
                                            .put("error", result.cause().getMessage()).encodePrettily());
                        }
                    });
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code", 500)
                            .put("message", "Server error in creating credential profile")
                            .put("error", exception.getCause().getMessage()).encodePrettily());
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
                                            .put("status.code",200)
                                            .put("message","Credential profile updated successfully").encodePrettily());
                        }
                        else
                        {
                            if (result.cause().getMessage().contains("No matching rows found"))
                            {
                                context.response()
                                        .setStatusCode(404)
                                        .end(new JsonObject()
                                                .put("status.code",404)
                                                .put("message","Credential profile not found of this ID").encodePrettily());
                            }
                            else
                            {
                                context.response()
                                        .setStatusCode(500)
                                        .end(new JsonObject()
                                                .put("status.code",500)
                                                .put("message","Database error while updating credential profile")
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
                            .put("message","Server error in updating credential profile")
                            .put("error",exception.getCause().getMessage()).encodePrettily());
        }
    }

    //Deleting credential profile
    @Override
    public void delete(RoutingContext context)
    {
        var credentialID = context.pathParam("id");

        if (credentialID == null || credentialID.isEmpty())
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put("status.code", 404)
                            .put("message", "Please enter a valid credential ID").encodePrettily());
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
                                            .put("status.code", 200)
                                            .put("message", "Credential profile deleted successfully").encodePrettily());
                        }
                        else
                        {
                            if (result.cause().getMessage().contains("Information not found"))
                            {
                                context.response()
                                        .setStatusCode(404)
                                        .end(new JsonObject()
                                                .put("status.code", 404)
                                                .put("message", "Credential profile not found for this ID").encodePrettily());
                            }
                            else
                            {
                                context.response()
                                        .setStatusCode(500)
                                        .end(new JsonObject()
                                                .put("status.code", 500)
                                                .put("message", "Database error while deleting credential profile")
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
                            .put("message", "Server error in deleting credential profile")
                            .put("error", "Please enter a valid credential profile ID").encodePrettily());
        }
    }


    //Fetching credential profile
    @Override
    public void get(RoutingContext context)
    {
        var credentialID = context.pathParam("id");

        if (credentialID == null || credentialID.isEmpty())
        {
            context.response()
                    .setStatusCode(404)
                    .end(new JsonObject()
                            .put("status.code", 404)
                            .put("message", "Please enter a valid credential ID").encodePrettily());
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
                                .put("status.code", 200)
                                .put("message", "Credential profile fetched successfully")
                                .put("data", credentials.get(id)).encodePrettily());
            }
            else
            {
                context.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                                .put("status.code", 404)
                                .put("message", "Credential profile not found for this ID").encodePrettily());
            }
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code", 500)
                            .put("message", "Server error in fetching credential profile")
                            .put("error", "Please enter a valid credential profile ID").encodePrettily());
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
                                .put("status.code", 200)
                                .put("message", "Credential profiles fetched successfully")
                                .put("data", records).encodePrettily());
            }
            else
            {
                context.response()
                        .setStatusCode(200)
                        .end(new JsonObject()
                                .put("status.code", 200)
                                .put("message", "Credential profiles fetched successfully")
                                .put("data", "No credential profiles currently").encodePrettily());
            }
        }
        catch (Exception exception)
        {
            context.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                            .put("status.code",500)
                            .put("message","Server error in fetching credential profiles")
                            .put("error",exception.getCause().getMessage()).encodePrettily());
        }
    }
}
