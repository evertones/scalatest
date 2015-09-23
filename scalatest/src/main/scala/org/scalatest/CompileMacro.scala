/*
 * Copyright 2001-2012 Artima, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.scalatest

import org.scalatest.exceptions.StackDepthExceptionHelper

import scala.language.experimental.macros
import scala.reflect.macros.{ Context, TypecheckException, ParseException }
import org.scalatest.exceptions.StackDepthException._
import org.scalatest.exceptions.StackDepthExceptionHelper._
import org.scalatest.words.{TypeCheckWord, CompileWord}

private[scalatest] object CompileMacro {

  // SKIP-SCALATESTJS-START
  val stackDepth = 0
  // SKIP-SCALATESTJS-END
  //SCALATESTJS-ONLY val stackDepth = 9

  // extract the code string from the AST
  def getCodeStringFromCodeExpression(c: Context)(methodName: String, code: c.Expr[String]): String = {
    import c.universe._
    code.tree match {
      case Literal(Constant(codeStr)) => codeStr.toString  // normal string literal
      case Select(
        Apply(
          Select(
            _,
            augmentStringTermName
          ),
          List(
            Literal(Constant(codeStr))
          )
        ),
        stripMarginTermName
      ) if augmentStringTermName.decoded == "augmentString" && stripMarginTermName.decoded == "stripMargin" => codeStr.toString.stripMargin // """xxx""".stripMargin string literal
      case _ => c.abort(c.enclosingPosition, methodName + " only works with String literals.")
    }
  }

  // parse and type check a code snippet, generate code to throw TestFailedException when type check passes or parse error
  def assertTypeErrorImpl(c: Context)(code: c.Expr[String]): c.Expr[Assertion] = {
    import c.universe._

    // extract code snippet
    val codeStr = getCodeStringFromCodeExpression(c)("assertNoTypeError", code)

    try {
      c.typeCheck(c.parse("{ "+codeStr+" }"))  // parse and type check code snippet
      // If reach here, type check passes, let's generate code to throw TestFailedException
      val messageExpr = c.literal(Resources.expectedTypeErrorButGotNone(codeStr))
      reify {
        throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
      }
    } catch {
      case e: TypecheckException =>
        reify {
          // type check failed as expected, generate code to return Succeeded
          Succeeded
        }
      case e: ParseException =>
        // parse error, generate code to throw TestFailedException
        val messageExpr = c.literal(Resources.expectedTypeErrorButGotParseError(e.getMessage, codeStr))
        reify {
          throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
        }
    }
  }

  def expectTypeErrorImpl(c: Context)(code: c.Expr[String]): c.Expr[Fact] = {
    import c.universe._

    // extract code snippet
    val codeStr = getCodeStringFromCodeExpression(c)("expectNoTypeError", code)

    try {
      c.typeCheck(c.parse("{ "+codeStr+" }"))  // parse and type check code snippet
      // If reach here, type check passes, let's generate code to throw TestFailedException
      val messageExpr = c.literal(Resources.expectedTypeErrorButGotNone(codeStr))
      reify {
        Fact.No(
          messageExpr.splice,
          messageExpr.splice,
          messageExpr.splice,
          messageExpr.splice,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty
        )
      }
    } catch {
      case e: TypecheckException =>
        val messageExpr = c.literal(Resources.gotTypeErrorAsExpected(codeStr))
        reify {
          // type check failed as expected, return Yes
          Fact.Yes(
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            Vector.empty
          )
        }
      case e: ParseException =>
        // parse error, generate code to throw TestFailedException
        val messageExpr = c.literal(Resources.expectedTypeErrorButGotParseError(e.getMessage, codeStr))
        reify {
          Fact.No(
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            Vector.empty
          )
        }
    }
  }

  // parse and type check a code snippet, generate code to throw TestFailedException when both parse and type check succeeded
  def assertDoesNotCompileImpl(c: Context)(code: c.Expr[String]): c.Expr[Assertion] = {
    import c.universe._

    // extract code snippet
    val codeStr = getCodeStringFromCodeExpression(c)("assertDoesNotCompile", code)

    try {
      c.typeCheck(c.parse("{ "+codeStr+" }"))  // parse and type check code snippet
      // Both parse and type check succeeded, the code snippet compiles unexpectedly, let's generate code to throw TestFailedException
      val messageExpr = c.literal(Resources.expectedCompileErrorButGotNone(codeStr))
      reify {
        throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
      }
    } catch {
      case e: TypecheckException =>
        reify {
          // type check error, code snippet does not compile as expected, generate code to return Succeeded
          Succeeded
        }
      case e: ParseException =>
        reify {
          // parse error, code snippet does not compile as expected, generate code to return Succeeded
          Succeeded
        }
    }
  }

  // parse and type check a code snippet, generate code to return Fact (Yes or No).
  def expectDoesNotCompileImpl(c: Context)(code: c.Expr[String]): c.Expr[Fact] = {
    import c.universe._

    // extract code snippet
    val codeStr = getCodeStringFromCodeExpression(c)("expectDoesNotCompile", code)

    try {
      c.typeCheck(c.parse("{ "+codeStr+" }"))  // parse and type check code snippet
      // Both parse and type check succeeded, the code snippet compiles unexpectedly, let's generate code to throw TestFailedException
      val messageExpr = c.literal(Resources.expectedCompileErrorButGotNone(codeStr))
      reify {
        Fact.No(
          messageExpr.splice,
          messageExpr.splice,
          messageExpr.splice,
          messageExpr.splice,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty
        )
        //throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
      }
    } catch {
      case e: TypecheckException =>
        val messageExpr = c.literal(Resources.didNotCompile(codeStr))
        reify {
          // type check error, code snippet does not compile as expected, generate code to return Succeeded
          Fact.Yes(
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            Vector.empty
          )
        }
      case e: ParseException =>
        val messageExpr = c.literal(Resources.didNotCompile(codeStr))
        reify {
          // parse error, code snippet does not compile as expected, generate code to return Succeeded
          Fact.Yes(
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            Vector.empty
          )
          //Succeeded
        }
    }
  }

  // parse and type check a code snippet, generate code to throw TestFailedException when either parse or type check fails.
  def assertCompilesImpl(c: Context)(code: c.Expr[String]): c.Expr[Assertion] = {
    import c.universe._

    // extract code snippet
    val codeStr = getCodeStringFromCodeExpression(c)("assertCompiles", code)

    try {
      c.typeCheck(c.parse("{ " + codeStr + " }")) // parse and type check code snippet
      // Both parse and type check succeeded, the code snippet compiles as expected, generate code to return Succeeded
      reify {
        Succeeded
      }
    } catch {
      case e: TypecheckException =>
        // type check error, compiles fails, generate code to throw TestFailedException
        val messageExpr = c.literal(Resources.expectedNoErrorButGotTypeError(e.getMessage, codeStr))
        reify {
          throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
        }
      case e: ParseException =>
        // parse error, compiles fails, generate code to throw TestFailedException
        val messageExpr = c.literal(Resources.expectedNoErrorButGotParseError(e.getMessage, codeStr))
        reify {
          throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
        }
    }
  }

  def expectCompilesImpl(c: Context)(code: c.Expr[String]): c.Expr[Fact] = {
    import c.universe._

    // extract code snippet
    val codeStr = getCodeStringFromCodeExpression(c)("expectCompiles", code)

    try {
      c.typeCheck(c.parse("{ " + codeStr + " }")) // parse and type check code snippet
      // Both parse and type check succeeded, the code snippet compiles as expected, generate code to return Succeeded
      val messageExpr = c.literal(Resources.compiledSuccessfully(codeStr))
      reify {
        Fact.Yes(
          messageExpr.splice,
          messageExpr.splice,
          messageExpr.splice,
          messageExpr.splice,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty
        )
        //Succeeded
      }
    } catch {
      case e: TypecheckException =>
        // type check error, compiles fails, generate code to throw TestFailedException
        val messageExpr = c.literal(Resources.expectedNoErrorButGotTypeError(e.getMessage, codeStr))
        reify {
          Fact.No(
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            Vector.empty
          )
        }
      case e: ParseException =>
        // parse error, compiles fails, generate code to throw TestFailedException
        val messageExpr = c.literal(Resources.expectedNoErrorButGotParseError(e.getMessage, codeStr))
        reify {
          Fact.No(
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            messageExpr.splice,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            Vector.empty
          )
        }
    }
  }

  // check that a code snippet does not compile
  def notCompileImpl(c: Context)(compileWord: c.Expr[CompileWord])(shouldOrMust: String): c.Expr[Assertion] = {

    import c.universe._

    // parse and type check a code snippet, generate code to throw TestFailedException if both parse and type check succeeded
    def checkNotCompile(code: String): c.Expr[Assertion] = {
      try {
        c.typeCheck(c.parse("{ " + code + " }"))  // parse and type check code snippet
        // both parse and type check succeeded, compiles succeeded unexpectedly, generate code to throw TestFailedException
        val messageExpr = c.literal(Resources.expectedCompileErrorButGotNone(code))
        reify {
          throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
        }
      } catch {
        case e: TypecheckException =>
          reify {
            // type check error, compile fails as expected, generate code to return Succeeded
            Succeeded
          }
        case e: ParseException =>
          reify {
            // parse error, compile fails as expected, generate code to return Succeeded
            Succeeded
          }
      }
    }

    val methodName = shouldOrMust + "Not"

    c.macroApplication match {
      case Apply(
             Select(
               Apply(
                 _,
                 List(
                   Literal(Constant(code))
                 )
               ),
               methodNameTermName
             ),
             _
           ) if methodNameTermName.decoded == methodName =>
        // LHS is a normal string literal, call checkNotCompile with the extracted code string to generate code
        val codeStr = code.toString
        checkNotCompile(codeStr)

      case Apply(
             Select(
               Apply(
                 _,
                 List(
                   Select(
                     Apply(
                       Select(
                         _,
                         augmentStringTermName
                       ),
                       List(
                         Literal(
                           Constant(code)
                         )
                       )
                     ),
                     stripMarginTermName
                   )
                 )
               ),
               methodNameTermName
             ),
             _
           ) if augmentStringTermName.decoded == "augmentString" && stripMarginTermName.decoded == "stripMargin" && methodNameTermName.decoded == methodName =>
        // LHS is a """xxx""".stripMargin string literal, call checkNotCompile with the extracted code string to generate code
        val codeStr = code.toString.stripMargin
        checkNotCompile(codeStr)

      case _ => c.abort(c.enclosingPosition, "The '" + shouldOrMust + "Not compile' syntax only works with String literals.")
    }
  }

  // used by shouldNot compile syntax, delegate to notCompileImpl to generate code
  def shouldNotCompileImpl(c: Context)(compileWord: c.Expr[CompileWord]): c.Expr[Assertion] =
    notCompileImpl(c)(compileWord)("should")

  // used by mustNot compile syntax, delegate to notCompileImpl to generate code
  def mustNotCompileImpl(c: Context)(compileWord: c.Expr[CompileWord]): c.Expr[Assertion] =
    notCompileImpl(c)(compileWord)("must")

  // check that a code snippet does not compile
  def notTypeCheckImpl(c: Context)(typeCheckWord: c.Expr[TypeCheckWord])(shouldOrMust: String): c.Expr[Assertion] = {

    import c.universe._

    // parse and type check a code snippet, generate code to throw TestFailedException if parse error or both parse and type check succeeded
    def checkNotTypeCheck(code: String): c.Expr[Assertion] = {
      try {
        c.typeCheck(c.parse("{ " + code + " }"))  // parse and type check code snippet
        // both parse and type check succeeded unexpectedly, generate code to throw TestFailedException
        val messageExpr = c.literal(Resources.expectedTypeErrorButGotNone(code))
        reify {
          throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
        }
      } catch {
        case e: TypecheckException =>
          reify {
            // type check error as expected, generate code to return Succeeded
            Succeeded
          }
        case e: ParseException =>
          // expect type check error but got parse error, generate code to throw TestFailedException
          val messageExpr = c.literal(Resources.expectedTypeErrorButGotParseError(e.getMessage, code))
          reify {
            throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
          }
      }
    }

    val methodName = shouldOrMust + "Not"

    c.macroApplication match {
      case Apply(
             Select(
               Apply(
                 _,
                 List(
                   Literal(Constant(code))
                 )
               ),
               methodNameTermName
             ),
             _
           ) if methodNameTermName.decoded == methodName =>
        // LHS is a normal string literal, call checkNotTypeCheck with the extracted code string to generate code
        val codeStr = code.toString
        checkNotTypeCheck(codeStr)

      case Apply(
             Select(
               Apply(
                 _,
                 List(
                   Select(
                     Apply(
                       Select(
                         _,
                         augmentStringTermName
                       ),
                       List(
                         Literal(
                           Constant(code)
                         )
                       )
                     ),
                     stripMarginTermName
                   )
                 )
               ),
               methodNameTermName
             ),
             _
      ) if augmentStringTermName.decoded == "augmentString" && stripMarginTermName.decoded == "stripMargin" && methodNameTermName.decoded == methodName =>
        // LHS is a """xxx""".stripMargin string literal, call checkNotTypeCheck with the extracted code string to generate code
        val codeStr = code.toString.stripMargin
        checkNotTypeCheck(codeStr)

      case _ => c.abort(c.enclosingPosition, "The '" + shouldOrMust + "Not typeCheck' syntax only works with String literals.")
    }
  }

  // used by shouldNot typeCheck syntax, delegate to notTypeCheckImpl to generate code
  def shouldNotTypeCheckImpl(c: Context)(typeCheckWord: c.Expr[TypeCheckWord]): c.Expr[Assertion] =
    notTypeCheckImpl(c)(typeCheckWord)("should")

  // used by mustNot typeCheck syntax, delegate to notTypeCheckImpl to generate code
  def mustNotTypeCheckImpl(c: Context)(typeCheckWord: c.Expr[TypeCheckWord]): c.Expr[Assertion] =
    notTypeCheckImpl(c)(typeCheckWord)("must")

  // check that a code snippet compiles
  def compileImpl(c: Context)(compileWord: c.Expr[CompileWord])(shouldOrMust: String): c.Expr[Assertion] = {
    import c.universe._

    // parse and type check a code snippet, generate code to throw TestFailedException if either parse error or type check error
    def checkCompile(code: String): c.Expr[Assertion] = {
      try {
        c.typeCheck(c.parse("{ " + code + " }"))  // parse and type check code snippet
        // both parse and type check succeeded, compile succeeded expectedly, generate code to do nothing
        reify {
          Succeeded
        }
      } catch {
        case e: TypecheckException =>
          // type check error, compile fails unexpectedly, generate code to throw TestFailedException
          val messageExpr = c.literal(Resources.expectedNoErrorButGotTypeError(e.getMessage, code))
          reify {
            throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
          }
        case e: ParseException =>
          // parse error, compile failes unexpectedly, generate code to throw TestFailedException
          val messageExpr = c.literal(Resources.expectedNoErrorButGotParseError(e.getMessage, code))
          reify {
            throw new exceptions.TestFailedException(messageExpr.splice, stackDepth)
          }
      }
    }

    c.macroApplication match {
      case Apply(
             Select(
               Apply(
                 _,
                 List(
                   Literal(
                     Constant(code)
                   )
                 )
               ),
               shouldOrMustTermName
             ),
             _
      ) if shouldOrMustTermName.decoded == shouldOrMust =>
        // LHS is a normal string literal, call checkCompile with the extracted code string to generate code
        val codeStr = code.toString
        checkCompile(codeStr)

      case Apply(
             Select(
               Apply(
                 _,
                 List(
                   Select(
                     Apply(
                       Select(
                         _,
                         augmentStringTermName
                       ),
                       List(
                         Literal(
                           Constant(code)
                         )
                       )
                     ),
                     stripMarginTermName
                   )
                 )
               ),
               shouldOrMustTermName
             ),
             _
      ) if augmentStringTermName.decoded == "augmentString" && stripMarginTermName.decoded == "stripMargin" && shouldOrMustTermName.decoded == shouldOrMust =>
        // LHS is a """xxx""".stripMargin string literal, call checkCompile with the extracted code string to generate code
        val codeStr = code.toString.stripMargin
        checkCompile(codeStr)

      case _ => c.abort(c.enclosingPosition, "The '" + shouldOrMust + " compile' syntax only works with String literals.")
    }
  }

  // used by should compile syntax, delegate to compileImpl to generate code
  def shouldCompileImpl(c: Context)(compileWord: c.Expr[CompileWord]): c.Expr[Assertion] =
    compileImpl(c)(compileWord)("should")

  // used by must compile syntax, delegate to compileImpl to generate code
  def mustCompileImpl(c: Context)(compileWord: c.Expr[CompileWord]): c.Expr[Assertion] =
    compileImpl(c)(compileWord)("must")

}
