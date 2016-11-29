CRAS
====

Configurable ReST API Server

[![Build Status](https://travis-ci.org/edadma/cras.svg?branch=dev)](https://travis-ci.org/edadma/cras)


Overview
--------

*cras* (which is an acronym for "configurable ReST API server) allows you to get your ReST API up and running in very little time by reducing the amount of typing normally required.


Example
-------

This example shows how to get a simple API to support a "to do list" app working. Start by creating a folder for the example. Now download the [executable](https://dl.bintray.com/edadma/generic/cras-0.1.jar) and place it in the example folder you just created. Now, create a text file called `todo.cras` will the following text in it.

	table todo api/v1
	  name        string  required
	  description string  optional
	  status      integer required

The executable contains both an HTTP server and a REPL to make it easier to develop your API configurations. We'll start by looking at the server.


### HTTP Server

Now, on the command line in the example folder, start the server with the command

	java -cp cras-0.1.jar xyz.hyperreal.cras.ServerMain ./todo todo
	
You should now have a working HTTP server bound to port 8080 that will serve API requests for a todo list. Let's try it out. Using `curl`, let's add an item to our todo list database. To do that, type

	curl --data "{name: \"finish 0.1\", status: 1}" http://localhost:8080/api/v1/todo

You should see

	{
		"status": "ok",
		"update": 1
	}

as the response. This means that the route (URI) was correct and that one row was added to the database. Add another item by typing

	curl --data "{name: \"write readme\", description: \"add example involving finishing 0.1 and writing the readme\", status: 1}" http://localhost:8080/api/v1/todo

We can see our todo list with the command

	curl http://localhost:8080/api/v1/todo
	
getting the response

	{
		"status": "ok",
		"data": [
			{
				"ID": 1,
				"NAME": "finish 0.1",
				"DESCRIPTION": null,
				"STATUS": 1
			},
			{
				"ID": 2,
				"NAME": "write readme",
				"DESCRIPTION": "add example involving finishing 0.1 and writing the readme",
				"STATUS": 1
			}
		]
	}

The `description` is `null` in the first one because we marked that column as `optional` and did not provide data for it in our post. We can retrieve just the second item with the command

	curl http://localhost:8080/api/v1/todo/2
	
Press `Ctrl-C` to stop the server.


### REPL

To start the REPL, type the following command (while in the same folder where the executable was placed)

	java -jar cras-0.1.jar
	
The REPL creates a database called `test` for you in the same folder. Type `help` to see all the REPL commands. Tell the REPL to load the `todo` configuration by typing

	l todo
	
To verify that a table called `todo` has been created, type the SQL command

	select * from todo;
	
In the REPL, you can always restart from scratch using the `wipe` command, reload a modified configuration using `load`, etc.
