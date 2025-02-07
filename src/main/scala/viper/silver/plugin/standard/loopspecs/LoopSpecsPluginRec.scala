package viper.silver.plugin.standard.loopspecs

import fastparse._
import viper.silver.ast._
import viper.silver.parser._
import viper.silver.plugin.{ParserPluginTemplate, SilverPlugin}
import viper.silver.verifier.AbstractError

import scala.annotation.unused


class LoopSpecsPluginRec(@unused reporter: viper.silver.reporter.Reporter,
                         @unused logger: ch.qos.logback.classic.Logger,
                         config: viper.silver.frontend.SilFrontendConfig,
                         fp: FastParser)  extends SilverPlugin with ParserPluginTemplate {

  import FastParserCompanion._
  import fp.{ParserExtension, _file, lineCol, parenthesizedExp, postcondition, precondition, semiSeparated, stmt, stmtBlock}


  private def deactivated: Boolean = config != null && config.disableTerminationPlugin.toOption.getOrElse(false)

  //TODO: Add some variable in config to choose which version of desugaring: inex, rec


  def ghostBlock[$: P]: P[PGhostBlock] =
    P((reservedKw(PGhostKeyword) ~ ghostBody()) map {case (kw, body) => PGhostBlock(kw, body) _ }).pos

  def ghostBody[$: P](allowDefine: Boolean = true): P[PSeqn] =
    P(semiSeparated(stmt(allowDefine)).braces map PSeqn.apply).pos


  def baseCaseBlock[$: P]: P[PBaseCaseBlock] =
    P((reservedKw(PBaseCaseKeyword) ~ baseCaseBody()) map { case (kw, body) => PBaseCaseBlock(kw, body) _ }).pos

  def baseCaseBody[$: P](allowDefine: Boolean = true): P[PSeqn] =
    P(semiSeparated(stmt(allowDefine)).braces map PSeqn.apply).pos



  def loopspecs[$ : P]: P[PLoopSpecs] =

    // Parse the custom while loop
    P(
      (
      reservedKw(PKw.While) ~ parenthesizedExp ~~
      semiSeparated(precondition) ~~
      semiSeparated(postcondition) ~~~
      stmtBlock().lw ~
      ghostBlock.? ~
      baseCaseBlock.?
    ).map {
      case (whileKw, condExp, preSpec, postSpec, bodySeqn, maybeGhost,  maybeBaseCase) =>

        // PGrouped.Paren[PExp]
        PLoopSpecs(
          whileKw,
          condExp,
          preSpec,
          postSpec,
          bodySeqn,
          maybeGhost,
          maybeBaseCase
        )(_)
    }).pos


  def loopspecs_other_syntax[$ : P]: P[PLoopSpecs] =

    // Parse the custom while loop
    P(
      (
        reservedKw(PKw.While) ~ parenthesizedExp ~~
          semiSeparated(precondition) ~~
          semiSeparated(postcondition) ~~~
          stmtBlock().lw ~
          baseCaseBlock.? ~
          ghostBlock.?
        ).map {
        case (whileKw, condExp, preSpec, postSpec, bodySeqn, maybeGhost,  maybeBaseCase) =>

          // PGrouped.Paren[PExp]
          PLoopSpecs(
            whileKw,
            condExp,
            preSpec,
            postSpec,
            bodySeqn,
            maybeBaseCase,
            maybeGhost
          )(_)
      }).pos

  def preExpr[$: P]: P[PPreExp] =
    P((reservedKw(PPreKeyword) ~ parenthesizedExp).map{
      case(preKw, exp) =>
        PPreExp(preKw, exp)(_)
    }).pos
  

  /**
   * Add extensions to the parser
   */
  // So that it can parse our new while loop into a PLoopSpecs
  override def beforeParse(input: String, isImported: Boolean): String = {
    // Add 3 new keywords: ghost, basecase, pre
    ParserExtension.addNewKeywords(Set(PGhostKeyword, PBaseCaseKeyword, PPreKeyword))
    ParserExtension.addNewExpAtStart(preExpr(_))

    // Add new parser to the precondition
    //ParserExtension.addNewPreCondition(decreases(_))
    // Add new parser to the postcondition
    //ParserExtension.addNewPostCondition(decreases(_))
    // Add new parser to the invariants
    ParserExtension.addNewStmtAtStart(loopspecs(_))
    ParserExtension.addNewStmtAtStart(loopspecs_other_syntax(_))
    input
  }

  //Well-definedness
  // Reject pre(.) if not in post/ghost/bc (manual checking in befVerify()) ==> add test case DONE
  // ALso this is taken care of indircetly by Viper as it will complain about what to do with the pre: don't know how readable
  // the error will be though.
  // ExpectedOutput(consistency.error) as there's no reason
  //TODO: transform final englobing inhale error into post error (override def) ??
  // rn it says cant prove englobing postcond, maybe it should say "basecase not strong enough"

  //TODO: More positive big examples
  val choice = 1
  override def beforeVerify(input: Program): Program ={
      val ls = new LoopSpecsRec(this, input)
      ls.beforeVerify()
  }


  override def reportError(error: AbstractError): Unit = {
    super.reportError(error)
  }
}
