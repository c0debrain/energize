package xyz.hyperreal.energize

import java.sql._
import java.io.PrintWriter

import collection.mutable.HashMap
import jline.console.ConsoleReader

import xyz.hyperreal.table.TextTable


object REPLMain extends App {
	
	val reader =
		new ConsoleReader {
			setExpandEvents( false )
			setBellEnabled( false )
			setPrompt( "> " )
		}
	val out = new PrintWriter( reader.getTerminal.wrapOutIfNeeded(System.out), true )
	var line: String = _
	var stacktrace = false

	s"""
	|Welcome to Energize version $VERSION.
	|Type in expressions to have them evaluated.
	|Type help for more information.
	""".trim.stripMargin.lines foreach println
	println

	val vars = new HashMap[String, AnyRef]
	var env: Environment = _
	var connection: Connection = _
	var statement: Statement = _
	var db: Database = _
	var name = "H2"
	var driver = "org.h2.Driver"
	var url = "jdbc:h2:mem:"
	var user = "sa"
	var password = ""
	var config: String = _
	var key = AUTHORIZATION.getString( "key" )

	sys.addShutdownHook {
		connection.close
	}
	
	def connect {
		val (c, s, d) = Energize.dbconnect( name, driver, url, user, password )
		
		connection = c
		statement = s
		db = d
		env = new Environment( Map.empty, Nil, Builtins.map ++ vars, Builtins.sys, connection, statement, db, Map.empty, Map.empty, key )
		println( connection )
		println( connection.getMetaData.getDriverName + " " + connection.getMetaData.getDriverVersion )
	}

	def reconnect = {
		if (connection ne null)
			connection.close

		connect
	}

	def load( conf: String ) = {
		env = Energize.configure( io.Source.fromFile(conf + ".energize"), connection, statement, db, key ) add vars
	}

	connect
	println

	while ({line = reader.readLine; line != null}) {
		val line1 = line.trim
		val com = line1 split "\\s+" toList
	
		def result( method: String, path: String, json: String ) =
			println( env.process(method, path, json) )

		try {
			com match {
				case List( "config"|"co" ) =>
					name = DATABASE.getString( "name" )
					driver = DATABASE.getString( "driver" )
					url = DATABASE.getString( "url" )
					user = DATABASE.getString( "user" )
					password = DATABASE.getString( "password" )
				case List( "connect"|"c" ) =>
					reconnect
				case List( "connect"|"c", u ) =>
					if (connection ne null)
						connection.close

					url = u
					connect
				case List( "db" ) =>
					println( driver, url, user, password )
				case List( "driver"|"d", d ) =>
					driver = d
				case List( "help"|"h" ) =>
					"""
					|config                                     set database parameters from config file
					|connect (c)                                (re)connect to database using current database parameters
					|connect (c) <url>                          connect to database using <url> clearing in-memory table and routing information
					|db                                         show current database parameters
					|driver (d) <driver>                        set database <driver>
					|load (l)                                   reload previously loaded configuration
					|load (l) <config>                          load a <config> (".energize" file) creating all tables and routes as specified
					|help (h)                                   print this summary
					|password (p) <password>                    set database <password>
					|quit (q)                                   exit the REPL
					|routes (r)                                 print all routes showing absolute paths
					|trace (t) on/off                           turn exception stack trace on or off
					|user (u) <user>                            set database <user>
					|variable (v) <name> <value>                set variable <name> to <value> (added to environment)
					|variable (v) <name>                        delete variable <name> (removed from environment)
					|variable (v)                               show current REPL variables (not all environment variables)
					|GET/POST/PUT/PATCH/DELETE <path> [<json>]  issue a request with optional <json> message body
					|select ...                                 execute SQL query
					|<SQL>                                      execute <SQL> non-query command
					|?<expression>                              evaluate an ENERGIZE action script expression
					""".trim.stripMargin.lines foreach out.println
				case List( "load"|"l" ) =>
					if (config eq null)
						println( "no configuration has been loaded" )

					reconnect
					load( config )
				case List( "load"|"l", conf ) =>
					config = conf
					load( conf )
//				case List( "wipe"|"w" ) =>
//					connection.close
//					new File( sys.props("user.home"), db + ".mv.db" ).delete
//					new File( sys.props("user.home"), db + ".trace.db" ).delete
//					connect
//					env = null
				case List( "password"|"p", p ) =>
					password = p
				case List( "quit"|"q" ) =>
					if (connection ne null)
						connection.close

					sys.exit
				case List( "routes"|"r" ) =>
					for (Route(method, URIPath(path), action) <- env.routes ) {
						val pathbuf = new StringBuilder
						
						path foreach {
							case NameURISegment( name ) =>
								pathbuf += '/'
								pathbuf ++= name
							case ParameterURISegment( parm, typ ) =>
								pathbuf += '/'
								pathbuf ++= parm
								pathbuf += ':'
								pathbuf ++= typ
						}
						
						println( method + " " + pathbuf + " " + action )
					}
				case List( "trace"|"t", "on" ) => stacktrace = true
				case List( "trace"|"t", "off" ) => stacktrace = false
				case List( "user"|"u", u ) => user = u
				case List( "variable"|"v" ) => println( vars )
				case List( "variable"|"v", n ) =>
					vars -= n
					env = env remove n
				case List( "variable"|"v", n, v ) =>
					vars(n) = v
					env = env add n -> v
				case Nil|List( "" ) =>
				case List( method@("GET"|"get"|"DELETE"|"delete"), path ) =>
					result( method, path, null )
				case (method@("GET"|"get"|"POST"|"post"|"PUT"|"put"|"PATCH"|"patch")) :: path :: a if a != Nil =>
					result( method, path, line1.split("\\s+", 3)(2) )
				case "select" :: _ =>
					print( TextTable(statement.executeQuery(line1)) )
				case _ if line1 startsWith "?" =>
					println( env evaluate line1.substring(1) )
				case _ => //sql non-query command
					statement.execute( line1 )
			}
		}
		catch
		{
			case e: Exception =>
				if (stacktrace)
					e.printStackTrace( out )
				else
					out.println( e )
		}
		
		out.println
	}
	
}