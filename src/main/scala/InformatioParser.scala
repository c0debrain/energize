package xyz.hyperreal.informatio

import java.{lang => boxed}

import util.parsing.combinator.PackratParsers
import util.parsing.combinator.syntactical.StandardTokenParsers
import util.parsing.input.CharArrayReader.EofCh
import util.parsing.combinator.lexical.StdLexical
import util.parsing.input.Reader

import xyz.hyperreal.indentation_lexical._


class InformatioParser extends StandardTokenParsers with PackratParsers
{
	override val lexical: IndentationLexical =
		new IndentationLexical( false, true, List("[", "("), List("]", ")"), "//", "/*", "*/" )
		{
			override def token: Parser[Token] = decimalParser | super.token

			override def identChar = letter | elem('_') | elem('$')
			
			override def whitespace: Parser[Any] = rep[Any](
				whitespaceChar
				| '/' ~ '*' ~ comment
				| '/' ~ '/' ~ rep( chrExcept(EofCh, '\n') )
				| '/' ~ '*' ~ failure("unclosed comment")
				)
			
			private def decimalParser: Parser[Token] =
				rep1(digit) ~ optFraction ~ optExponent ^^
					{case intPart ~ frac ~ exp =>
						NumericLit( (intPart mkString "") :: frac :: exp :: Nil mkString "")} |
				fraction ~ optExponent ^^
					{case frac ~ exp => NumericLit( frac + exp )}

			private def chr( c: Char ) = elem( "", ch => ch == c )

			private def sign = chr( '+' ) | chr( '-' )

			private def optSign = opt( sign ) ^^
				{
					case None => ""
					case Some(sign) => sign
				}

			private def fraction = ('.' <~ not('.')) ~ rep( digit ) ^^
				{case dot ~ ff => dot :: (ff mkString "") :: Nil mkString ""}

			private def optFraction = opt( fraction ) ^^
				{
					case None => ""
					case Some( fraction ) => fraction
				}

			private def exponent = (chr( 'e' ) | chr( 'E' )) ~ optSign ~ rep1( digit ) ^^
				{case e ~ optSign ~ exp => e :: optSign :: (exp mkString "") :: Nil mkString ""}

			private def optExponent = opt( exponent ) ^^
				{
					case None => ""
					case Some( exponent ) => exponent
				}

			reserved += (
				"if", "then", "else", "elif", "true", "false", "or", "and", "not",
				"table", "unique", "required", "string", "optional", "integer", "secret", "api", "GET", "POST", "DELETE"
				)
			delimiters += (
				"+", "*", "-", "/", "^", "(", ")", "[", "]", ",", "=", "==", "/=", "<", ">", "<=", ">=",
				":"
				)
		}

	def parse( r: Reader[Char] ) = phrase( source )( lexical.read(r) )

	import lexical.{Newline, Indent, Dedent}

	lazy val onl = opt(Newline)

	lazy val source =
		rep1(statement <~ Newline) |
		Newline ^^^ Nil

	lazy val statement: PackratParser[StatementAST] =
		tableDefinition |
		tableMapping
		
	lazy val tableDefinition: PackratParser[TableDefinitionAST] =
		"table" ~> ident ~ (Indent ~> rep1(tableField) <~ Dedent) ^^ {case name ~ fields => TableDefinitionAST( name, fields )}
		
	lazy val tableField: PackratParser[TableField] =
		rep(fieldModifier) ~ fieldType ~ ident <~ Newline ^^ {case modifiers ~ typ ~ name => TableField( modifiers, typ, name )}

	lazy val fieldType: PackratParser[FieldType] =
		"string" ^^^ StringType
		
	lazy val fieldModifier: PackratParser[FieldTypeModifier] =
		"unique" ^^^ UniqueModifier |
		"secret" ^^^ SecretModifier
		
	lazy val tableMapping: PackratParser[APIAST] =
		"api" ~> opt(ident) ~ (Indent ~> rep1(uriMapping) <~ Dedent) ^^ {case table ~ mappings => APIAST( table, mappings )}
		
	lazy val uriMapping: PackratParser[URIMapping] =
		httpMethod ~ mappingAction <~ Newline ^^ {case method ~ action => URIMapping( method, Nil, action )} |
		httpMethod ~ rep1sep(uriSegment, "/") ~ mappingAction <~ Newline ^^ {case method ~ path ~ action => URIMapping( method, path, action )}
		
	lazy val httpMethod: PackratParser[HTTPMethod] =
		"GET" ^^^ GETMethod |
		"POST" ^^^ POSTMethod |
		"DELETE" ^^^ DELETEMethod
		
	lazy val uriSegment: PackratParser[URISegment] =
		ident ^^ (NameURISegment) |
		":" ~> ident ^^ (ParameterURISegment)
	
	lazy val mappingAction: PackratParser[ActionAST] =
		ident ~ ("(" ~> repsep(ident, ",") <~ ")") ^^ {case name ~ args => ActionAST( name, args )}
}