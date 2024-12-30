package org.example.routes;

import io.vertx.ext.web.RoutingContext;

public interface CrudOperations   //5 CRUD operations will be here
{
    public void create(RoutingContext context);

    public void update(RoutingContext context);

    public void delete(RoutingContext context);

    public void get(RoutingContext context);

    public void getAll(RoutingContext context);
}
