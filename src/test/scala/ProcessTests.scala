package xyz.hyperreal.cras

import org.scalatest._
import prop.PropertyChecks


class ProcessTests extends FreeSpec with PropertyChecks with Matchers {
	
	"empty database" in {
		val (c, s) = dbconnect( "test", true )
		val config =
			"""
			|table todo api/v1
			|	name        string  required
			|	description string  optional
			|	status      integer required
			""".trim.stripMargin
			
		val (tables, routes) = configuration( io.Source.fromString(config), c )

		process( "GET", "/api/v1/todo", "", tables, routes, s ) shouldBe
			Some( """
			|{
			|  "status": "ok",
			|  "data": []
			|}
			""".trim.stripMargin )
		process( "GET", "/api/v1/tod", "", tables, routes, s ) shouldBe None
		
		c.close
	}
	
	"post/get one item" in {
		val (c, s) = dbconnect( "test", true )
		val config =
			"""
			|table todo api/v1
			|	name        string  required
			|	description string  optional
			|	status      integer required
			""".trim.stripMargin
			
		val (tables, routes) = configuration( io.Source.fromString(config), c )

		process( "POST", "/api/v1/todo", """{"name": "do something", "status": 1}""", tables, routes, s ) shouldBe
			Some( """
			|{
			|  "status": "ok",
			|  "data": 1
			|}
			""".trim.stripMargin )

		process( "GET", "/api/v1/todo", "", tables, routes, s ) shouldBe
			Some( """
			|{
			|  "status": "ok",
			|  "data": [
			|    {
			|      "ID": 1,
			|      "NAME": "do something",
			|      "DESCRIPTION": null,
			|      "STATUS": 1
			|    }
		  |  ]
			|}
			""".trim.stripMargin )
		process( "GET", "/api/v1/tod", "", tables, routes, s ) shouldBe None
		
		c.close
	}
	
}