package xyz.hyperreal.energize


object AggragateFunctionsHelpers {

	def filtering( filter: Option[String] ) =
		QueryFunctionHelpers.filtering( filter ) match {
			case "" => ""
			case w => s"WHERE $w"
		}

	def checkField( resource: Table, field: String ) =
		resource.columnMap get field match {
			case None => throw new NotFoundException( s"field '$field' not found" )
			case Some( c ) => c.typ
		}

	def aggregate( env: Environment, func: String, resource: Table, filter: Option[String], field: String ) = {
		val typ = AggragateFunctionsHelpers.checkField( resource, field )
		val where = AggragateFunctionsHelpers.filtering(filter)
		val res = env.statement.executeQuery( s"SELECT $func(${nameIn(field)}) FROM ${resource.name} $where" )

		res.next

		typ match {
			case IntegerType => BigInt( res.getLong(1) )
			case FloatType => res.getDouble( 1 )
		}
	}

}

object AggragateFunctions {

	def count( env: Environment, resource: Table, filter: Option[String] ) = {
		val where = AggragateFunctionsHelpers.filtering(filter)
		val res = env.statement.executeQuery( s"SELECT COUNT(*) FROM ${resource.name} $where" )

		res.next

		BigInt( res.getLong(1) )
	}

	def avg( env: Environment, resource: Table, filter: Option[String], field: String ) = {
		val where = AggragateFunctionsHelpers.filtering(filter)
		val res = env.statement.executeQuery( s"SELECT AVG(${nameIn(field)}) FROM ${resource.name} $where" )

		res.next

		res.getDouble( 1 )
	}

	def sum( env: Environment, resource: Table, filter: Option[String], field: String ) = AggragateFunctionsHelpers.aggregate( env, "SUM", resource, filter, field )

	def min( env: Environment, resource: Table, filter: Option[String], field: String ) = AggragateFunctionsHelpers.aggregate( env, "MIN", resource, filter, field )

	def max( env: Environment, resource: Table, filter: Option[String], field: String ) = AggragateFunctionsHelpers.aggregate( env, "MAX", resource, filter, field )

}