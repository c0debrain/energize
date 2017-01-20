package xyz.hyperreal.cras

import java.sql.Statement

import collection.mutable.ListBuffer


object CommandFunctionHelpers {
	def insertCommand( env: Env, resource: Table, json: Map[String, AnyRef] ) = {
		val com = new StringBuilder( "INSERT INTO " )
		val json1 = escapeQuotes( json )

		com ++= resource.name
		com ++= resource.names.
			filterNot (n => resource.columns(env.db.desensitize( n )).typ.isInstanceOf[ManyReferenceType]).
			mkString( " (", ", ", ") " )
		com ++= "VALUES ("

		val values = new ListBuffer[String]

		for (c <- resource.names)
			json1 get c match {
				case None if resource.columns(env.db.desensitize( c )).typ.isInstanceOf[ManyReferenceType] =>
					throw new CrasErrorException( s"insert: manay-to-many field cannot be NULL: $c" )
				case None => values += "NULL"
				case Some( v ) =>
					resource.columns(env.db.desensitize( c )).typ match {
						case SingleReferenceType( tname, tref ) if v != null && !v.isInstanceOf[Int] && !v.isInstanceOf[Long] =>
							values += s"(SELECT id FROM $tname WHERE " +
								(tref.columns.values.find( c => c.unique ) match {
									case None => throw new CrasErrorException( "insert: no unique column in referenced resource in POST request" )
									case Some( uc ) => uc.name
								}) + " = '" + String.valueOf( v ) + "')"
						case ManyReferenceType( _, _ ) =>
							if (v eq null)
								throw new CrasErrorException( s"insert: manay-to-many field cannot be NULL: $c" )
						case StringType => values += '\'' + String.valueOf( v ) + '\''
						case _ => values += String.valueOf( v )
					}
			}

		com ++= values mkString ", "
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
		val id =
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

		resource.columns.values.foreach {
			case Column( col, ManyReferenceType(tab, ref), _, _, _, _ ) =>
				json get col match {
					case None =>
					case Some( v ) =>

				}
			case _ =>
		}

		id
	}
	
	def update( env: Env, resource: Table, json: Map[String, AnyRef], id: Long, all: Boolean ) =
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
					case (k, v) if {typ = resource.columns(env.db.desensitize(k)).typ; typ.isInstanceOf[SingleReferenceType]} =>
						if (v.isInstanceOf[Int] || v.isInstanceOf[Long])
							k + " = " + String.valueOf( v )
						else {
							val reft = typ.asInstanceOf[SingleReferenceType].ref
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
}