package xyz.hyperreal.cras

import java.sql._
import java.io.{PrintStream, ByteArrayOutputStream}


object Test {

  def dbconnect = {
    Class.forName( "org.h2.Driver" )

    val connection = DriverManager.getConnection( "jdbc:h2:mem:test", "sa", "" )

    (connection, connection.createStatement)
  }

	def capture( code: String ) = {
		val buf = new ByteArrayOutputStream
		
		Console.withOut( new PrintStream(buf) )( Cras.configure(io.Source.fromString(code), null, null) )
		buf.toString.trim
	}
	
	def captureReturn( code: String ) = {
		val buf = new ByteArrayOutputStream
		val ret = Console.withOut( new PrintStream(buf) )( Cras.configure(io.Source.fromString(code), null, null) )
		
		(ret, buf.toString.trim)
	}
}