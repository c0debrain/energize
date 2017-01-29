package xyz.hyperreal.energize

import org.mindrot.jbcrypt.BCrypt


object AuthorizationFunctions {
	def register( env: Env, json: OBJ ) = {
		val users = (env get "users" get).asInstanceOf[Table]
		val required = users.names.toSet -- Set( "createdTime", "updatedTime", "state", "groups" )

		if ((required -- json.keySet) nonEmpty)
			throw new BadRequestException( "register: excess field(s): " + (required -- json.keySet).mkString(", ") )

		val json1: OBJ =
			(json map {
				case ("password", p: String) => ("password", BCrypt.hashpw( p, BCrypt.gensalt ))
				case f => f
			}) + ("groups" -> List("user"))

		val user = CommandFunctions.insert( env, users, json1 )
		val tokens = (env get "tokens" get).asInstanceOf[Table]
		val token = {
			def gentoken: String = {
				val tok = SupportFunctions.rndAlpha( env, 15 )

				QueryFunctions.findOption( env, tokens, "token", tok ) match {
					case None => tok
					case Some( _ ) => gentoken
				}
			}

			gentoken
		}

		CommandFunctions.insert( env, tokens, Map("token" -> token, "user" -> user).asInstanceOf[OBJ] )
		token
	}
}