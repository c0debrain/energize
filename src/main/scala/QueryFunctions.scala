package xyz.hyperreal.cras

import collection.mutable.ListBuffer
import collection.immutable.ListMap

	
// 	def list( env: Env, resource: Table ) =
// 		resource.columns.values.find( c => c.typ.isInstanceOf[TableType] ) match {
// 			case None =>
// 				query( env, resource, s"SELECT * FROM ${resource.name}" )
// 			case Some( Column(col, TableType(reft), _, _, _, _) ) =>
// 				query( env, resource, s"SELECT * FROM ${resource.name} LEFT OUTER JOIN $reft ON ${resource.name}.$col = $reft.id" )
// 			case _ => throw new CrasErrorException( "shouldn't be impossible" )
// 		}

object QueryFunctionHelpers {
	def listQuery( resource: Table ) = {
		val buf = new StringBuilder( s"SELECT * FROM ${resource.name}" )
		
		resource.columns.values filter (c => c.typ.isInstanceOf[TableType]) foreach {
			case Column(col, TableType(reft), _, _, _, _) =>
				 buf ++= s" LEFT OUTER JOIN $reft ON ${resource.name}.$col = $reft.id"
			case _ => sys.error( "somthing bad happened" )
		}
		
//		println( buf )
		buf.toString
	}
	
	val FILTER = "([a-zA-Z.]+)(=|<|>|<=|>=|!=|~)(.+)"r
	val ORDER = """([a-zA-Z.]+)\:(ASC|asc|DESC|desc)"""r
	val DELIMITER = ","r
	val NUMERIC = """[+-]?\d*\.?\d+(?:[eE][-+]?[0-9]+)?"""r
	
	def numeric( s: String ) = NUMERIC.pattern.matcher( s ).matches
}

object QueryFunctions {
	def query( env: Env, resource: Table, sql: String ) = {
		val res = env.statement.executeQuery( sql )
		val list = new ListBuffer[Map[String, Any]]
		val md = res.getMetaData
		val count = md.getColumnCount

		def mkmap( table: Table ): Map[String, Any] = {
			val attr = new ListBuffer[(String, Any)]
			
			for (i <- 1 to count) {
				val dbtable = md.getTableName( i )
				val dbcol = md.getColumnName( i )
				val obj = res.getObject( i )
				
				env.tables get dbtable match {
					case None => sys.error( "data not from a known table" )
					case Some( t ) if t == table =>
						t.columns get dbcol match {
							case None if dbcol.toLowerCase == "id" =>
								attr += ("id" -> obj)
							case None => sys.error( "data not from a known column" )
							case Some( Column(cname, TableType(reft), _, _, _, _) ) if obj ne null =>
								attr += (cname -> mkmap( env.tables(reft.toUpperCase) ))
							case Some( c ) =>
								attr += (c.name -> obj)
						}
					case _ =>
				}
			}
			
			ListMap( attr: _* )
		}
		
		while (res.next)
			list += mkmap( resource )
		
		list.toList
	}
	
	def size( env: Env, resource: Table ) = {
		val res = env.statement.executeQuery( s"SELECT COUNT(*) FROM ${resource.name}" )
		
		res.next
		res.getInt( 1 )
	}

	def list( env: Env, resource: Table, filter: Option[String], order: Option[String], page: Option[String], limit: Option[String] ) = {
		val where =
			if (filter == None)
				""
			else {
				" WHERE " +
					(QueryFunctionHelpers.DELIMITER.split( filter.get ) map {
						f => {
							val QueryFunctionHelpers.FILTER(col, op, search) = f
							val search1 = escapeQuotes( search )
							
							if (op == "~")
								s"$col LIKE '$search1'"
							else if (QueryFunctionHelpers.numeric( search1 ))
								s"$col $op $search1"
							else
								s"$col $op '$search1'"
						}
					} mkString " AND ")
			}
		val orderby =
			if (order == None)
				""
			else {
				" ORDER BY " +
					(QueryFunctionHelpers.DELIMITER.split( order.get ) map {
						o => {
							val QueryFunctionHelpers.ORDER(col, ordering) = o
							val ordering1 = ordering.toUpperCase
							
							s"$col $ordering1"
						}
					} mkString ", ")
			}
		val limit1 = limit.getOrElse( "10" ).toInt
		val limoff =			
			if (page == None)
				if (limit == None)
					""
				else
					s" LIMIT $limit1"
			else {
				val page1 = (page.get.toInt - 1)*limit1
				
				s" LIMIT $limit1 OFFSET $page1"
			}
			
		query( env, resource, QueryFunctionHelpers.listQuery(resource) + where + orderby + limoff )
	}
	
	def find( env: Env, resource: Table, id: Long ) =
		query( env, resource, QueryFunctionHelpers.listQuery(resource) + s" WHERE ${resource.name}.id = $id" )
}
