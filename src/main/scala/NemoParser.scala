import scala.util.parsing.combinator.syntactical.StandardTokenParsers
import scala.util.parsing.combinator.lexical.StdLexical
import scala.io.BufferedSource
import scala.util.parsing.input.StreamReader
import scala.xml.{NodeSeq, XML}

sealed abstract class Expr {
  def eval(c: NemoContext):Option[NemoValue]
}

sealed abstract class Statement {
  def eval(c: NemoContext):Option[NemoValue]
}

case class SExpr(e: Expr) extends Statement {
  def eval(c: NemoContext) = e.eval(c)
}

case class SLet(b:String, e: Expr) extends Statement {
  def eval(c: NemoContext):Option[NemoValue] = {
    val ev = e.eval(c)
    ev.foreach(v => c.add(b, v))
    ev
  }
}

case class EIf(cond:Expr, e1:Expr, e2:Expr) extends Expr {
  def eval(c:NemoContext):Option[NemoValue] = {
    val x = cond.eval(c)
    x.flatMap { v => if (v.toBoolean) e1.eval(c) else e2.eval(c) }
  }
}

case class ELit(v:NemoValue) extends Expr {
  def eval(c: NemoContext) = Some(v)
}

case class EAdd(l:Expr, r:Expr) extends Expr {
  def eval(c: NemoContext) = for (i <- l.eval(c); j <- r.eval(c)) yield i + j
}

case class ESub(l:Expr, r:Expr) extends Expr {
  def eval(c: NemoContext) = for (i <- l.eval(c); j <- r.eval(c)) yield i - j
}

case class EMul(l:Expr, r:Expr) extends Expr {
  def eval(c: NemoContext) = for (i <- l.eval(c); j <- r.eval(c)) yield i * j
}

case class EDiv(l:Expr, r:Expr) extends Expr {
  def eval(c: NemoContext) = for (i <- l.eval(c); j <- r.eval(c)) yield i / j
}

case class EEq(l:Expr, r:Expr) extends Expr {
  def eval(c: NemoContext) = for (i <- l.eval(c); j <- r.eval(c)) yield NemoBoolean(i == j)
}

case class EAnd(l:Expr, r:Expr) extends Expr {
  def eval(c: NemoContext) = {
    l.eval(c).flatMap { v => if (!v.toBoolean) Some(NemoBoolean(false)) else r.eval(c) }
  }
}

case class EOr(l:Expr, r:Expr) extends Expr {
  def eval(c: NemoContext) = {
    l.eval(c).flatMap { v => if (v.toBoolean) Some(NemoBoolean(true)) else r.eval(c) }
  }
}

case class ERef(r:String) extends Expr {
  def eval(c: NemoContext) = c(r)
}

//case class EBody(Seq[Statement]) extends Expr {
//  def eval(c: NemoContext) = {
    

case class EFun(val paramList:Seq[String], val body:Seq[Statement]) extends Expr {
  def eval(c: NemoContext) = Some(NemoUserFunction(this, c))
}

//object EList {
  //def cons(head:Expr, tail:EList) = EList(head +: tail.es)
//  def append(l:EList, t:Expr) = EList(l.es :+ t)
//}

case class EList(val es: Seq[Expr]) extends Expr {
  def eval(c: NemoContext):Option[NemoList] = {
    val result = 
      for (e <- es;
           v <- e.eval(c)) yield v
    if (result.length == es.length) Some(NemoList(result)) else None
  }
  def length = es.length
}

case class EApply(fun:Expr, args:EList) extends Expr {
  def eval(c:NemoContext) = {
    val cf = fun.eval(c)
    cf match {
      case None => Some(NemoError("Function not found"))
      case Some(NemoSpecialForm(f)) => f(c, args)      
      case Some(f:NemoFunction) => args.eval(c).flatMap(f.apply _)
      case _ => Some(NemoError("Not a function"))
    }
  }
}

// Using Parser Combinators to define syntax/parsing rules of Nemo formulas declaratively.
// Each nemo formula is parsed into an instance of Expr.  Any related utility functions also
// belong here
object NemoParser extends StandardTokenParsers {
  
