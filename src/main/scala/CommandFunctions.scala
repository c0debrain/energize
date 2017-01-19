package xyz.hyperreal.cras

import java.sql.Statement


object CommandFunctionHelpers {
	def insertCommand( env: Env, resource: Table, json: Map[String, AnyRef] ) = {
		val com = new StringBuilder( "INSERT INTO " )
		val json1 = escapeQuotes( json )

		com ++= resource.name
		com ++= resource.names.mkString( " (", ", ", ") " )
		com ++= "VALUES ("
		com ++=
			(for (c <- resource.names)
				yield {
					json1 get c match {
						case None => "NULL"
						case Some( v ) =>
							resource.columns(env.db.desensitize( c )).typ match {
								case ReferenceType( tname, tref ) if v != null && !v.isInstanceOf[Int] && !v.isInstanceOf[Long] =>
									s"(SELECT id FROM $tname WHERE " +
										(tref.columns.values.find( c => c.unique ) match {
											case None => throw new CrasErrorException( "insert: no unique column in referenced resource in POST request" )
											case Some( uc ) => uc.name
										}) + " = '" + String.valueOf( v ) + "')"
								case StringType => '\'' + String.valueOf( v ) + '\''
								case _ => String.valueOf( v )
							}
					}
				}) mkString ", "
		com += ')'
		com.toString
	}
}

object CommandFunctions {
	def command( env: Env, sql: String ) = env.statement.executeUpdate( sql )
	
	def delete( env: Env, resource: Table, id: Long ) = command( env, s"DELETE FROM ${resource.name} WHERE id = $id;" )
	
	def batchInsert( env: Env, resource: Table, rows: List[List[AnyRef]] ) {
		val types = for ((n, i) <- resource.names zipWithIndex) yield (i + 1, resource.columns(env.db.desensitize(n)).typ)
			
		for (r <- rows) {
			for (((i, t), c) <- types zip r) {
				(t, c) match {
					case (StringType, a: String) => resource.preparedInsert.setString( i, a )
					case (IntegerType, a: java.lang.Integer) => resource.preparedInsert.setInt( i, a )
					case (LongType, a: java.lang.Long) => resource.preparedInsert.setLong( i, a )
					case _ => sys.error( s"missing support for '$t'" )
				}
			}
				
			resource.preparedInsert.addBatch
		}
		
		resource.preparedInsert.executeBatch
		resource.preparedInsert.clearParameters
	}
	
	def insert( env: Env, resource: Table, json: Map[String, AnyRef] ) = {
// 		if (json.keySet == resource.names.toSet) {
// 			
// 			for ((n, i) <- resource.names zipWithIndex)
// 				json(n) match {
// 					case a: Integer => resource.preparedInsert.setInt( i + 1, a )
// 					case a: String => resource.preparedInsert.setString( i + 1, a )
// 				}
// 				
// 			resource.preparedInsert.executeUpdate
// 			resource.preparedInsert.clearParameters
// 			
// 			val g = resource.preparedInsert.getGeneratedKeys
// 			
// 			g.next
// 			g.getLong(1)
// 		} else {
		val com = CommandFunctionHelpers.insertCommand( env, resource, json )

		if (env.db == PostgresDatabase) {
			val res = env.statement.executeQuery( com + "RETURNING id" )

			res.next
			res.getLong( 1 )
		} else {
			env.statement.executeUpdate( com, Statement.RETURN_GENERATED_KEYS )

			val g = env.statement.getGeneratedKeys

			g.next
			g.getLong( 1 )
		}
	}
	
	def update( env: Env, resource: Table, json: Map[String, Any], id: Long, all: Boolean ) =
		if (all && json.keySet != resource.names.toSet)
			throw new CrasErrorException( "update: missing column(s) in PUT request" )
		else {
			val com = new StringBuilder( "UPDATE " )
			var typ: ColumnType = null
			
			com ++= resource.name
			com ++= " SET "
			com ++=
				escapeQuotes( json ).toList map {
					case (k, v) if resource.columns(env.db.desensitize(k)).typ == StringType => k + " = '" + String.valueOf( v ) + "'"
					case (k, v) if {typ = resource.columns(env.db.desensitize(k)).typ; typ.isInstanceOf[ReferenceType]} =>
						if (v.isInstanceOf[Int] || v.isInstanceOf[Long])
							k + " = " + String.valueOf( v )
						else {
							val reft = typ.asInstanceOf[ReferenceType].ref
							val refc =
								reft.columns.values.find( c => c.unique ) match {
									case None => throw new CrasErrorException( "update: no unique column in referenced resource in PUT/PATCH request" )
									case Some( c ) => c.name
								}

							k + s" = (SELECT id FROM $reft WHERE $refc = '$v')"
						}
					case (k, v) => k + " = " + String.valueOf( v )
				} mkString ", "
			com ++= " WHERE id = "
			com ++= id.toString
			env.statement.executeUpdate( com.toString )
		}

	def associate( env: Env, src: Table, sfield: String, svalue: AnyRef, dst: Table, dfield: String, dvalue: AnyRef ) =
		insert( env, env.tables(src.name + '$' + dst.name),
			Map(
				src.name + "$id" -> QueryFunctions.findOne(env, src, sfield, svalue)("id"),
				dst.name + "$id" -> QueryFunctions.findOne(env, dst, dfield, dvalue)("id")) )

}//findOne(students, "name", "rayna").id, findOne(classrooms, "number", "105").id