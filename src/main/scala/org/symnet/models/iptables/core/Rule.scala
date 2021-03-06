// Copyright (C) 2017 Calin Cruceru <calin.cruceru@stud.acs.upb.ro>.
//
// See the LICENCE file distributed with this work for additional
// information regarding copyright ownership.

package org.symnet.models.iptables
package core

import scalaz.Maybe
import scalaz.Maybe._

case class Rule(matches: List[Match], target: Target, goto: Boolean = false) {

  ///
  /// Validation
  ///

  import scalaz.Maybe.maybeInstance.traverse

  /** The `traverse' combinator is the equivalent to `mapM' in Haskell.  It maps
   *  each element of a structure to a monadic action, evaluates these actions
   *  from left to right, and collects the results.
   *
   *  NOTE: The `v' in `v[Name]' stands for `validated'.
   */

  def validate(chain: Chain, table: Table): Maybe[Rule] =
    for {
      // A rule is valid if all its matches are valid ...
      vMatches <- traverse(matches)(_.validate(this, chain, table))

      // ... and its 'real' target is valid.
      //
      // NOTE: The 'real' target of a rule could be another one than that used
      // when constructing it only if it is a placeholder target which refers a
      // valid (from a 'target' perspective) chain.
      actualResult <- target match {
        case PlaceholderTarget(name, goto) => {
          // Find the user-defined chains that match this placeholder's name.
          val matchedChains = table.chains.collect {
            case uc @ UserChain(chainName, _) if chainName == name => uc
          }

          // If more than 1 or 0 have been found, it is an error.
          if (matchedChains.length == 1)
            Just((matchedChains.head, goto))
          else
            empty
        }
        case _ => Just((target, false))
      }
      vTarget <- actualResult._1.validate(this, chain, table)
    } yield Rule(vMatches, vTarget, actualResult._2)
}