  override val lexical = ExprLexical
  lexical.delimiters ++= List("+", "-", "*", "/", "(", ")", "=", ";", "{", "}", ",", "<", ">", "&&", "||", "==", ".")
  lexical.reserved ++= List("let", "fun","true", "false", "if", "else" )
  val numericLiteral = numericLit ^^ {
    i => if (i.contains(".")) ELit(NemoDouble(i.toDouble)) else ELit(NemoInt(i.toInt))
  }
  // takes a parser and a separator and returns a parser that parses a list as a Seq
  def sequencer[T](elementParser:Parser[T], separator:String):Parser[Seq[T]] =
    opt(chainl1(elementParser ^^ {e => Seq(e)}, elementParser, (separator ^^^ {(l:Seq[T], e:T) => l :+ e }))) ^^ {
      case None => Seq[T]()
      case Some(x) => x
    }

  val stringLiteral = stringLit ^^ { s => ELit(NemoString(s)) }
  val booleanLiteral = "true" ^^ { s => ELit(NemoBoolean(true)) } | "false" ^^ { s => ELit(NemoBoolean(false)) }

//  def anonFunCall:Parser[Expr] = ("(" ~> expr <~ ")") ~ ("(" ~> exprList <~ ")") ^^ { case f ~ e => EApply(f, e) }
//  def funtionCall:Parser[Expr] = term ~ ("(" ~> exprList <~ ")") ^^ { case f ~ e => EApply(f, e) }
//  def methodCall = (ref <~ ".") ~ ref ^^ { case e ~ f => EApply(f, e) } |
  
  val ref = ident ^^ ERef
  def term:Parser[Expr] =  "(" ~> expr <~ ")" | numericLiteral | stringLiteral | booleanLiteral | ref
//  def factor = funtionCall | term

  def factor = term ~ rep(("(" ~> exprList <~ ")") | (("." ~ ref ~ opt("(" ~> exprList <~ ")")))) ^^ {
    case e ~ cs => cs.foldLeft(e) {
      (_, _) match {
        case (e1, ("." ~ (f:Expr) ~ Some(l:EList))) => EApply(f, EList(e1 +: l.es))
        case (e1, ("." ~ (f:Expr) ~ None)) => EApply(f, EList(Seq(e1)))
        case (f, l:EList) => EApply(f, l)
      }
    }
  }
        
  def ifExp = ("if" ~> "(" ~> expr <~ ")") ~ expr ~ ("else" ~> expr) ^^ {
    case cond ~ e1 ~ e2 => EIf(cond, e1, e2)
  }

  def exprList = sequencer(expr, ",") ^^ { EList(_)}

  def expr:Parser[Expr] = ifExp | funDef | binaryOpExpr(minPrecedentLevel) | factor // | exprList


  // precedent
  def binaryOperator(precedent:Int) = {
    precedent match {
      case 1 =>
        "&&" ^^^ EAnd | "||" ^^^ EOr
      case 2 =>
        "=" ^^^ EEq | "==" ^^^ EEq
      case 3 =>
        "+" ^^^ EAdd | "-" ^^^ ESub
      case 4 =>
        "*" ^^^ EMul | "/" ^^^ EDiv
    }
  }
  
  val minPrecedentLevel = 1
  val maxPrecedentLevel = 4

  def binaryOpExpr(level:Int):Parser[Expr] = {
    if (level > maxPrecedentLevel) factor else binaryOpExpr(level + 1) * binaryOperator(level)
  }

  def sLet = ("let" ~> ident <~ "=") ~ expr ^^ {
    case b ~ e => SLet(b,e)
  }

  def sExpr = expr ^^ SExpr
  def stmt = sLet | sExpr

  def stmtBlock = "{" ~> stmtList <~ "}"
  def stmtList = sequencer(stmt, ";") <~ opt(";")
  def paramList = "(" ~> sequencer(ident, ",") <~ ")"

  def funDef = "fun" ~> paramList ~ stmtBlock ^^ {
    case pl ~ sb => EFun(pl, sb)
  }

  //def apply(str:String) = {
    //println("Parsing " + str)
  //phrase(expr)(new lexical.Scanner(str))
  //}

  def parseSourceFile(f:BufferedSource) = {
    phrase(stmtList)(new lexical.Scanner(StreamReader(f.bufferedReader)))
  }

  def parseSourceFile(str:String) = {
    phrase(stmtList)(new lexical.Scanner(str))
  }

  def apply(str:String) = {
    val scanned = new lexical.Scanner(str)
    try {
      Success(ELit(NemoXML(XML.loadString(str))), scanned)
    }
    catch {
      case _ => phrase(expr)(scanned)
    }
  }
}

object ExprLexical extends StdLexical {
  override def token = decimal | super.token
  override def identChar = super.identChar | elem('?')
  def decimal = rep(digit) ~ '.' ~ rep1(digit) ^^ {
    case i ~ dot ~ d => NumericLit(i.mkString + "." + d.mkString)
  }
}
