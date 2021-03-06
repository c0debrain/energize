package xyz.hyperreal.energize

import java.time.{Instant, ZoneOffset}

import util.Random._

import xyz.hyperreal.table.TextTable
import xyz.hyperreal.importer.Importer


object UtilityFunctions {
	
	def print( env: Environment, o: Any ) = println( o )

	def toInt( env: Environment, v: String ) = v.toInt

	def toLong( env: Environment, v: String ) = v.toLong

	def toString( env: Environment, o: Any ) = o.toString

	def eval( env: Environment, expr: String ) = env.evaluate( expr )

	def rndPrintable( env: Environment, len: Int ) = new String( Array.fill( len )( util.Random.nextPrintableChar) )

	def rndAlpha( env: Environment, len: Int ) = new String( Array.fill( len )((util.Random.nextInt('z' - 'a') + 'a').toChar) )

	def rndInt( env: Environment, low: Int, high: Int ) = nextInt( high + 1 - low ) + low

	def show( env: Environment, tab: String ) = Console.print( TextTable(env.statement.executeQuery(s"select * from $tab")) )

	def Some( env: Environment, v: Any ) = scala.Some( v )

	def now( env: Environment ) = Instant.now.atOffset( ZoneOffset.UTC )

	def reject( env: Environment ) = throw new RejectRouteThrowable

	def jsrequest( env: Environment ): Unit = {
		for (t <- env.tables.values)
			env.jseng.put( t.name, new ResourceJSObject(env, t) )

		env.jseng.put( "req", new RequestJSObject(env) )
	}

	def jscompile( env: Environment, code: String ) = {
		jsrequest( env )
		env.jseng.compile( code )
	}

	def jseval( env: Environment, code: String ) = {
		jscompile( env, code ).eval
	}

	def tableSchema( env: Environment, table: Table ) = {
		val primitive: PartialFunction[ColumnType, String] = {
			case StringType => "string"
			case TextType => "text"
			case BooleanType => "boolean"
			case IntegerType => "integer"
			case LongType => "long"
			case UUIDType => "uuid"
			case DateType => "date"
			case DatetimeType => "datetime"
			case TimeType => "time"
			case TimestampType => "timestamp"
			case BinaryType => "binary"
			case BLOBType( _ ) => "blob"
			case FloatType => "float"
			case DecimalType( _, _ ) => "decimal"
			case MediaType( _, _, _ ) => "media"
		}

		def column( t: ColumnType ) =
			if (primitive isDefinedAt t)
				Map( "category" -> "primitive", "type" -> primitive(t) )
			else
				t match {
					case SingleReferenceType( table, _ ) => Map( "category" -> "one-to-many", "type" -> table )
					case ManyReferenceType( table, _ ) => Map( "category" -> "many-to-many", "type" -> table )
					case ArrayType( parm, _, _, _ ) => Map( "category" -> "array", "type" -> primitive(parm) )
				}

		def modifiers( secret: Boolean, required: Boolean, unique: Boolean, indexed: Boolean ) =
			(if (secret) List("secret") else Nil) ++
			(if (required) List("required") else Nil) ++
			(if (unique) List("unique") else Nil) ++
			(if (indexed) List("indexed") else Nil)

		def fields( cs: List[Column] ) =
			cs map {case Column( name, typ, secret, required, unique, indexed, validators ) =>	// todo: add validators support to schema
				Map( "name" -> name, "type" -> column(typ), "modifiers" -> modifiers(secret, required, unique, indexed))}

		def uripath( path: URIPath ) = path.segments map {case NameURISegment( n ) => n} mkString ("/", "/", "")

		val Table(name, columns, _, resource, base, _, _, _, _) = table

		Map( "name" -> name, "resource" -> resource, "fields" -> fields(columns), "base" -> (base map uripath orNull) )
	}

	def databaseSchema( env: Environment ) = {
		val tables = env.tables.values map {t => tableSchema( env, t )}

		Map( "tables" -> tables )
	}

	def populateDatabase( env: Environment, file: String ): Unit = {
		new Importer().importFromFile( file, false ) foreach { table =>
			env.tables get env.db.desensitize(table.name) match {
				case None =>
				case Some( t ) => CommandFunctions.batchInsert( env, t, table.data, true )
			}
		}
	}
}
